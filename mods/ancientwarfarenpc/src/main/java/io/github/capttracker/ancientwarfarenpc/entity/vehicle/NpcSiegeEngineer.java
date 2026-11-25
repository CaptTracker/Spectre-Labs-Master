/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/vehicle/NpcSiegeEngineer.
 */
package io.github.capttracker.ancientwarfarenpc.entity.vehicle;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAIAttackNearest;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIDefendFriends;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIDoor;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIFollowPlayer;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIHurt;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIWander;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIWatchClosest;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIOwnerHurtByTarget;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIOwnerHurtTarget;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedAlarmResponse;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedCommander;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedFollowCommand;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedGetFood;
import io.github.capttracker.ancientwarfarenpc.ai.vehicle.NpcAIAimVehicle;
import io.github.capttracker.ancientwarfarenpc.ai.vehicle.NpcAIDismountVehicle;
import io.github.capttracker.ancientwarfarenpc.ai.vehicle.NpcAIFindVehicle;
import io.github.capttracker.ancientwarfarenpc.ai.vehicle.NpcAIFireVehicle;
import io.github.capttracker.ancientwarfarenpc.ai.vehicle.NpcAIMountVehicle;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.npccommand.NpcCommand;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Player-owned crew of the AW vehicles: finds a free vehicle, mounts it, aims and fires at the attack target or at a block given
 * with the baton's attack area command. It keeps riding only while it is fed.
 *
 * <p>The original set the follow range to 120 in {@code applyEntityAttributes}, but the owned NPC defaults (follow range 60)
 * are applied after that in the constructor, so the effective value was 60. The port has the same effective value (no override).
 */
@SuppressWarnings({"squid:MaximumInheritanceDepth", "squid:S2160"})
public class NpcSiegeEngineer extends NpcPlayerOwned implements IVehicleUser {
    @Nullable
    private VehicleBase vehicle;
    private ITarget vehicleTarget = TargetFactory.NONE;

    public NpcSiegeEngineer(EntityType<? extends NpcSiegeEngineer> type, Level level) {
        super(type, level);
    }

    @Override
    public Optional<ITarget> getVehicleTarget() {
        return vehicleTarget == TargetFactory.NONE ? Optional.empty() : Optional.of(vehicleTarget);
    }

    private void setVehicleTarget(BlockPos pos) {
        vehicleTarget = new BlockPosTarget(pos);
    }

    @Override
    public void resetTarget() {
        vehicleTarget = TargetFactory.NONE;
    }

    @Override
    public void setTarget(@Nullable LivingEntity entity) {
        super.setTarget(entity);
        if (entity != null) {
            vehicleTarget = new EntityTarget(entity);
        } else {
            resetTarget();
        }
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new NpcAIDoor(this, true));
        goalSelector.addGoal(2, new NpcAIFollowPlayer(this));
        goalSelector.addGoal(2, new NpcAIPlayerOwnedFollowCommand(this));
        goalSelector.addGoal(3, new NpcAIPlayerOwnedAlarmResponse(this));
        goalSelector.addGoal(4, new NpcAIPlayerOwnedGetFood(this));
        goalSelector.addGoal(6, new NpcAIDismountVehicle<>(this));
        goalSelector.addGoal(6, new NpcAIFindVehicle<>(this));
        goalSelector.addGoal(7, new NpcAIMountVehicle<>(this));
        goalSelector.addGoal(8, new NpcAIAimVehicle<>(this));
        goalSelector.addGoal(9, new NpcAIFireVehicle<>(this));

        goalSelector.addGoal(101, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        goalSelector.addGoal(102, new NpcAIWander(this) {
            @Override
            public boolean canUse() {
                return !isRidingVehicle() && super.canUse();
            }
        });
        goalSelector.addGoal(103, new NpcAIWatchClosest(this, Mob.class, 8.0F));

        targetSelector.addGoal(0, new NpcAIPlayerOwnedCommander(this));
        targetSelector.addGoal(1, new NpcAIOwnerHurtByTarget(this));
        targetSelector.addGoal(2, new NpcAIOwnerHurtTarget(this));
        targetSelector.addGoal(3, new NpcAIHurt(this));
        targetSelector.addGoal(4, new NpcAIAttackNearest(this, this::isHostileTowards));
        targetSelector.addGoal(5, new NpcAIDefendFriends(this));
    }

    @Override
    public String getNpcSubType() {
        return "";
    }

    @Override
    public String getNpcType() {
        return "siege_engineer";
    }

    @Override
    public boolean canBeAttackedBy(Entity e) {
        return true;
    }

    @Override
    public Optional<VehicleBase> getAssignedVehicle() {
        if (vehicle == null || vehicle.isRemoved()) {
            return Optional.empty();
        }
        return Optional.of(vehicle);
    }

    @Override
    public void setAssignedVehicle(VehicleBase vehicle) {
        this.vehicle = vehicle;
    }

    @Override
    public void resetAssignedVehicle() {
        vehicle = null;
    }

    @Override
    public boolean isRidingVehicle() {
        return getAssignedVehicle().isPresent() && isPassenger();
    }

    @Override
    public boolean canContinueRidingVehicle() {
        return true;
    }

    @Override
    public boolean isOwnedSiegeEngineer() {
        return true;
    }

    @Override
    public java.util.List<io.github.capttracker.ancientwarfarevehicle.entity.SeatRole> getShipSeatPreference() {
        return java.util.List.of(io.github.capttracker.ancientwarfarevehicle.entity.SeatRole.GUNNER, io.github.capttracker.ancientwarfarevehicle.entity.SeatRole.ROWER,
                io.github.capttracker.ancientwarfarevehicle.entity.SeatRole.PASSENGER);
    }

    @Override
    public void handlePlayerCommand(NpcCommand.Command cmd) {
        if (cmd.type == NpcCommand.CommandType.ATTACK_AREA) {
            setVehicleTarget(cmd.pos);
        } else if (cmd.type == NpcCommand.CommandType.CLEAR_COMMAND) {
            resetTarget();
        } else {
            super.handlePlayerCommand(cmd);
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide && tickCount % 20 == 0) {
            checkTargetExistence();
        }
    }

    private void checkTargetExistence() {
        if (vehicleTarget != TargetFactory.NONE && !vehicleTarget.exists(level())) {
            vehicleTarget = TargetFactory.NONE;
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        TargetFactory.serializeNBT(vehicleTarget, tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        vehicleTarget = TargetFactory.deserializeFromNBT(tag);
    }
}
