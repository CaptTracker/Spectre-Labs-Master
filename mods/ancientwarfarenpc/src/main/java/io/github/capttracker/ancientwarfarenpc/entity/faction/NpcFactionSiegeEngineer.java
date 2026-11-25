/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/faction/NpcFactionSiegeEngineer.
 */
package io.github.capttracker.ancientwarfarenpc.entity.faction;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAIAttackNearest;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIDefendFriends;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIDoor;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIFollowPlayer;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIMoveHome;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIWander;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIWatchClosest;
import io.github.capttracker.ancientwarfarenpc.ai.faction.NpcAIFactionHurt;
import io.github.capttracker.ancientwarfarenpc.ai.vehicle.NpcAIAimVehicle;
import io.github.capttracker.ancientwarfarenpc.ai.vehicle.NpcAIFindVehicle;
import io.github.capttracker.ancientwarfarenpc.ai.vehicle.NpcAIFireVehicle;
import io.github.capttracker.ancientwarfarenpc.ai.vehicle.NpcAIMountVehicle;
import io.github.capttracker.ancientwarfarenpc.entity.vehicle.EntityTarget;
import io.github.capttracker.ancientwarfarenpc.entity.vehicle.ITarget;
import io.github.capttracker.ancientwarfarenpc.entity.vehicle.IVehicleUser;
import io.github.capttracker.ancientwarfarenpc.entity.vehicle.TargetFactory;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Optional;

/** The siege engineer of a faction: finds a free vehicle, mounts it, aims and fires at its target. Unlike the player-owned one it rides whether fed or not. */
public class NpcFactionSiegeEngineer extends NpcFaction implements IVehicleUser {
    @Nullable
    private VehicleBase vehicle = null;
    private ITarget vehicleTarget = TargetFactory.NONE;

    public NpcFactionSiegeEngineer(EntityType<? extends NpcFactionSiegeEngineer> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new NpcAIDoor(this, true));
        goalSelector.addGoal(1, new NpcAIFollowPlayer(this));
        goalSelector.addGoal(2, new NpcAIMoveHome(this, 50F, 5F, 30F, 5F));
        goalSelector.addGoal(3, new NpcAIFindVehicle<>(this));
        goalSelector.addGoal(4, new NpcAIMountVehicle<>(this));
        goalSelector.addGoal(5, new NpcAIAimVehicle<>(this));
        goalSelector.addGoal(6, new NpcAIFireVehicle<>(this));
        //post-100 -- used by delayed shared tasks (look at random stuff, wander)
        goalSelector.addGoal(101, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        goalSelector.addGoal(102, new NpcAIWander(this));
        goalSelector.addGoal(103, new NpcAIWatchClosest(this, Mob.class, 8.0F));

        targetSelector.addGoal(1, new NpcAIFactionHurt(this, this::isHostileTowards));
        targetSelector.addGoal(2, new NpcAIAttackNearest(this, this::isHostileTowards));
        targetSelector.addGoal(3, new NpcAIDefendFriends(this));
    }

    @Override
    public String getNpcType() {
        return "siege_engineer";
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
    public boolean isFactionSiegeEngineer() {
        return true;
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
    public void resetTarget() {
        vehicleTarget = TargetFactory.NONE;
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
    public Optional<ITarget> getVehicleTarget() {
        return vehicleTarget == TargetFactory.NONE ? Optional.empty() : Optional.of(vehicleTarget);
    }
}
