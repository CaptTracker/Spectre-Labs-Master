/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/faction/NpcFactionMounted.
 */
package io.github.capttracker.ancientwarfarenpc.entity.faction;

import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfarenpc.ai.faction.NpcAIFactionRideHorse;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * Base of the faction NPCs that ride a mount (the cavalry and the mounted archers). {@code NpcAIFactionRideHorse} puts the NPC on a new
 * mount and remembers whether it is alive; the mount goes away with the NPC when it despawns or is packed into a spawner.
 *
 * <p>Changes from 1.12.2: the mount is an entity type id of the faction defaults ({@code horse_entity}), not a Java class name.
 */
public abstract class NpcFactionMounted extends NpcFaction implements IHorseMountedNpc {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Assigned in registerGoals (server only), see NpcBase: no initialiser, and null on the client.
    @Nullable
    private NpcAIFactionRideHorse<NpcFactionMounted> horseAI;
    private boolean horseLives = true;

    protected NpcFactionMounted(EntityType<? extends NpcFactionMounted> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        horseAI = new NpcAIFactionRideHorse<>(this);
        goalSelector.addGoal(0, horseAI);
    }

    @Override
    public boolean isHorseAlive() {
        return horseLives;
    }

    @Override
    public void setHorseKilled() {
        horseLives = false;
    }

    /** The mount is removed with the NPC when the NPC despawns (1.12.2: {@code despawnEntity}). The mount is taken before the NPC is removed, which dismounts it. */
    @Override
    public void checkDespawn() {
        Entity mount = getVehicle();
        super.checkDespawn();
        if (isRemoved() && mount instanceof Mob && !mount.isRemoved()) {
            mount.discard();
        }
    }

    @Override
    protected void onRepack() {
        super.onRepack();
        Entity mount = getVehicle();
        if (mount instanceof Mob) {
            stopRiding();
            mount.discard();
        }
    }

    @Override
    public boolean worksInRain() {
        return true;
    }

    @Override
    public boolean isPassive() {
        return false;
    }

    @Override
    public boolean shouldSleep() {
        return false;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        horseLives = tag.getBoolean("horseLives");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("horseLives", horseLives);
    }

    @Override
    public Mob instantiateMountedEntity() {
        ResourceLocation id = getAdditionalAttributes().getHorseEntity();
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        Entity entity = type.create(level());
        if (entity instanceof Mob mob) {
            return mob;
        }
        LOGGER.error("Error instantiating mount entity of type {}, using a horse", id);
        return EntityType.HORSE.create(level());
    }

    @Override
    public void die(DamageSource damageSource) {
        if (horseAI != null) {
            horseAI.onKilled();
        }
        super.die(damageSource);
    }
}
