/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/faction/NpcFaction.
 */
package io.github.capttracker.ancientwarfarenpc.entity.faction;

import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfare.util.NBTHelper;
import io.github.capttracker.ancientwarfarenpc.ai.AIHelper;
import io.github.capttracker.ancientwarfarenpc.ai.faction.NpcAICaravanEscort;
import io.github.capttracker.ancientwarfarenpc.ai.faction.NpcAIFactionFleeSun;
import io.github.capttracker.ancientwarfarenpc.ai.faction.NpcAIFactionRestrictSun;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.faction.AwFactions;
import io.github.capttracker.spectrefactions.api.ActorId;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcSounds;
import io.github.capttracker.ancientwarfarenpc.registry.FactionAdditionalAttributes;
import io.github.capttracker.ancientwarfarenpc.registry.FactionNpcDefault;
import io.github.capttracker.ancientwarfarenpc.registry.FactionRegistry;
import io.github.capttracker.ancientwarfarenpc.registry.NpcDefaultsRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;

import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Base of the faction NPCs. The faction is only a name ({@link #getFaction()}); the stats, equipment, size, sound and extra
 * attributes of a faction and type come from the faction NPC defaults data.
 *
 * <p>Changes from 1.12.2: the hit box (random height and the width from it) is returned by {@link #getDefaultDimensions} and
 * applied with {@code refreshDimensions} ({@code getDimensions(Pose)} is final in 1.21.1); the NPC is created with the entity
 * type factory and gets its faction with {@link #setFactionNameAndDefaults}; the lizardman and coven poison immunity (see
 * {@link #canBeAffected}) works. Whether an NPC counts as undead can only be answered by this class, so vanilla's entity type
 * tag based rules (smite, the inverted effects) follow {@link #isUndead()} only where overridden here.
 */
@SuppressWarnings({"squid:MaximumInheritanceDepth", "squid:S2160"})
public abstract class NpcFaction extends NpcBase {
    private static final int DEATH_REVENGE_TICKS = 6000;
    private static final int HIT_REVENGE_TICKS = DEATH_REVENGE_TICKS / 20;
    private static final int REVENGE_LIST_VALIDATION_TICKS = DEATH_REVENGE_TICKS / 100;
    private static final double REVENGE_SET_RANGE = 50D;
    private static final String HEIGHT_TAG = "height";
    private static final EntityDataAccessor<String> SOUND = SynchedEntityData.defineId(NpcFaction.class, EntityDataSerializers.STRING);

    /** Called when a faction NPC that is allowed to despawn despawns (the Structures module turns it back into a spawner). */
    @FunctionalInterface
    public interface DespawnHandler {
        void onDespawn(NpcFaction npc);
    }

    private static final List<DespawnHandler> DESPAWN_HANDLERS = new ArrayList<>();

    public static void addDespawnHandler(DespawnHandler handler) {
        DESPAWN_HANDLERS.add(handler);
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    @Nullable
    protected String factionName;
    private Map<String, Long> revengePlayers = new HashMap<>();
    private FactionAdditionalAttributes additionalAttributes = FactionAdditionalAttributes.NONE;
    private boolean canDespawn = false;
    /** True if this NPC belongs to a caravan or trade ship (rework phase 11: its crew stays aboard whoever is at the helm). */
    public boolean isCaravanMember() {
        return caravanLeader != null;
    }

    /** The leader of the caravan this NPC escorts (rework phase 5), or null. */
    @Nullable
    private UUID caravanLeader;
    // 0 until the faction defaults gave this NPC a size
    private float npcWidth;
    private float npcHeight;

    protected NpcFaction(EntityType<? extends NpcFaction> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new io.github.capttracker.ancientwarfarenpc.ai.vehicle.NpcAIShipCrew(this));
        goalSelector.addGoal(1, new io.github.capttracker.ancientwarfarenpc.ai.vehicle.NpcAIShipBoard(this)); // boarding and defending decks (rework phase 11)
        goalSelector.addGoal(1, new NpcAICaravanEscort(this));
        goalSelector.addGoal(3, new io.github.capttracker.ancientwarfarenpc.ai.vehicle.NpcAIShipFactionCrew(this));
        goalSelector.addGoal(2, new NpcAIFactionRestrictSun(this));
        goalSelector.addGoal(3, new NpcAIFactionFleeSun(this, 1.0D));
    }

    /** True for the NPCs that crew the ships of allied players (soldiers and archers: see {@code NpcAIShipFactionCrew}). */
    protected boolean canCrewShips() {
        return false;
    }

    /** The ones that crew a ship man a gun first, then row; the others never board. */
    @Override
    public List<io.github.capttracker.ancientwarfarevehicle.entity.SeatRole> getShipSeatPreference() {
        return canCrewShips() ? List.of(io.github.capttracker.ancientwarfarevehicle.entity.SeatRole.GUNNER, io.github.capttracker.ancientwarfarevehicle.entity.SeatRole.ROWER)
                : List.of();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SOUND, "none");
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return canDespawn;
    }

    private void setDeathRevengePlayer(String playerName) {
        revengePlayers.put(playerName, level().getGameTime() + DEATH_REVENGE_TICKS);
    }

    /** If the NPC despawned, the despawn handlers run (1.12.2: the respawn data capability turned the NPC back into a spawner). */
    @Override
    public void checkDespawn() {
        boolean removedBefore = isRemoved();

        super.checkDespawn();

        if (!removedBefore && isRemoved()) {
            for (DespawnHandler handler : DESPAWN_HANDLERS) {
                handler.onDespawn(this);
            }
        }
    }

    public void setCanDespawn() {
        canDespawn = true;
    }

    /** The trader that leads the caravan this NPC escorts, or null. */
    @Nullable
    public UUID getCaravanLeader() {
        return caravanLeader;
    }

    public void setCaravanLeader(@Nullable UUID leader) {
        this.caravanLeader = leader;
    }

    /**
     * The extra attributes of the faction defaults (sun burn, undead, mount, sound, heal amount). 1.12.2 kept them in a map of typed
     * keys on the NPC; the defaults data is an immutable record now, so this is the one of the defaults applied to this NPC.
     */
    public FactionAdditionalAttributes getAdditionalAttributes() {
        return additionalAttributes;
    }

    /** The entry of {@code faction_npc_defaults.json} for this faction and type, or null (no faction name yet, or an unknown faction). */
    @Nullable
    @Override
    public FactionNpcDefault getNpcDefault() {
        return factionName == null ? null : NpcDefaultsRegistry.getFactionNpcDefault(factionName, getNpcType());
    }

    public boolean burnsInSun() {
        return additionalAttributes.isBurnsInSun();
    }

    public boolean isUndead() {
        return additionalAttributes.isUndead();
    }

    @Override
    public boolean isFactionSoldier() {
        return true;
    }

    @Override
    public void aiStep() {
        doSunBurn();
        super.aiStep();
    }

    /** Burns in daylight without a helmet (the vanilla sun burn rules); a helmet takes damage instead. */
    private void doSunBurn() {
        if (burnsInSun() && !level().isClientSide && isSunBurnTick()) {
            ItemStack helmet = getItemBySlot(EquipmentSlot.HEAD);
            if (helmet.isEmpty()) {
                igniteForSeconds(8);
            } else {
                damageHelmet(helmet);
            }
        }
    }

    private void damageHelmet(ItemStack helmet) {
        if (helmet.isDamageableItem()) {
            helmet.setDamageValue(helmet.getDamageValue() + random.nextInt(2));

            if (helmet.getDamageValue() >= helmet.getMaxDamage()) {
                onEquippedItemBroken(helmet.getItem(), EquipmentSlot.HEAD);
                setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            }
        }
    }

    /** Undead NPCs are hurt by healing and healed by harming effects like vanilla undead (1.12.2: {@code EnumCreatureAttribute.UNDEAD}). */
    @Override
    public boolean isInvertedHealAndHarm() {
        return isUndead() || super.isInvertedHealAndHarm();
    }

    /**
     * Lizardmen and the coven are immune to poison. Original bug fixed: the 1.12.2 code compared the faction with the string
     * {@code "lizardman|coven"}, which no faction has, so nobody was immune. The priests are immune to poison (they throw it).
     */
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        if (effect.is(MobEffects.POISON) && ("lizardman".equals(getFaction()) || "coven".equals(getFaction()))) {
            return false;
        }
        return super.canBeAffected(effect);
    }

    public void setFactionNameAndDefaults(String factionName) {
        this.factionName = factionName;
        FactionNpcDefault npcDefault = getNpcDefault();
        if (npcDefault == null) {
            LOGGER.error("No faction NPC defaults for {} {}, the NPC keeps the plain mob stats", factionName, getNpcType());
            return;
        }
        applyFactionNpcSettings(npcDefault);
        // do not apply the default equipment if the hasCustomEquipment tag was set to true
        if (!getCustomEquipmentOverride()) {
            npcDefault.applyEquipment(this);
        }

        if (AWNPCStatics.vanillaEquipmentDropRate) {
            // necessary to reset this to the default values as many old structures had NPCs scanned with 1.0f drop rates
            setDropChances(0.085f);
        } else {
            // makes faction NPCs drop all their items otherwise use the default vanilla drop rate
            setDropChances(1.f);
        }

        float newHeight = npcDefault.getHeightRange().random(level().random);
        float newWidth = (newHeight / 1.8f) * 0.6f * npcDefault.getThinness();
        setNpcSize(newWidth, newHeight);
    }

    private void setDropChances(float chance) {
        for (int i = 0; i < armorDropChances.length; i++) {
            armorDropChances[i] = chance;
        }
        for (int i = 0; i < handDropChances.length; i++) {
            handDropChances[i] = chance;
        }
    }

    /** 1.12.2: {@code setSize(width, height)} with the width in 0.1 to 10 and the height in 0.1 to 40. */
    private void setNpcSize(float width, float height) {
        npcWidth = Mth.clamp(width, 0.1f, 10f);
        npcHeight = Mth.clamp(height, 0.1f, 40f);
        refreshDimensions();
    }

    /** The random size of this NPC ({@code getDimensions(Pose)} is final, and a sleeping NPC keeps the vanilla 0.2 x 0.2 box). */
    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        if (npcWidth <= 0 || npcHeight <= 0) {
            return super.getDefaultDimensions(pose);
        }
        return EntityDimensions.scalable(npcWidth, npcHeight);
    }

    private void applyFactionNpcSettings(FactionNpcDefault npcDefault) {
        npcDefault.applyAttributes(this);
        additionalAttributes = npcDefault.getAdditionalAttributes();
        xpReward = npcDefault.getExperienceDrop();
        npcDefault.applyPathSettings(getNavigation());
        setSound(additionalAttributes.getEntitySound());
    }

    @Override
    public int getMaxFallDistance() {
        int i = super.getMaxFallDistance();
        if (i > 4) {
            i += level().getDifficulty().getId() * getMaxHealth() / 5;
        }
        if (i >= getHealth()) {
            return (int) getHealth();
        }
        return i;
    }

    @Override
    protected boolean canEditBy(Player player) {
        return player.isCreative();
    }

    @Override
    public boolean hasCommandPermissions(UUID playerUuid, String playerName) {
        return false;
    }

    /** The name is made by {@link NpcBase#getName()}: the type name of the faction ("Brigand Backshooter") and the generated first name. */
    @Override
    protected String getNamePoolFaction() {
        return factionName == null ? "" : factionName;
    }

    @Override
    protected boolean canGenerateName() {
        return factionName != null && !factionName.isEmpty();
    }

    @Override
    protected float getLitBlockWeight(BlockPos pos, LevelReader level) {
        return burnsInSun() ? 1F - level.getLightLevelDependentMagicValue(pos) : super.getLitBlockWeight(pos, level);
    }

    @Override
    public String getNpcFullType() {
        return factionName + "." + super.getNpcFullType();
    }

    /** The NPC faction of this NPC in Spectre Factions (null before the faction is set). */
    @Override
    public Optional<ActorId> getFactionActor() {
        return factionName == null ? Optional.empty() : Optional.of(AwFactions.actorOfFaction(factionName));
    }

    private boolean isRevengeTarget(Entity e) {
        if (e instanceof Player player) {
            return revengePlayers.containsKey(player.getGameProfile().getName());
        }
        return e instanceof NpcPlayerOwned owned && revengePlayers.containsKey(owned.getOwner().getName());
    }

    /**
     * Not the own faction, and: whoever hurt or killed us recently, else HOSTILE by the faction relation. Entities that have no actor
     * (animals, monsters) are hostile if they are in the target list of the faction.
     */
    @Override
    public boolean isHostileTowards(Entity e) {
        if (isRevengeTarget(e)) {
            return true;
        }
        Boolean byRelation = isHostileByRelation(e);
        if (byRelation != null) {
            return byRelation;
        }
        return factionName != null && (FactionRegistry.getFaction(factionName).isTarget(e) || AIHelper.isAdditionalEntityToTarget(e));
    }

    /**
     * The target filter of the archers, the mounted archers and the priests: hostile, and, if the NPC has a home, within 30 blocks of it
     * (1.12.2: the same lambda in each of the three classes).
     */
    protected boolean isHostileTowardsNearHome(Entity entity) {
        if (!isHostileTowards(entity)) {
            return false;
        }
        if (hasHome()) {
            BlockPos home = getHomePosition();
            double dist = entity.distanceToSqr(home.getX() + 0.5d, home.getY(), home.getZ() + 0.5d);
            return dist <= 30 * 30;
        }
        return true;
    }

    /**
     * Drops the revenge entries that ran out. 1.12.2 used the world time (day time) for the check interval, which stops with
     * the daylight cycle; the game time is used here.
     */
    @Override
    public void baseTick() {
        super.baseTick();
        if (!level().isClientSide && level().getGameTime() % REVENGE_LIST_VALIDATION_TICKS == 0) {
            Iterator<Map.Entry<String, Long>> it = revengePlayers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Long> playerRevengeTime = it.next();
                if (level().getGameTime() > playerRevengeTime.getValue()) {
                    it.remove();
                    setTarget(null);
                    setLastHurtByMob(null);
                }
            }
        }

    }

    private String getSound() {
        return entityData.get(SOUND);
    }

    public void setSound(String sound) {
        entityData.set(SOUND, sound);
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return getSoundOrDefault("_hurt", SoundEvents.GENERIC_HURT);
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return getSoundOrDefault("_death", SoundEvents.GENERIC_DEATH);
    }

    /**
     * The creature sound {@code <entity_sound><suffix>}, or the vanilla one if the NPC has no sound set or the sound does not exist
     * (the data names two sounds that were never registered: {@code owlbear} and {@code coven_puppetd}; 1.12.2 played nothing for them).
     */
    private SoundEvent getSoundOrDefault(String suffix, SoundEvent fallback) {
        String sound = getSound();
        if (FactionAdditionalAttributes.NO_SOUND.equals(sound)) {
            return fallback;
        }
        SoundEvent event = AwNpcSounds.getSoundEventFromString(sound + suffix);
        return event == null ? fallback : event;
    }

    private void playFactionAttackSound() {
        // don't play any sound if there is no specific attack sound
        if (!getSound().equals("none")) {
            SoundEvent sound = AwNpcSounds.getSoundEventFromString(getSound() + "_attack");
            if (sound != null) {
                playSound(sound, 1.0F, 1.2F / (random.nextFloat() * 0.2F + 0.9F));
            }
        }
    }

    /** 1.12.2: {@code damageEntity}. A hit by a player (or by the NPC of a player) makes this NPC hostile to that player for 300 ticks. */
    @Override
    protected void actuallyHurt(DamageSource damageSrc, float damageAmount) {
        super.actuallyHurt(damageSrc, damageAmount);

        Entity attacker = damageSrc.getEntity();
        if (attacker instanceof Player player && damageSrc.is(DamageTypes.PLAYER_ATTACK)) {
            revengePlayers.put(player.getGameProfile().getName(), level().getGameTime() + HIT_REVENGE_TICKS);
        } else if (attacker instanceof NpcBase npc && damageSrc.is(DamageTypes.MOB_ATTACK)) {
            revengePlayers.put(npc.getOwner().getName(), level().getGameTime() + HIT_REVENGE_TICKS);
        }
    }

    @Override
    public boolean canTarget(Entity e) {
        if (!(e instanceof LivingEntity)) {
            return false;
        }
        return isRevengeTarget(e) ? !isSameActor(e) : mayAttackByRelation(e);
    }

    @Override
    public boolean canBeAttackedBy(Entity e) {
        //can only be attacked by other factions, not your own...disable friendly fire
        return !isSameActor(e);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if ((Math.random() < 0.2)) {
            playFactionAttackSound();
        }
        return super.doHurtTarget(target);
    }

    /** A kill by a player (or an NPC of a player) changes the player's standing with the faction and makes the faction hostile to the killer for 6000 ticks. */
    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);
        Entity killer = damageSource.getEntity();
        if (!level().isClientSide && (killer instanceof Player || killer instanceof NpcPlayerOwned)) {
            String playerName = killer instanceof Player player ? player.getGameProfile().getName() : ((NpcBase) killer).getOwner().getName();
            // the standing change of the kill is done by Spectre Factions (kill reputation penalty), not here, so it is not counted twice
            setDeathRevengePlayer(playerName);
            level().getEntitiesOfClass(NpcFaction.class, new AABB(blockPosition()).inflate(REVENGE_SET_RANGE))
                    .forEach(factionNpc -> {
                        if (factionNpc.getFaction().equals(getFaction())) {
                            factionNpc.setDeathRevengePlayer(playerName);
                        }
                    });
        }
    }

    /** The loot table of the faction defaults data (1.12.2 had a resource location); none means the empty table. */
    @Override
    protected ResourceKey<LootTable> getDefaultLootTable() {
        FactionNpcDefault npcDefault = getNpcDefault();
        ResourceLocation lootTable = npcDefault == null ? null : npcDefault.getLootTable();
        return lootTable == null ? BuiltInLootTables.EMPTY : ResourceKey.create(Registries.LOOT_TABLE, lootTable);
    }

    @Override
    public String getNpcSubType() {
        return "";
    }

    public String getFaction() {
        return factionName;
    }

    @Nullable
    @Override
    public PlayerTeam getTeam() {
        return null;
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        super.writeSpawnData(buffer);
        buffer.writeUtf(factionName == null ? "" : factionName);
        buffer.writeFloat(npcHeight);
        buffer.writeFloat(npcWidth);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        super.readSpawnData(buffer);
        factionName = buffer.readUtf(20);
        npcHeight = buffer.readFloat();
        npcWidth = buffer.readFloat();
        refreshDimensions();
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        factionName = tag.getString("factionName");
        FactionNpcDefault npcDefault = getNpcDefault();
        if (npcDefault != null) {
            applyFactionNpcSettings(npcDefault);
        }
        canDespawn = tag.getBoolean("canDespawn");
        caravanLeader = tag.hasUUID("caravanLeader") ? tag.getUUID("caravanLeader") : null;
        revengePlayers = NBTHelper.getMap(tag.getList("revengePlayers", Tag.TAG_COMPOUND),
                t -> t.getString("playerName"), t -> t.getLong("time"));
        if (tag.contains(HEIGHT_TAG)) {
            setNpcSize(tag.getFloat("width"), tag.getFloat(HEIGHT_TAG));
        } else {
            setFactionNameAndDefaults(factionName);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (factionName != null) {
            tag.putString("factionName", factionName);
        }
        tag.putBoolean("canDespawn", canDespawn);
        if (caravanLeader != null) {
            tag.putUUID("caravanLeader", caravanLeader);
        }
        tag.put("revengePlayers", NBTHelper.mapToCompoundList(revengePlayers,
                (t, playerName) -> t.putString("playerName", playerName), (t, time) -> t.putLong("time", time)));
        tag.putFloat(HEIGHT_TAG, npcHeight);
        tag.putFloat("width", npcWidth);
    }

    /** The scale the renderer applies to the model, the height of this NPC over the 1.8 of the standard one. */
    public float getRenderSizeModifier() {
        return npcHeight / 1.8f;
    }

    public float getWidthModifier() {
        return npcWidth / 0.6f;
    }
}
