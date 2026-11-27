/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/NpcBase.
 */
package io.github.capttracker.ancientwarfarenpc.entity;

import io.github.capttracker.ancientwarfarenpc.faction.AwFactions;
import io.github.capttracker.spectrefactions.api.ActorId;
import io.github.capttracker.spectrefactions.api.FactionsApi;
import io.github.capttracker.spectrefactions.api.GuardMode;
import io.github.capttracker.spectrefactions.api.Relation;
import io.github.capttracker.spectrefactions.config.FactionsConfig;
import io.github.capttracker.ancientwarfare.api.IEntityPacketHandler;
import io.github.capttracker.ancientwarfare.owner.IOwnable;
import io.github.capttracker.ancientwarfare.owner.Owner;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfare.util.WorldTools;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIBlockWithShield;
import io.github.capttracker.ancientwarfarenpc.ai.NpcNavigator;
import io.github.capttracker.ancientwarfarenpc.ai.path.NpcRouteAssist;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFaction;
import io.github.capttracker.ancientwarfarenpc.registry.NpcDefault;
import io.github.capttracker.ancientwarfarenpc.skin.NpcSkinSettings;
import io.github.capttracker.ancientwarfarevehicle.entity.IPathableEntity;
import io.github.capttracker.ancientwarfarevehicle.entity.IVehicleCrew;
import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.pathing.Node;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import io.github.capttracker.ancientwarfarenpc.skill.NpcSkills;
import io.github.capttracker.ancientwarfarenpc.skill.Rank;
import io.github.capttracker.ancientwarfarenpc.skill.Skill;
import io.github.capttracker.ancientwarfarenpc.skill.SkillEffects;
import io.github.capttracker.ancientwarfarenpc.trait.NpcTraits;
import io.github.capttracker.ancientwarfarenpc.trait.Trait;
import io.github.capttracker.ancientwarfarenpc.trait.TraitEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics.npcLevelDamageMultiplier;

/**
 * Base of all NPCs: ownership, leveling, upkeep hooks, orders and upkeep slots, home and bed handling, shield blocking,
 * skin settings and the status bitfield the renderer draws icons from.
 *
 * <h2>Changes from 1.12.2</h2>
 * <ul>
 *   <li>Goals are assembled in {@link #registerGoals()} of the concrete classes. {@code Mob}'s constructor calls it
 *       (server side only), before the fields of this class and of its subclasses are initialised. Subclass fields that
 *       registerGoals assigns therefore have no initialiser, and goal constructors must not read NPC state.</li>
 *   <li>Sleeping uses the vanilla sleeping position and {@code Pose.SLEEPING} (hit box 0.2 x 0.2, synced to clients)
 *       instead of the custom {@code IS_SLEEPING}, {@code BED_POS} and {@code BED_DIRECTION} synced values.</li>
 *   <li>The vanilla attack, armor and shield code that the original copied is gone: {@code doHurtTarget} calls the vanilla
 *       one, armor uses the armor attribute (an armor override replaces the total), shields use the vanilla blocking with
 *       the original's extra rules in {@link #hurtCurrentlyUsedShield} and {@link #blockUsingShield}.</li>
 *   <li>The equipment slots of the container are the 1.12.2 indices 0 to 5 (main hand, off hand, feet, legs, chest, head)
 *       plus {@link #ORDER_SLOT} and {@link #UPKEEP_SLOT}: see {@link #getItemBySlot(int)}.</li>
 * </ul>
 */
public abstract class NpcBase extends PathfinderMob implements IEntityWithComplexSpawn, IOwnable, IEntityPacketHandler, IPathableEntity, IVehicleCrew, Npc {

    private static final EntityDataAccessor<Integer> AI_TASKS = SynchedEntityData.defineId(NpcBase.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SHIELD_DISABLED_TICK = SynchedEntityData.defineId(NpcBase.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SWINGING_ARMS = SynchedEntityData.defineId(NpcBase.class, EntityDataSerializers.BOOLEAN);
    /** The generated first name ({@link NpcNames}); empty until the first server tick. */
    private static final EntityDataAccessor<String> GIVEN_NAME = SynchedEntityData.defineId(NpcBase.class, EntityDataSerializers.STRING);
    /** The rank (0 to 4) of the main skill for the name, or -1 for none (rework phase 7). */
    private static final EntityDataAccessor<Integer> MAIN_RANK = SynchedEntityData.defineId(NpcBase.class, EntityDataSerializers.INT);

    private static final String SLOT_NUM_TAG = "slotNum";
    private static final String CACHED_BED_POS_TAG = "cachedBedPos";
    private static final String FOUND_BED_TAG = "foundBed";
    private static final String ORDERS_STACK_TAG = "ordersStack";
    private static final String UPKEEP_STACK_TAG = "upkeepStack";
    private static final String LEVELING_STATS_TAG = "levelingStats";
    private static final String MAX_HEALTH_TAG = "maxHealth";
    private static final String HEALTH_TAG = "health";
    private static final String ATTACK_DAMAGE_OVERRIDE_TAG = "attackDamageOverride";
    private static final String ARMOR_VALUE_OVERRIDE_TAG = "armorValueOverride";
    private static final String AI_ENABLED_TAG = "aiEnabled";
    private static final String HAS_CUSTOM_EQUIPMENT_TAG = "hasCustomEquipment";
    private static final String DO_NOT_PURSUE = "donotpursue";
    private static final String ATTACK_TARGET_TAG = "attackTarget";
    private static final String OWNER_NAME_TAG = "ownerName";

    /** The container slot of the orders stack (the 1.12.2 slot numbers 0 to 5 are the vanilla equipment slots, see {@link #getEquipmentSlot(int)}). */
    public static final int ORDER_SLOT = 6;
    /** The container slot of the upkeep orders stack. */
    public static final int UPKEEP_SLOT = 7;

    /** The 1.12.2 {@code EntityEquipmentSlot.values()} order: the slot numbers of the container and of the equipment maps of the NPC defaults. */
    private static final EquipmentSlot[] SLOTS_BY_INDEX = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
    };

    public ItemStack ordersStack = ItemStack.EMPTY;
    public ItemStack upkeepStack = ItemStack.EMPTY;

    // No initialisers on the fields that goals or registerGoals may touch while Mob's constructor runs (see the class comment).
    @Nullable
    private NpcAIBlockWithShield shieldBlockAI;
    private Owner owner = Owner.EMPTY;

    @Nullable
    private String followingPlayerName;//set/cleared onInteract from player if player.team==this.team
    private final NpcLevelingStats levelingStats;

    private NpcSkinSettings skinSettings = new NpcSkinSettings();

    // used for flee/distress AI
    // this isn't really useful yet, combat NPC's will attack any mob they come in
    // contact with during a distress response
    public Set<Entity> nearbyHostiles = new LinkedHashSet<>();

    private boolean aiEnabled = true;
    public boolean doNotPursue = false; //if the npc should not pursue targets away from its position/route
    private boolean hasCustomEquipment = false; //faction based only

    private int attackDamage = -1;//faction based only
    private int armorValue = -1;//faction based only
    private int maxHealthOverride = -1;

    @Nullable
    private BlockPos cachedBedPos;
    private boolean foundBed = false;
    private boolean rainedOn = false;

    private int stopAIControlFlags = 0;

    protected NpcBase(EntityType<? extends NpcBase> type, Level level) {
        super(type, level);
        levelingStats = new NpcLevelingStats(this);
        setPathfindingMalus(PathType.DOOR_WOOD_CLOSED, 0.0F);
    }

    /**
     * The attribute set of every NPC: the mob attributes plus attack damage and attack speed (1.12.2: {@code applyEntityAttributes}
     * registered those two on the attribute map). The values come from the NPC defaults data when the entity is created.
     */
    public static AttributeSupplier.Builder createNpcAttributes() {
        return Mob.createMobAttributes().add(Attributes.ATTACK_DAMAGE).add(Attributes.ATTACK_SPEED);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new NpcNavigator(this, level);
    }

    /** Created on first use: the goals are registered inside the constructor of {@code Mob}, before the initialisers of this class run. */
    @Nullable
    private NpcRouteAssist routeAssist;

    /** The routes over ladders for the NPC that cannot get somewhere on foot (down a mine and back up). Server side. */
    public NpcRouteAssist getRouteAssist() {
        if (routeAssist == null) {
            routeAssist = new NpcRouteAssist(this);
        }
        return routeAssist;
    }

    /** Created on first use, like the route assist. */
    @Nullable
    private NpcMind mind;

    /** The recent thoughts of this NPC (talk screen). */
    public NpcMind getMind() {
        if (mind == null) {
            mind = new NpcMind();
        }
        return mind;
    }

    /** Server side: writes an event to the recent thoughts ({@code npc.thought.event.<event>.1-3}); see {@link NpcMind#note}. */
    public void think(String event, Component... args) {
        if (!level().isClientSide && isAlive()) {
            getMind().note(this, event, args);
        }
    }

    // what the NPC is doing now, as the goals tell (server side, not saved): the key of {@code npc.thought.now.<key>.1-3}, a target position and an argument
    @Nullable
    private String doingKey;
    @Nullable
    private BlockPos doingPos;
    @Nullable
    private Component doingArg;
    private long doingTime = -1000L;

    /** Called by the goals every tick or so while they act: what the NPC does ({@code key}), where ({@code pos}, may be null) and with what (may be null). */
    public void setDoing(String key, @Nullable BlockPos pos, @Nullable Component arg) {
        doingKey = key;
        doingPos = pos;
        doingArg = arg;
        doingTime = level().getGameTime();
    }

    /** The key of what the goals last said the NPC does, or null if that is more than 5 seconds old. */
    @Nullable
    public String getDoingKey() {
        return doingKey != null && level().getGameTime() - doingTime <= 100L ? doingKey : null;
    }

    @Nullable
    public BlockPos getDoingPos() {
        return doingPos;
    }

    @Nullable
    public Component getDoingArg() {
        return doingArg;
    }

    private int lastWorkSwing = -1000;

    /**
     * Swings the main hand at the pace of {@code swing_interval_ticks} (default 12, a normal pace; a player swings about every 4 ticks): call it every tick
     * of an action in progress, it swings when the interval has passed. The swing is broadcast to the clients by {@code LivingEntity.swing}.
     *
     * @return true if it swung on this call
     */
    public boolean workSwing() {
        if (tickCount - lastWorkSwing >= SettlementConfig.swingIntervalTicks) {
            lastWorkSwing = tickCount;
            swing(InteractionHand.MAIN_HAND);
            return true;
        }
        return false;
    }

    /** Runs before the move control of this tick, so the route can steer the NPC. */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (routeAssist != null) {
            routeAssist.tick();
        }
    }

