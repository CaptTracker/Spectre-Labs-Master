/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/vehicle/NpcAIAimVehicle.
 */
package io.github.capttracker.ancientwarfarenpc.ai.vehicle;

import io.github.capttracker.ancientwarfare.util.Trig;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.vehicle.ITarget;
import io.github.capttracker.ancientwarfarenpc.entity.vehicle.IVehicleUser;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;

/**
 * Aims the vehicle at the target of the NPC until the target is aimed at: when the turret cannot reach the yaw the whole vehicle is turned with the strafe
 * input of the move helper, otherwise {@code VehicleFiringHelper.handleSoldierTargetInput} sets the turret angles.
 */
public class NpcAIAimVehicle<T extends NpcBase & IVehicleUser> extends NpcAI<T> {
    public NpcAIAimVehicle(T npc) {
        super(npc);
    }

    @Override
    public boolean canUse() {
        return super.canUse() && npc.getVehicleTarget().isPresent() && npc.canContinueRidingVehicle() && npc.isRidingVehicle()
                && npc.getVehicleTarget().map(t -> !npc.getAssignedVehicle().map(v -> v.firingHelper.isAimedAt(t)).orElse(false)).orElse(false);
    }

    @Override
    public void tick() {
        npc.getAssignedVehicle().ifPresent(vehicle -> npc.getVehicleTarget().ifPresent(target -> {
            if (turnVehicleIfYawDifferenceGreat(vehicle, target)) {
                return;
            }
            vehicle.moveHelper.setStrafeInput((byte) 0);
            vehicle.moveHelper.setForwardInput((byte) 0);
            vehicle.firingHelper.handleSoldierTargetInput(target.getX(), target.getY(), target.getZ());
        }));
    }

    @SuppressWarnings("squid:S1066")
    private boolean turnVehicleIfYawDifferenceGreat(VehicleBase vehicle, ITarget target) {
        float yawDiff = Trig.getAngleDiffSigned(vehicle.getYRot(), vehicle.firingHelper.getAimYaw(target));
        if (!vehicle.vehicleType.canAdjustYaw() && Math.abs(yawDiff) < 2) {
            //if there's a minor difference in the rotation just set the rotation to it instead of continues steps to one side and back
            vehicle.setYRot(vehicle.getYRot() + yawDiff);
            vehicle.moveHelper.stopMotion();
        } else if (vehicle.vehicleType.getBaseTurretRotationAmount() < 180 || Math.abs(yawDiff) > 120) {
            //if turret cannot rotate fully around, or if it can but yaw diff is great, turn towards target
            if (!Trig.isAngleBetween(vehicle.getYRot() + yawDiff, vehicle.localTurretRotationHome - getMaxRotDifference(vehicle),
                    vehicle.localTurretRotationHome + getMaxRotDifference(vehicle))) {
                if (yawDiff < 0) {
                    vehicle.moveHelper.setStrafeInput((byte) 1); //left
                } else {
                    vehicle.moveHelper.setStrafeInput((byte) -1); //right
                }
                vehicle.moveHelper.setForwardInput((byte) 0);
                vehicle.firingHelper.handleSoldierTargetInput(target.getX(), target.getY(), target.getZ());
                return true;
            }
        }
        return false;
    }

    private float getMaxRotDifference(VehicleBase vehicle) {
        return Math.min(vehicle.currentTurretRotationMax + 1.5f, 180f);
    }

    @Override
    public void stop() {
        npc.getAssignedVehicle().ifPresent(v -> v.moveHelper.setStrafeInput((byte) 0));
    }
}