    public void stopAIControlFlag(int flag) {
        stopAIControlFlags |= flag;
    }

    public void startAIControlFlag(int flag) {
        stopAIControlFlags &= ~flag;
    }

    public boolean isAIFlagStopped(int flag) {
        return (stopAIControlFlags & flag) > 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(AI_TASKS, 0);
        builder.define(SWINGING_ARMS, false);
        builder.define(SHIELD_DISABLED_TICK, 0);
        builder.define(GIVEN_NAME, "");
        builder.define(MAIN_RANK, -1);
    }

    /** The generated first name, or empty if there is none yet. */
    public String getGivenName() {
        return entityData.get(GIVEN_NAME);
    }

    public void setGivenName(String name) {
        entityData.set(GIVEN_NAME, name == null ? "" : name);
    }

    // ---- surname (rework phase 10) ----

    /** The family name of the NPC; empty for the NPCs that have none (only the player-owned NPCs get one). Server side; not synched to the clients. */
    private String surname = "";

    public String getSurname() {
        return surname;
    }

    public void setSurname(String name) {
        surname = name == null ? "" : name;
    }

    /** Whether this kind of NPC has a surname. */
    protected boolean supportsSurname() {
        return false;
    }

    /**
     * The full name ("Bill Carter"): a custom name (name tag) first, else the first name with the surname, else the plain display name. This is the name of
     * the talk screen, the census and the look-at tooltip; the nameplate keeps {@link #getName()} ("Apprentice Miner Bill").
     */
    public Component getFullName() {
        Component customName = getCustomName();
        if (customName != null) {
            return customName;
        }
        if (surname.isEmpty() || getGivenName().isEmpty()) {
            return getName();
        }
        return NpcNames.full(getGivenName(), surname);
    }

    /** Server side: gives an NPC that has a first name but no surname one (new NPCs and NPCs saved before families existed). */
    private void ensureSurname() {
        if (surname.isEmpty() && supportsSurname() && !getGivenName().isEmpty()) {
            surname = NpcNames.pickSurname(getRandom(), getNamePoolFaction(), getGivenName());
        }
    }

    /** The faction whose name pool the first name comes from; empty for the player-owned NPCs. */
    protected String getNamePoolFaction() {
        return "";
    }

    /** False while the NPC cannot yet tell which name pool it draws from (a faction NPC before its faction is set). */
    protected boolean canGenerateName() {
        return true;
    }

    /** Server side: gives an NPC without a first name one (new NPCs and NPCs saved before names existed). */
    private void ensureGivenName() {
        if (getGivenName().isEmpty() && canGenerateName()) {
            setGivenName(NpcNames.pick(getRandom(), getNamePoolFaction()));
        }
    }

    // ---- attributes and overrides ----

    private AttributeInstance attribute(Holder<Attribute> attribute) {
        return Objects.requireNonNull(getAttribute(attribute));
    }

    ItemStack getShieldStack() {
        return getOffhandItem();
    }

    public void setShieldStack(ItemStack stack) {
        setItemSlot(EquipmentSlot.OFFHAND, stack);
    }

    public int getMaxHealthOverride() {
        return maxHealthOverride;
    }

    public void setMaxHealthOverride(int maxHealthOverride) {
        this.maxHealthOverride = maxHealthOverride;
        if (maxHealthOverride > 0) {
            attribute(Attributes.MAX_HEALTH).setBaseValue(maxHealthOverride);
            if (getHealth() < getMaxHealth()) {
                setHealth(getMaxHealth());
            }
        }
    }

    /** Sets the base of the max health attribute (leveling). */
    void setBaseMaxHealth(double health) {
        attribute(Attributes.MAX_HEALTH).setBaseValue(health);
    }

    public void setAttackDamageOverride(int attackDamage) {
        this.attackDamage = attackDamage;
        attribute(Attributes.ATTACK_DAMAGE).setBaseValue(attackDamage);
    }

    /** The total armor of this NPC is this value, whatever it wears; negative: use the armor attribute (equipment adds to it). */
    public void setArmorValueOverride(int armorValue) {
        this.armorValue = armorValue;
    }

    public int getArmorValueOverride() {
        return armorValue;
    }

    public int getAttackDamageOverride() {
        return attackDamage;
    }

    public void setCustomEquipmentOverride(boolean val) {
        hasCustomEquipment = val;
    }

    public boolean getCustomEquipmentOverride() {
        return hasCustomEquipment;
    }

    /**
     * 1.12.2 copied the body of {@code EntityMob.attackEntityAsMob} and swapped the off-hand shield's attribute modifiers in
     * around it. Here the vanilla method does the attack (enchantments, knockback, fire, post-attack effects, which includes the
     * player shield disabling of the vanilla {@code Player.blockUsingShield}); the held item's hit hooks are called afterwards
     * so that weapons with effects (extended reach weapons with potion effects) apply. The attribute modifiers of an off-hand
     * item are applied by the vanilla equipment handling.
     */
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean targetHit = super.doHurtTarget(target);
        if (targetHit) {
            gainSkillXp(Skill.COMBAT, SettlementConfig.skillXpHit);
        }
        if (targetHit && target instanceof LivingEntity livingTarget) {
            ItemStack weapon = getMainHandItem();
            if (!weapon.isEmpty()) {
                weapon.getItem().hurtEnemy(weapon, livingTarget, this);
                weapon.getItem().postHurtEnemy(weapon, livingTarget, this);
            }
        }
        return targetHit;
    }

    /**
     * 1.12.2: {@code getTotalArmorValue} (override) and {@code applyArmorCalculations} (Forge {@code ISpecialArmor}). The 1.21.1
     * damage reduction reads this value and the armor toughness attribute, so one override covers both.
     */
    @Override
    public int getArmorValue() {
        if (getArmorValueOverride() >= 0) {
            return getArmorValueOverride();
        }
        return super.getArmorValue();
    }

    @Override
    public int getMaxFallDistance() {
        return getTarget() == null ? 4 : 4 + (int) (getHealth() / 3);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        BlockState stateBelow = level.getBlockState(pos.below());
        if (stateBelow.getFluidState().is(FluidTags.LAVA) || stateBelow.is(Blocks.CACTUS)) {//Avoid cacti and lava when wandering
            return -10;
        } else if (!stateBelow.getFluidState().isEmpty()) {//Don't try swimming too much
            return 0;
        }
        float lightLevel = getLitBlockWeight(pos, level);//Prefer lit areas
        if (lightLevel < 0) {
            return 0;
        } else {
            return lightLevel + (stateBelow.isFaceSturdy(level, pos.below(), Direction.UP) ? 1 : 0);
        }
    }

    protected float getLitBlockWeight(BlockPos pos, LevelReader level) {
        return level.getLightLevelDependentMagicValue(pos);
    }

    // ---- home (the 1.12.2 EntityCreature home position is the Mob restriction in 1.21.1) ----

    public boolean hasHome() {
        return hasRestriction();
    }

    public BlockPos getHomePosition() {
        return getRestrictCenter();
    }

    public void setHomePosAndDistance(BlockPos pos, int distance) {
        restrictTo(pos, distance);
    }

    public double getDistanceSqFromHome() {
        if (!hasHome()) {
            return 0;
        }
        BlockPos home = getHomePosition();
        return distanceToSqr(home.getX() + 0.5d, home.getY(), home.getZ() + 0.5d);
    }

    public Optional<BlockPos> getTownHallPosition() {
        return Optional.empty();//NOOP on non-player owned npc
    }

    public void setHomeAreaAtCurrentPosition() {
        setHomePosAndDistance(blockPosition(), getHomeRange());
    }

    public int getHomeRange() {
        if (hasHome()) {
            return Mth.floor(getRestrictRadius());
        }
        return 5;
    }

    /**
     * Return true if this NPC should be within his home range.<br>
     * Should still allow for a combat NPC to attack targets outside his home range.
     */
    public boolean shouldBeAtHome() {
        if (getTarget() != null || !hasHome()) {
            return false;
        }
        if (level().isRainingAt(blockPosition())) {
            setRainedOn(true);
        }
        return shouldSleep() || isWaitingForRainToStop();
    }

    private boolean isWaitingForRainToStop() {
        if (worksInRain() || !level().isRaining()) {
            // rain has stopped, reset
            setRainedOn(false);
            return false;
        }
        return rainedOn;
    }

    public boolean worksInRain() {
        return false;
    }

    public boolean isPassive() {
        return true;
    }

    private void setRainedOn(boolean rainedOn) {
        this.rainedOn = rainedOn;
    }

    public void setIsAIEnabled(boolean val) {
        aiEnabled = val;
    }

    public boolean getIsAIEnabled() {
        return aiEnabled && !AWNPCStatics.npcAIDebugMode;
    }

    // ---- interaction ----

    @Override
    protected final InteractionResult mobInteract(Player player, InteractionHand hand) {
        return processInteract(player, hand) ? InteractionResult.sidedSuccess(level().isClientSide) : InteractionResult.PASS;
    }

    /** 1.12.2: {@code processInteract}; true if the interaction was handled. Both sides call it. */
    protected boolean processInteract(Player player, InteractionHand hand) {
        return tryCommand(player);
    }

    /**
     * should be implemented by any npc that wishes to open a GUI on interact<br>
     * must be called from interact code to actually open the GUI<br>
     * allows for subtypes/etc to vary the opened GUI without re-implementing the interact logic
     */
    public void openGUI(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            NpcBridge.openGui(serverPlayer, this, NpcBridge.Gui.INVENTORY);
        }
    }

    /**
     * if this npc has an alt-control GUI, open it here.<br>
     * should called from the npc inventory gui.
     */
    public void openAltGui(Player player) {

    }

    /**
     * used by the npc inventory gui to determine if it should display the 'alt control gui' button<br>
     * this setting must return true -on the client- if the button is to be displayed.
     */
    public boolean hasAltGui() {
        return false;
    }

    /** Whether the player may edit this NPC (open its inventory screen): the commanders of a player-owned NPC; a faction NPC overrides it (creative players). */
    protected boolean canEditBy(Player player) {
        return hasCommandPermissions(player.getUUID(), player.getGameProfile().getName());
    }

    /**
     * Right-click: the editor opens the inventory screen, sneak + right-click with an empty hand opens the talk screen and with an item in hand toggles
     * follow (as before); anyone else who is not an enemy gets the talk screen.
     */
    protected boolean tryCommand(Player player) {
        boolean baton = NpcItemIds.isCommandBaton(player.getMainHandItem()) || NpcItemIds.isCommandBaton(player.getOffhandItem());
        if (baton) {
            return false;
        }
        if (!canEditBy(player)) {
            if (level().isClientSide) {
                return true; // the server decides (an enemy gets nothing)
            }
            if (isHostileTowards(player)) {
                return false;
            }
            openTalk(player);
            return true;
        }
        if (!level().isClientSide) {
            if (player.isShiftKeyDown()) {
                if (player.getMainHandItem().isEmpty()) {
                    openTalk(player);
                } else {
                    String playerName = player.getGameProfile().getName();
                    if (followingPlayerName != null && followingPlayerName.equals(playerName)) {
                        followingPlayerName = null;
                    } else {
                        followingPlayerName = playerName;
                    }
                }
            } else {
                openGUI(player);
            }
        }
        return true;
    }

    /** Opens the talk screen (what the NPC is thinking) for the player. Server side. */
    public void openTalk(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            NpcBridge.openGui(serverPlayer, this, NpcBridge.Gui.TALK);
        }
    }

    @Override
    public final boolean hurt(DamageSource source, float damage) {
        if (isSleeping()) { // prevent suffocation damage (allows bunk beds and such)
            return false;
        }
        if (source.getEntity() != null && !canBeAttackedBy(source.getEntity())) {
            return false;
        }
        if (!level().isClientSide && supportsSkills() && source.getEntity() instanceof LivingEntity) {
            damage *= (float) SkillEffects.damageTakenFactor(getSkills().getRank(Skill.COMBAT)); // a ranked fighter takes less from creatures
        }
        if (!level().isClientSide) {
            processPreDamageLogic(source, damage);
        }
        boolean hurt = super.hurt(source, damage);
        if (hurt && !level().isClientSide && isAlive()) {
            Entity attacker = source.getEntity();
            think("hurt", attacker == null ? Component.translatable("npc.thought.something") : attacker.getName());
            boolean nearDeath = getHealth() < getMaxHealth() * 0.3F;
            if (nearDeath) {
                think("near_death");
            }
            onInjured(nearDeath);
        }
        return hurt;
    }

    /** Server side, after the NPC took damage: lets the player-owned NPCs note it for their morale. */
    protected void onInjured(boolean nearDeath) {
    }

    /** The NPC went to bed (server side). */
    protected void onFellAsleep() {
    }

    /** The NPC got up from its bed (server side). */
    protected void onWokeUp() {
    }

    private void processPreDamageLogic(DamageSource source, float damage) {
        if (shieldBlockAI != null) {
            shieldBlockAI.onPreDamage(source, damage);
        }
    }

    /** While an item is in use (a shield, a bow) the NPC is slow; called after the move control set the speed for this tick. */
    @Override
    public void travel(Vec3 travelVector) {
        if (isSleeping()) {
            jumping = false;
            xxa = 0.0F;
            zza = 0.0F;
            super.travel(Vec3.ZERO);
            return;
        }
        if (isUsingItem() && !isPassenger()) {
            setSpeed(getSpeed() * 0.2f);
            setSprinting(false);
        }
        super.travel(travelVector);
    }

    /** 1.12.2: {@code damageShield}. The shield takes damage when the blow is at least 3 and breaks with the shield break sound. */
    @Override
    protected void hurtCurrentlyUsedShield(float damage) {
        if (useItem.canPerformAction(ItemAbilities.SHIELD_BLOCK) && damage >= 3.0F) {
            int amount = 1 + Mth.floor(damage);
            InteractionHand hand = getUsedItemHand();
            EquipmentSlot slot = getSlotForHand(hand);
            ItemStack shield = getItemInHand(hand);
            shield.hurtAndBreak(amount, this, slot);
            if (shield.isEmpty()) {  //shield breaks
                setItemSlot(slot, ItemStack.EMPTY);
                stopUsingItem();
                playSound(SoundEvents.SHIELD_BREAK, 0.8F, 0.8F + level().random.nextFloat() * 0.4F);
            }
        }
    }

    /** 1.12.2: the attacker is pushed back by 0.1 and the shield is disabled if the attacker's weapon can do that. */
    @Override
    protected void blockUsingShield(LivingEntity attacker) {
        attacker.knockback(0.1D, getX() - attacker.getX(), getZ() - attacker.getZ());

        ItemStack weapon = attacker.getMainHandItem();
        if (weapon.canDisableShield(useItem, this, attacker)) {
            disableShield();
        }
    }

    private void disableShield() {
        // 1.12.2: chance 1 + efficiency * 0.05 for a random float below 1, that is always
        setShieldDisabledTick(80);
        stopUsingItem();
        level().broadcastEntityEvent(this, (byte) 30);
    }

    // ---- targets ----

    @Override
    public void setTarget(@Nullable LivingEntity entity) {
        if (entity != null && !canTarget(entity)) {
            return;
        }
        super.setTarget(entity);

        if (!level().isClientSide && entity != null) {
            updateAttackTargetClient(entity);
        }
    }

    private void updateAttackTargetClient(LivingEntity entity) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(ATTACK_TARGET_TAG, entity.getId());
        NpcEntityPayloads.sendToTracking(this, tag);
    }

    /** 1.12.2: {@code setRevengeTarget} (final). */
    @Override
    public void setLastHurtByMob(@Nullable LivingEntity entity) {
        if (entity != null && !canTarget(entity)) {
            return;
        }
        super.setLastHurtByMob(entity);
    }

    /** 1.12.2: {@code onKillEntity} (final). */
    @Override
    public boolean killedEntity(ServerLevel level, LivingEntity entity) {
        boolean result = super.killedEntity(level, entity);
        addExperience(AWNPCStatics.npcXpFromKill);
        gainSkillXp(Skill.COMBAT, SettlementConfig.skillXpKill);
        think("won_fight", entity.getName());
        if (entity == getTarget()) {
            setTarget(null);
        }
        return result;
    }

    // ---- equipment and inventory slots ----

    /** The vanilla slot of the 1.12.2 slot number 0 to 5. */
    public static EquipmentSlot getEquipmentSlot(int index) {
        return SLOTS_BY_INDEX[index];
    }

    /** The 1.12.2 slot number (0 to 5) of a vanilla slot, or -1 for the body slot. */
    public static int getEquipmentSlotIndex(EquipmentSlot slot) {
        for (int i = 0; i < SLOTS_BY_INDEX.length; i++) {
            if (SLOTS_BY_INDEX[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The stack in a slot of the NPC inventory: 0 to 5 the equipment ({@link #getEquipmentSlot(int)}), {@link #ORDER_SLOT} the
     * orders and {@link #UPKEEP_SLOT} the upkeep orders (1.12.2: {@code getItemStackFromSlot(int)}).
     */
    public final ItemStack getItemBySlot(int slot) {
        if (slot == ORDER_SLOT) {
            return ordersStack;
        } else if (slot == UPKEEP_SLOT) {
            return upkeepStack;
        } else if (slot >= 0 && slot < SLOTS_BY_INDEX.length) {
            return getItemBySlot(SLOTS_BY_INDEX[slot]);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        super.setItemSlot(slot, stack);
        if (slot == EquipmentSlot.MAINHAND) {
            onWeaponInventoryChanged();
        }
        if (slot == EquipmentSlot.OFFHAND) {
            onOffhandInventoryChanged();
        }
    }

    /** Sets a slot of the NPC inventory, see {@link #getItemBySlot(int)} (1.12.2: {@code setItemStackToSlot(int, ItemStack)}). */
    public final void setItemSlot(int slot, ItemStack stack) {
        if (slot >= 0 && slot < SLOTS_BY_INDEX.length) {
            setItemSlot(SLOTS_BY_INDEX[slot], stack);
        } else if (slot == UPKEEP_SLOT) {
            upkeepStack = stack;
        } else if (slot == ORDER_SLOT) {
            ordersStack = stack;
            onOrdersInventoryChanged();
        }
    }

    // ---- ai task flags ----

    /**
     * return the bitfield containing all of the currently executing AI tasks<br>
     * used by player-owned npcs for rendering ai-tasks
     */
    public final int getAITasks() {
        return entityData.get(AI_TASKS);
    }

    /**
     * add a task to the bitfield of currently executing tasks<br>
     * input should be a ^2, or combination of (e.g. 1+2 or 2+4)<br>
     */
    public final void addAITask(int task) {
        int tasks = getAITasks();
        int tc = tasks;
        tasks = tasks | task;
        if (tc != tasks) {
            setAITasks(tasks);
        }
    }

    /**
     * remove a task from the bitfield of currently executing tasks<br>
     * input should be a ^2, or combination of (e.g. 1+2 or 2+4)<br>
     */
    public final void removeAITask(int task) {
        int tasks = getAITasks();
        int tc = tasks;
        tasks = tasks & (~task);
        if (tc != tasks) {
            setAITasks(tasks);
        }
    }

    /** set ai tasks -- only used internally */
    private void setAITasks(int tasks) {
        entityData.set(AI_TASKS, tasks);
    }

    /**
     * add an amount of experience to this npcs leveling stats<br>
     * experience is added for base level, and subtype level(if any)
     */
    public final void addExperience(int amount) {
        getLevelingStats().addExperience(amount);
    }

    // ---- skills (rework phase 7) ----

    private static final ResourceLocation SKILL_DAMAGE_ID = ResourceLocation.fromNamespaceAndPath("ancientwarfarenpc", "skill_damage");
    /** Created on first use, like the mind. */
    @Nullable
    private NpcSkills skills;
    private int appliedCombatRank = -1;
    /** Game time of the last experience of each skill that has a cooldown ({@link Skill#xpCooldownTicks}); not saved. */
    private final long[] lastSkillXpAt = new long[Skill.ALL.length];

    /** True for the NPCs that learn skills (the player-owned ones). */
    public boolean supportsSkills() {
        return false;
    }

    /** The experience of this NPC in each skill (server side; the client gets the numbers with the talk menu). */
    public NpcSkills getSkills() {
        if (skills == null) {
            skills = new NpcSkills();
        }
        return skills;
    }

    public int getSkillRank(Skill skill) {
        return getSkills().getRank(skill);
    }

    /** The main skill of this NPC (the one its title shows), or null. */
    @Nullable
    public Skill getMainSkill() {
        return supportsSkills() ? Skill.mainOf(getNpcFullType()) : null;
    }

    /** The rank index of the main skill for the name (client: synced); -1 for none. */
    public int getMainRank() {
        return entityData.get(MAIN_RANK);
    }

    /**
     * Server side: experience for a skill. {@code base} is the amount before the {@code skill_xp_multiplier}. A new rank is written as a thought, lifts the
     * morale and plays a sound and a particle.
     */
    public final void gainSkillXp(@Nullable Skill skill, int base) {
        if (skill == null || base <= 0 || level().isClientSide || !supportsSkills() || !isAlive() || SettlementConfig.skillXpMultiplier <= 0.0D) {
            return;
        }
        int cooldown = skill.xpCooldownTicks();
        if (cooldown > 0) {
            long now = level().getGameTime();
            if (lastSkillXpAt[skill.ordinal()] > 0L && now - lastSkillXpAt[skill.ordinal()] < cooldown) {
                return; // a repeatable action (trades, courier stops) does not farm experience
            }
            lastSkillXpAt[skill.ordinal()] = Math.max(1L, now);
        }
        int amount = Math.max(1, (int) Math.round(base * SettlementConfig.skillXpMultiplier * TraitEffects.xpFactor(this)));
        int rank = getSkills().add(skill, amount);
        if (rank >= 0) {
            think("skill_rank", Component.translatable(skill.langKey()), Component.translatable(Rank.byIndex(rank).langKey()));
            refreshSkillState();
            onRankUp(skill, rank);
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, blockPosition(), SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.NEUTRAL, 0.4F, 1.3F);
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, getX(), getY() + getBbHeight() + 0.2D, getZ(), 8, 0.4D, 0.3D, 0.4D, 0.0D);
            }
        }
    }

    /** Server side: the skill reached a new rank (the player-owned NPCs lift their morale). */
    protected void onRankUp(Skill skill, int rank) {
    }

    /** Server side: brings the synced main rank (the name) and the damage bonus of the combat rank up to date. Cheap; called now and then. */
    public final void refreshSkillState() {
        if (level().isClientSide || !supportsSkills()) {
            return;
        }
        Skill main = SettlementConfig.skillRankInName ? getMainSkill() : null;
        int rank = main == null ? -1 : getSkills().getRank(main);
        if (entityData.get(MAIN_RANK) != rank) {
            entityData.set(MAIN_RANK, rank);
        }
        int combat = getSkills().getRank(Skill.COMBAT);
        if (combat != appliedCombatRank) {
            appliedCombatRank = combat;
            AttributeInstance damage = getAttribute(Attributes.ATTACK_DAMAGE);
            if (damage != null) {
                damage.removeModifier(SKILL_DAMAGE_ID);
                double bonus = SkillEffects.damageBonus(combat);
                if (bonus > 0.0D) {
                    damage.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(SKILL_DAMAGE_ID, bonus,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
            }
        }
    }

    // ---- traits (rework phase 7) ----

    private static final ResourceLocation TRAIT_DAMAGE_ID = ResourceLocation.fromNamespaceAndPath("ancientwarfarenpc", "trait_damage");
    private static final ResourceLocation TRAIT_HEALTH_ID = ResourceLocation.fromNamespaceAndPath("ancientwarfarenpc", "trait_health");
    /** Created on first use, like the skills. */
    @Nullable
    private NpcTraits traits;
    /** False until the traits were read from a save or rolled: an NPC from an older save rolls its traits when it first loads. */
    private boolean traitsRolled = false;
    private int appliedTraitMask = -1;

    /** True for the NPCs that have traits (the player-owned ones). */
    public boolean supportsTraits() {
        return false;
    }

    /** The traits of this NPC (server side; the clients get them with the talk menu, the census and the tooltip data). */
    public NpcTraits getTraits() {
        if (traits == null) {
            traits = new NpcTraits();
        }
        return traits;
    }

    /** True if this NPC has the trait (always false when traits are off in the config, and for NPCs that have none). */
    public boolean hasTrait(Trait trait) {
        return traits != null && SettlementConfig.traitsEnabled && traits.has(trait);
    }

    /** The traits as a bit mask (see {@link Trait#bit()}), for the census. */
    public int getTraitMask() {
        return traits == null ? 0 : traits.mask();
    }

    /** Sets the traits (cut to at most 2 that do not contradict) and applies their attribute effects. For the families of phase 10 and for commands. */
    public void setTraits(java.util.Collection<Trait> list) {
        getTraits().set(list);
        traitsRolled = true;
        refreshTraitState();
    }

    /** Server side: gives the NPC its traits if it has none yet (a new NPC, or one from an older save). */
    public final void ensureTraits() {
        if (traitsRolled || level().isClientSide || !supportsTraits() || !SettlementConfig.traitsEnabled) {
            return;
        }
        traitsRolled = true;
        traits = NpcTraits.roll(getRandom());
        refreshTraitState();
    }

    /** Server side: brings the attribute modifiers of the traits (health, attack damage) up to date. Cheap; called now and then and after a change. */
    public final void refreshTraitState() {
        if (level().isClientSide || !supportsTraits()) {
            return;
        }
        int key = SettlementConfig.traitsEnabled ? getTraitMask() : 0;
        if (key == appliedTraitMask) {
            return;
        }
        appliedTraitMask = key;
        AttributeInstance damage = getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.removeModifier(TRAIT_DAMAGE_ID);
            double bonus = TraitEffects.damageBonus(this);
            if (bonus > 0.0D) {
                damage.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(TRAIT_DAMAGE_ID, bonus,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
        AttributeInstance health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            boolean wasFull = getHealth() >= getMaxHealth() - 0.01F;
            health.removeModifier(TRAIT_HEALTH_ID);
            double bonus = TraitEffects.healthBonus(this);
            if (bonus > 0.0D) {
                health.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(TRAIT_HEALTH_ID, bonus,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            if (wasFull) {
                setHealth(getMaxHealth());
            } else if (getHealth() > getMaxHealth()) {
                setHealth(getMaxHealth());
            }
        }
    }

    // ---- item (spawner) form ----

    /** implementations should read in any data written during {@link #writeAdditionalItemData(CompoundTag)} */
    public void readAdditionalItemData(CompoundTag tag) {
        ListTag equipmentList = tag.getList("equipment", Tag.TAG_COMPOUND);
        for (int i = 0; i < equipmentList.size(); i++) {
            CompoundTag equipmentTag = equipmentList.getCompound(i);
            if (equipmentTag.contains(SLOT_NUM_TAG)) {
                ItemStack stack = ItemStack.parseOptional(registryAccess(), equipmentTag);
                setItemSlot(equipmentTag.getInt(SLOT_NUM_TAG), stack);
            }
        }
        readBaseTags(tag);
    }

    /**
     * Implementations should write out any persistent entity-data needed to restore entity-state from an item-stack.<br>
     * This should include inventory, levels, orders, faction / etc
     */
    public void writeAdditionalItemData(CompoundTag tag) {
        ListTag equipmentList = new ListTag();
        for (int slot = 0; slot < SLOTS_BY_INDEX.length; slot++) {
            ItemStack stack = getItemBySlot(SLOTS_BY_INDEX[slot]);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag equipmentTag = (CompoundTag) stack.save(registryAccess(), new CompoundTag());
            equipmentTag.putInt(SLOT_NUM_TAG, slot);
            equipmentList.add(equipmentTag);
        }
        tag.put("equipment", equipmentList);
        writeBaseTags(tag);
    }

    /**
     * is the input stack a valid orders-item for this npc?<br>
     * only used by player-owned NPCs
     */
    @SuppressWarnings("squid:S1172")
    public boolean isValidOrdersStack(ItemStack stack) {
        return false;
    }

    /**
     * callback for when orders-stack changes.  implementations should inform any necessary AI tasks of the
     * change to order-items. Runs on both sides, the AI exists on the server only.
     */
    public void onOrdersInventoryChanged() {
    }

    /**
     * callback for when weapon slot has been changed.<br>
     * Implementations should re-set any subtype or inform any AI that need to know when
     * weapon was changed. Runs on both sides, the AI exists on the server only.
     */
    public void onWeaponInventoryChanged() {
    }

    /**
     * callback for when the offhand item slot has been changed.<br>
     * A shield in the off hand with no bow in the main hand adds the shield blocking AI (priority 3). Original bug fixed:
     * the AI was added again at every call and never removed.
     */
    public void onOffhandInventoryChanged() {
        if (!level().isClientSide) {
            ItemStack mainhandStack = getMainHandItem();
            ItemStack offhandStack = getOffhandItem();
            if (shieldBlockAI != null) {
                goalSelector.removeGoal(shieldBlockAI);
            }
            if (offhandStack.canPerformAction(ItemAbilities.SHIELD_BLOCK) && !isBow(mainhandStack.getItem())) {
                shieldBlockAI = initShieldAI();
                goalSelector.addGoal(3, shieldBlockAI);
            } else {
                shieldBlockAI = null;
            }
        }
    }

    /**
     * return the NPCs subtype.<br>
     * this subtype may vary at runtime.
     */
    public abstract String getNpcSubType();

    /**
     * return the NPCs type.  This type should be unique for the class of entity,
     * or at least unique pertaining to the entity registration.
     */
    public abstract String getNpcType();

    /**
     * return the full NPC type for this npc<br>
     * returns npcType if subtype is empty, else npcType.npcSubtype
     */
    public String getNpcFullType() {
        String type = getNpcType();
        if (type == null || type.isEmpty()) {
            throw new IllegalStateException("Type must not be null or empty:");
        }
        String sub = getNpcSubType();
        if (sub == null) {
            throw new IllegalStateException("Subtype must not be null...type: " + type);
        }
        if (!sub.isEmpty()) {
            type = type + "." + sub;
        }
        return type;
    }

    /**
     * A custom name (name tag or the inventory screen) replaces everything; otherwise the template of the type and role with the generated first
     * name ("Guard Captain Rory"). The role is read each time, so it follows a role change.
     */
    @Override
    public Component getName() {
        Component customName = getCustomName();
        if (customName != null) {
            return customName;
        }
        if (isChild()) {
            return NpcNames.child(getGivenName()); // "Child Tom" (rework phase 10)
        }
        return NpcNames.display(getNpcFullType(), getGivenName(), entityData.get(MAIN_RANK));
    }

    /** True for a child that has not grown up yet (rework phase 10). Only the worker has a child state; every other NPC is always grown up. Synced to the clients. */
    public boolean isChild() {
        return false;
    }

    /** The age of a child in ticks (rework phase 10; 0 for an NPC that is not a child). Server side. */
    public long getChildAgeTicks() {
        return 0L;
    }

    public final NpcLevelingStats getLevelingStats() {
        return levelingStats;
    }

    private ItemStack getItemToSpawn() {
        return NpcBridge.createSpawnerStack(this);
    }

    public final long getIDForSkin() {
        return getUUID().getLeastSignificantBits();
    }

    @Nullable
    @Override
    public ItemStack getPickResult() {
        ItemStack stack = getItemToSpawn();
        return stack.isEmpty() ? null : stack;
    }

    /** The UUID is part of the vanilla spawn packet, so only the owner and the skin settings are sent. */
    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        owner.serializeToBuffer(buffer);
        skinSettings.serializeToBuffer(buffer);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        owner = new Owner(buffer);
        skinSettings = NpcSkinSettings.deserializeFromBuffer(buffer);
    }

    @Override
    public void tick() {
        if (!level().isClientSide) {
            if (tickCount % 10 == 1) {
                ensureGivenName();
                ensureSurname();
            }
            if (getShieldDisabledTick() > 0) {
                decrementShieldDisabledTick();
            }
            if (tickCount % 200 == 0 && getHealth() < getMaxHealth() && isAlive() && !isHungry()) {
                setHealth(getHealth() + 1);
            }
        }
        super.tick();
        ItemStack mainhand = getMainHandItem();
        if (!mainhand.isEmpty()) {
            mainhand.inventoryTick(level(), this, 0, true);
        }
    }

    /** 1.12.2: no flying targets (the Ghast and, new in 1.21.1, the Phantom are the {@code FlyingMob}s). */
    @Override
    public boolean canAttackType(EntityType<?> type) {
        return type != EntityType.GHAST && type != EntityType.PHANTOM && super.canAttackType(type);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    final void updateDamageFromLevel() {
        attribute(Attributes.ATTACK_DAMAGE).setBaseValue(getLeveledAttack());
    }

    private double getLeveledAttack() {
        double dmg = getBaseAttack();
        int level = getLevelingStats().getLevel();
        return dmg * (1 + level * npcLevelDamageMultiplier);
    }

    private double getBaseAttack() {
        NpcDefault npcDefault = getNpcDefault();
        return npcDefault == null ? 1.0D : npcDefault.getBaseAttack();
    }

    /**
     * The data defaults (stats, equipment, path settings) of this kind of NPC: {@code owned_npc_defaults.json} for the player-owned NPCs,
     * {@code faction_npc_defaults.json} for a faction and type for the faction NPCs. Null if there is no entry (an unknown faction).
     * Replaces the {@code instanceof} checks of 1.12.2 ({@code NpcDefaultsRegistry.getFactionNpcDefault(NpcFaction)}).
     */
    @Nullable
    public abstract NpcDefault getNpcDefault();

    public int getFoodRemaining() {
        return 0;//NOOP in non-player owned
    }

    public void setFoodRemaining(int food) {
        //NOOP in non-player owned
    }

    /** Hungry (under the hunger threshold, starving included): no natural healing, slower work and movement. Always false without upkeep. */
    public boolean isHungry() {
        return false;
    }

    /** At 0 food. */
    public boolean isStarving() {
        return false;
    }

    /** The work speed multiplier from hunger: 1 when fed. */
    public double getWorkFactor() {
        return 1.0D;
    }

    /** The work speed multiplier from the tool: 1 for a matching tool (or a type without roles), less without one (workers). */
    public double getToolWorkFactor() {
        return 1.0D;
    }

    private double workProgress = 0.0D;
    private double workBonus = 0.0D;

    /** True when the work is faster than normal and one more tick of work is due (call after {@link #advanceWorkTick()} returned true). */
    public boolean takeBonusWorkTick() {
        if (workBonus >= 1.0D) {
            workBonus -= 1.0D;
            return true;
        }
        return false;
    }

    /** Called once per tick of work: true when the work counter may advance, which is less often while hungry. */
    public boolean advanceWorkTick() {
        double factor = getWorkFactor() * getToolWorkFactor();
        if (factor >= 1.0D) {
            // faster than normal (high morale): the surplus of each tick adds up to a bonus tick now and then, see takeBonusWorkTick
            workBonus = Math.min(2.0D, workBonus + factor - 1.0D);
            return true;
        }
        workBonus = 0.0D;
        workProgress += factor;
        if (workProgress >= 1.0D) {
            workProgress -= 1.0D;
            return true;
        }
        return false;
    }

    public boolean requiresUpkeep() {
        return this instanceof IKeepFood;//NOOP in non-player owned
    }

    // ---- ownership ----

    @Override
    public void setOwner(Player player) {
        owner = new Owner(player);
    }

    @Override
    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    /**
     * Sets the owner by name. Original bug fixed: the 1.12.2 code only told the clients when the new owner's name differed from
     * the name it had just been built with, which never happens; the clients are told now (nobody is tracking yet before the spawn).
     */
    public void setOwnerName(String name) {
        owner = new Owner(level(), name);
        if (!level().isClientSide) {
            NpcEntityPayloads.sendToTracking(this, owner.serializeToNBT(new CompoundTag()));
        }
    }

    @Override
    public boolean isOwner(Player player) {
        return owner.isOwnerOrSameTeamOrFriend(player);
    }

    @Override
    public Owner getOwner() {
        return owner;
    }

    @Nullable
    @Override
    public PlayerTeam getTeam() {
        return level().getScoreboard().getPlayersTeam(owner.getName());
    }

    public boolean hasCommandPermissions(Owner owner) {
        return hasCommandPermissions(owner.getUUID(), owner.getName());
    }

    public boolean hasCommandPermissions(UUID playerId, String playerName) {
        return owner.playerHasCommandPermissions(level(), playerId, playerName);
    }

    /** 1.12.2: {@code getExperiencePoints(attacker)}: only the players this NPC is hostile towards (and can be attacked by) get the experience. */
    @Override
    protected void dropExperience(@Nullable Entity killer) {
        Player attacker = lastHurtByPlayer;
        if (attacker == null || (isHostileTowards(attacker) && canBeAttackedBy(attacker))) {
            super.dropExperience(killer);
        }
    }

    public abstract boolean isHostileTowards(Entity e);

    /**
     * The Spectre Factions actor this NPC acts for: its owner's actor (faction or solo player) for a player-owned NPC, its NPC faction
     * for a faction NPC. Empty if there is none.
     */
    public Optional<ActorId> getFactionActor() {
        return Optional.empty();
    }

    /** The guard mode of the NPC's actor (server side). Without an actor the NPC is {@link GuardMode#AGGRESSIVE}. */
    public GuardMode getGuardMode() {
        if (level().isClientSide()) {
            return GuardMode.AGGRESSIVE;
        }
        return getFactionActor().map(FactionsApi::getGuardMode).orElse(GuardMode.AGGRESSIVE);
    }

    /** The GUARD mode radius: the guard radius of the config around the home position, or around the NPC if it has no home. */
    public boolean isWithinGuardArea(Entity e) {
        double radius = FactionsConfig.guardRadius;
        if (hasHome()) {
            BlockPos home = getHomePosition();
            return e.distanceToSqr(home.getX() + 0.5d, home.getY(), home.getZ() + 0.5d) <= radius * radius;
        }
        return distanceToSqr(e) <= radius * radius;
    }

    /**
     * Hostility from the factions relation of this NPC's actor and the actor of the entity: false for the same actor, else true only for
     * HOSTILE. Null if one of the two has no actor (the caller then uses the target lists).
     */
    @Nullable
    protected Boolean isHostileByRelation(Entity e) {
        Optional<ActorId> me = getFactionActor();
        if (me.isEmpty()) {
            return null;
        }
        Optional<ActorId> other = AwFactions.actorOf(level(), e);
        if (other.isEmpty()) {
            return null;
        }
        if (me.get().equals(other.get())) {
            return false;
        }
        return AwFactions.relation(level(), me.get(), other.get()) == Relation.HOSTILE;
    }

    /** The relation of this NPC's actor to the entity's actor; empty if either has none. */
    public Optional<Relation> getRelationTo(Entity e) {
        Optional<ActorId> me = getFactionActor();
        Optional<ActorId> other = me.isEmpty() ? Optional.empty() : AwFactions.actorOf(level(), e);
        if (me.isEmpty() || other.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(AwFactions.relation(level(), me.get(), other.get()));
    }

    /** True if the other entity acts for the same actor (same faction, or the same solo player and their NPCs). */
    public boolean isSameActor(Entity e) {
        Optional<ActorId> me = getFactionActor();
        return me.isPresent() && me.equals(AwFactions.actorOf(level(), e));
    }

    /** An ordered attack on an entity of a FRIENDLY or ALLIED actor is only allowed if the config says so. */
    protected boolean mayAttackByRelation(Entity e) {
        if (isSameActor(e)) {
            return false;
        }
        Optional<Relation> relation = getRelationTo(e);
        return relation.isEmpty() || !relation.get().isFriendlyOrBetter() || FactionsConfig.allowOrderedAttackOnFriends;
    }

    public abstract boolean canTarget(Entity e);

    public abstract boolean canBeAttackedBy(Entity e);

    @Nullable
    public final LivingEntity getFollowingEntity() {
        if (followingPlayerName == null) {
            return null;
        }
        for (Player player : level().players()) {
            if (player.getGameProfile().getName().equals(followingPlayerName)) {
                return player;
            }
        }
        return null;
    }

    public final void setFollowingEntity(LivingEntity entity) {
        if (entity instanceof Player player && hasCommandPermissions(player.getUUID(), player.getGameProfile().getName())) {
            followingPlayerName = player.getGameProfile().getName();
        }
    }

    public final void clearFollowingEntity() {
        followingPlayerName = null;
    }

    public void setDoNotPursue(boolean val) {
        doNotPursue = val;
    }

    public boolean getDoNotPursue() {
        return doNotPursue;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    public final void repackEntity(Player player) {
        // we already hide the button but we need to prevent repack server-side too
        if (AWNPCStatics.repackCreativeOnly && !player.isCreative()) {
            return;
        }
        if (!player.level().isClientSide && isAlive()) {
            onRepack();
            ItemStack item = InventoryTools.mergeItemStack(new PlayerMainInvWrapper(player.getInventory()), getItemToSpawn());
            if (!item.isEmpty()) {
                Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), item);
            }
        }
        if (!level().isClientSide) {
            discard();
        }
    }

    /**
     * called when NPC is being repacked into item-form.  Called prior to item being created and prior to entity being removed.<br>
     * Main function is for faction-mounted NPCs to disappear their mounts when repacked.
     */
    protected void onRepack() {

    }

    // ---- ship crew (rowers, gunners, passengers and helmsmen of the Vehicles module's ships) ----

    /** The seat roles this NPC takes on a ship, best first. Owned workers and the like row or sit, soldiers man the guns first; NPCs that do not crew return none. */
    public List<SeatRole> getShipSeatPreference() {
        return List.of(SeatRole.ROWER, SeatRole.PASSENGER);
    }

    /** True if this NPC works a turret when it sits in a gunner seat. */
    public boolean isShipGunner() {
        return getShipSeatPreference().contains(SeatRole.GUNNER);
    }

    /** The ship this NPC crews: it rides it in a seat that is not the helm. Null if it rides nothing, or something else. */
    @Nullable
    public VehicleBase getRidingShip() {
        if (getVehicle() instanceof VehicleBase vehicle && vehicle.isShip()) {
            int seat = vehicle.seatOf(this);
            if (seat >= 0 && seat < vehicle.getSeatCount() && vehicle.getSeat(seat).role() != SeatRole.DRIVER) {
                return vehicle;
            }
        }
        return null;
    }

    /** The ship this NPC steers: it sits in its helm (DRIVER seat). Null otherwise. */
    @Nullable
    public VehicleBase getHelmShip() {
        if (getVehicle() instanceof VehicleBase vehicle && vehicle.isShip()) {
            int seat = vehicle.seatOf(this);
            if (seat >= 0 && seat < vehicle.getSeatCount() && vehicle.getSeat(seat).role() == SeatRole.DRIVER) {
                return vehicle;
            }
        }
        return null;
    }

    public boolean isShipCrew() {
        return getRidingShip() != null;
    }

    /** The ship this NPC was ordered off (or left because its allied helm went): it does not board it again by itself while the owner or ally stays aboard. Saved. */
    @Nullable
    private UUID refusedShip;

    @Nullable
    public UUID getRefusedShip() {
        return refusedShip;
    }

    public void setRefusedShip(@Nullable UUID ship) {
        refusedShip = ship;
    }

    // ---- save data ----

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        for (EquipmentSlot slot : SLOTS_BY_INDEX) {
            setItemSlot(slot, ItemStack.EMPTY);
        }
        super.readAdditionalSaveData(tag);
        if (tag.contains("home")) {
            setHomePosAndDistance(BlockPos.of(tag.getLong("home")), tag.getInt("homeRange"));
        }
        if (tag.contains(CACHED_BED_POS_TAG)) {
            cachedBedPos = BlockPos.of(tag.getLong(CACHED_BED_POS_TAG));
        }
        if (tag.contains(FOUND_BED_TAG)) {
            foundBed = tag.getBoolean(FOUND_BED_TAG);
        }

        readBaseTags(tag);
        if (tag.contains("mind", Tag.TAG_LIST)) {
            getMind().load(tag.getList("mind", Tag.TAG_COMPOUND), registryAccess());
        }
        onWeaponInventoryChanged();
        onOffhandInventoryChanged();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (mind != null && !mind.thoughts().isEmpty()) {
            tag.put("mind", mind.save(registryAccess()));
        }
        if (!hasHome()) {
            Optional<BlockPos> position = getTownHallPosition();
            if (position.isPresent()) {
                setHomePosAndDistance(position.get(), getHomeRange());
            } else {
                setHomeAreaAtCurrentPosition();
            }
        }
        tag.putLong("home", getHomePosition().asLong());
        tag.putInt("homeRange", getHomeRange());
        if (cachedBedPos != null) {
            tag.putLong(CACHED_BED_POS_TAG, cachedBedPos.asLong());
        }
        tag.putBoolean(FOUND_BED_TAG, foundBed);

        writeBaseTags(tag);
    }

    private static final String REFUSED_SHIP_TAG = "refusedShip";

    private void readBaseTags(CompoundTag tag) {
        if (tag.contains(ORDERS_STACK_TAG)) {
            setItemSlot(ORDER_SLOT, ItemStack.parseOptional(registryAccess(), tag.getCompound(ORDERS_STACK_TAG)));
        }
        if (tag.contains(UPKEEP_STACK_TAG)) {
            setItemSlot(UPKEEP_SLOT, ItemStack.parseOptional(registryAccess(), tag.getCompound(UPKEEP_STACK_TAG)));
        }
        if (tag.contains(LEVELING_STATS_TAG)) {
            getLevelingStats().readFromNBT(tag.getCompound(LEVELING_STATS_TAG));
        }
        if (tag.contains("skills", Tag.TAG_COMPOUND)) {
            getSkills().load(tag.getCompound("skills")); // an older save has none: no experience
        }
        if (tag.contains("traits", Tag.TAG_LIST)) {
            getTraits().load(tag.getList("traits", Tag.TAG_STRING));
            traitsRolled = true;
        } // an older save has none: the NPC rolls its traits at the first tick (ensureTraits)
        if (tag.contains(MAX_HEALTH_TAG)) {
            attribute(Attributes.MAX_HEALTH).setBaseValue(tag.getFloat(MAX_HEALTH_TAG));
        }
        if (tag.contains(HEALTH_TAG)) {
            setHealth(tag.getFloat(HEALTH_TAG));
        }
        if (tag.contains("name")) {
            setCustomName(Component.literal(tag.getString("name")));
        }
        if (tag.contains("givenName")) {
            setGivenName(tag.getString("givenName"));
        }
        if (tag.contains("surname")) {
            setSurname(tag.getString("surname")); // an older save has none: the NPC rolls one at its first tick (ensureSurname)
        }
        if (tag.contains("food")) {
            setFoodRemaining(tag.getInt("food"));
        }
        if (tag.contains(ATTACK_DAMAGE_OVERRIDE_TAG)) {
            setAttackDamageOverride(tag.getInt(ATTACK_DAMAGE_OVERRIDE_TAG));
        }
        if (tag.contains(ARMOR_VALUE_OVERRIDE_TAG)) {
            setArmorValueOverride(tag.getInt(ARMOR_VALUE_OVERRIDE_TAG));
        }
        if (tag.contains(AI_ENABLED_TAG)) {
            setIsAIEnabled(tag.getBoolean(AI_ENABLED_TAG));
        }
        if (tag.contains(DO_NOT_PURSUE)) {
            setDoNotPursue(tag.getBoolean(DO_NOT_PURSUE));
        }
        if (tag.contains(HAS_CUSTOM_EQUIPMENT_TAG)) {
            setCustomEquipmentOverride(tag.getBoolean(HAS_CUSTOM_EQUIPMENT_TAG));
        }
        setSkinSettings(NpcSkinSettings.deserializeNBT(tag.getCompound("skinSettings")).minimizeData());
        owner = Owner.deserializeFromNBT(tag);
        refusedShip = tag.hasUUID(REFUSED_SHIP_TAG) ? tag.getUUID(REFUSED_SHIP_TAG) : null;
    }

    private void writeBaseTags(CompoundTag tag) {
        if (!ordersStack.isEmpty()) {
            tag.put(ORDERS_STACK_TAG, ordersStack.save(registryAccess()));
        }
        if (!upkeepStack.isEmpty()) {
            tag.put(UPKEEP_STACK_TAG, upkeepStack.save(registryAccess()));
        }
        tag.put(LEVELING_STATS_TAG, getLevelingStats().writeToNBT(new CompoundTag()));
        if (skills != null) {
            tag.put("skills", skills.save());
        }
        if (traitsRolled && traits != null) {
            tag.put("traits", traits.save());
        }
        tag.putFloat(MAX_HEALTH_TAG, (float) attribute(Attributes.MAX_HEALTH).getBaseValue()); // without the modifier of the tough trait
        tag.putFloat(HEALTH_TAG, getHealth());
        if (hasCustomName()) {
            tag.putString("name", getCustomName().getString());
        }
        if (!getGivenName().isEmpty()) {
            tag.putString("givenName", getGivenName());
        }
        if (!surname.isEmpty()) {
            tag.putString("surname", surname);
        }
        tag.putInt("food", getFoodRemaining());
        tag.putInt(ATTACK_DAMAGE_OVERRIDE_TAG, attackDamage);
        tag.putInt(ARMOR_VALUE_OVERRIDE_TAG, armorValue);
        tag.putBoolean(AI_ENABLED_TAG, aiEnabled);
        tag.putBoolean(DO_NOT_PURSUE, doNotPursue);
        tag.putBoolean(HAS_CUSTOM_EQUIPMENT_TAG, hasCustomEquipment);
        tag.put("skinSettings", skinSettings.serializeNBT());
        owner.serializeToNBT(tag);
        if (refusedShip != null) {
            tag.putUUID(REFUSED_SHIP_TAG, refusedShip);
        }
    }

    public final ResourceLocation getTexture() {
        return skinSettings.getTexture(getNpcFullType(), getIDForSkin());
    }

    /** Client side: updates sent with {@link NpcEntityPayloads#sendToTracking} (owner, skin settings, attack target). */
    @Override
    public void handlePacketData(CompoundTag tag) {
        if (tag.contains(OWNER_NAME_TAG)) {
            owner = Owner.deserializeFromNBT(tag);
        } else if (tag.contains(NpcSkinSettings.TAG_NAME)) {
            skinSettings = NpcSkinSettings.deserializeNBT(tag.getCompound(NpcSkinSettings.TAG_NAME));
        } else if (tag.contains(ATTACK_TARGET_TAG)) {
            Entity target = level().getEntity(tag.getInt(ATTACK_TARGET_TAG));
            setTarget(target instanceof LivingEntity living ? living : null);
        }
    }

    // ---- beds ----

    /** The nearest free bed (its foot part) within 6 blocks, cached, or null. */
    @Nullable
    public BlockPos findBed() {
        if (!foundBed) {
            BlockPos origin = blockPosition();
            int originX = origin.getX();
            int originY = origin.getY();
            int originZ = origin.getZ();
            int maxSearchRange = 6;
            List<BlockPos> foundBeds = new ArrayList<>();
            for (int x = originX - maxSearchRange; x <= originX + maxSearchRange; x++) {
                for (int y = originY - maxSearchRange; y <= originY + maxSearchRange; y++) {
                    for (int z = originZ - maxSearchRange; z <= originZ + maxSearchRange; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = level().getBlockState(pos);
                        if (state.getBlock() instanceof BedBlock) {
                            if (state.getValue(BedBlock.PART) != BedPart.FOOT) {
                                continue;
                            }
                            if (!state.getValue(BedBlock.OCCUPIED)) { // occupied check
                                foundBeds.add(pos);
                            }
                        }
                    }
                }
            }

            int closetBedIndex = -1;
            for (int i = 0; i < foundBeds.size(); i++) {
                double bedDistance = foundBeds.get(i).distToCenterSqr(originX, originY, originZ);
                if ((closetBedIndex == -1) || (bedDistance < foundBeds.get(closetBedIndex).distToCenterSqr(originX, originY, originZ))) {
                    closetBedIndex = i;
                }
            }
            if (closetBedIndex == -1) {
                foundBed = false;
                return null;
            }
            foundBed = true;
            cachedBedPos = foundBeds.get(closetBedIndex);
        }

        if (cachedBedPos != null && level().getBlockState(cachedBedPos).getBlock() instanceof BedBlock) {
            return cachedBedPos;
        } else {
            foundBed = false;
            return null; // try again in a while
        }
    }

    /** Uses this bed (foot part) as the bed to lie down in: the settlement assigned it. */
    public void assignBed(BlockPos pos) {
        cachedBedPos = pos;
        foundBed = true;
    }

    /** Goes to sleep in the bed (vanilla sleeping: the bed is set occupied, the NPC lies down with the 0.2 x 0.2 hit box). */
    public boolean lieDown(BlockPos pos) {
        if (!foundBed) {
            return false;
        }
        if (level().hasChunkAt(pos) && level().getBlockState(pos).getBlock() instanceof BedBlock) {
            BlockState state = level().getBlockState(pos);
            if (state.getValue(BedBlock.OCCUPIED)) {
                // occupied check
                foundBed = false;
                return false;
            }
            startSleeping(pos);
            setPositionToBed();
            onFellAsleep();
            return true;
        }
        return false;
    }

    /**
     * Gets up: the bed is set vacant and the NPC is put on a free spot beside it (vanilla {@code stopSleeping}; the 1.12.2 code tried
     * the two sides of the bed and then the foot end by hand).
     */
    public void wakeUp() {
        if (isSleeping()) {
            stopSleeping();
            if (!level().isClientSide) {
                onWokeUp();
            }
        }
    }

    public void setPositionToBed() {
        if (cachedBedPos != null) {
            setPos(cachedBedPos.getX() + 0.5F, cachedBedPos.getY() + 0.6F, cachedBedPos.getZ() + 0.5F);
        }
    }

    public boolean isBedCacheValid() {
        return cachedBedPos != null && level().getBlockState(cachedBedPos).getBlock() instanceof BedBlock;
    }

    @Override
    protected void doPush(Entity entity) {
        if (!isSleeping()) {
            super.doPush(entity);
        }
    }

    @Override
    public boolean isPushable() {
        return !isSleeping() && super.isPushable();
    }

    /** The facing of the bed the NPC sleeps in (north if it does not sleep). */
    public Direction getBedDirection() {
        return getSleepingPos()
                .map(pos -> level().getBlockState(pos))
                .filter(state -> state.getBlock() instanceof BedBlock)
                .map(state -> state.getValue(BedBlock.FACING))
                .orElse(Direction.NORTH);
    }

    public boolean shouldSleep() {
        return !WorldTools.isDaytimeInDimension(level());
    }

    // ---- synced values ----

    public boolean isSwingingArms() {
        return entityData.get(SWINGING_ARMS);
    }

    public void setSwingingArms(boolean swingingArms) {
        entityData.set(SWINGING_ARMS, swingingArms);
    }

    public int getShieldDisabledTick() {
        return entityData.get(SHIELD_DISABLED_TICK);
    }

    public void setShieldDisabledTick(int count) {
        entityData.set(SHIELD_DISABLED_TICK, count);
    }

    public void decrementShieldDisabledTick() {
        entityData.set(SHIELD_DISABLED_TICK, entityData.get(SHIELD_DISABLED_TICK) - 1);
    }

    // ---- vehicles (IPathableEntity, IVehicleCrew) ----
    //TODO(original) refactor vehicle stuff out - perhaps capability??

    @Nullable
    private VehicleBase getRidingVehicle() {
        if (getVehicle() instanceof VehicleBase vehicle) {
            return vehicle;
        }
        return null;
    }

    @Override
    public void setPath(List<Node> path) {
        VehicleBase vehicle = getRidingVehicle();
        if (vehicle != null) {
            vehicle.nav.forcePath(path);
        } else {
            //TODO(original) implement forcePath for NPC
            //nav.forcePath(path);
        }
    }

    @Override
    public void setMoveTo(double x, double y, double z, float moveSpeed) {
        getMoveControl().setWantedPosition(x, y, z, moveSpeed);
    }

    @Override
    public float getDefaultMoveSpeed() {
        return 1f;
    }

    @Override
    public boolean isPathableEntityOnLadder() {
        return onClimbable();
    }

    @Override
    public Entity getEntity() {
        return this;
    }

    @Override
    public void onStuckDetected() {
        //noop
    }

    @Override
    public boolean isFactionSoldier() {
        return false;
    }

    @Override
    public boolean isOwnedSiegeEngineer() {
        return false;
    }

    @Override
    public boolean isFactionSiegeEngineer() {
        return false;
    }

    @Override
    public int getSoldierLevel() {
        return getLevelingStats().getLevel();
    }

    @Override
    public void onVehicleStuck() {
        onStuckDetected();
    }

    public boolean isFemale() {
        return false;
    }

    public NpcSkinSettings getSkinSettings() {
        return skinSettings;
    }

    /**
     * Sets the skin settings and, if the NPC is already in the level, tells the players tracking it (1.12.2: {@code onNpcSet} sent the
     * settings and the resolved game profile of a player skin in an entity packet; the client resolves the player skin by name now, see
     * {@link NpcSkinSettings.PlayerSkinResolver}).
     */
    public void setSkinSettings(NpcSkinSettings skinSettings) {
        this.skinSettings = skinSettings;
        if (!level().isClientSide && !firstTick) {
            CompoundTag tag = new CompoundTag();
            tag.put(NpcSkinSettings.TAG_NAME, skinSettings.serializeNBT());
            NpcEntityPayloads.sendToTracking(this, tag);
        }
    }

    public boolean isBow(Item item) {
        return item instanceof BowItem;
    }

    private NpcAIBlockWithShield initShieldAI() {
        if (shieldBlockAI == null) {
            shieldBlockAI = new NpcAIBlockWithShield(this);
        }
        return shieldBlockAI;
    }
}
