/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/vehicle/NpcAIMountVehicle.
 */
package io.github.capttracker.ancientwarfarenpc.ai.vehicle;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.vehicle.IVehicleUser;
import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;

/** Walks to the assigned vehicle and boards a crew seat of it (by role, see {@code VehicleBase.boardAs}) once the NPC's bounding box, grown by 1, touches the vehicle's. */
public class NpcAIMountVehicle<T extends NpcBase & IVehicleUser> extends NpcAI<T> {
    private static final double MOUNT_REACH = 1.0D;

    public NpcAIMountVehicle(T npc) {
        super(npc);
    }

    @Override
    public boolean canUse() {
        return super.canUse() && !npc.isPassenger() && npc.canContinueRidingVehicle()
                && !npc.getAssignedVehicle().map(vehicle -> !vehicle.hasFreeSeat(SeatRole.DRIVER, SeatRole.GUNNER)).orElse(false);
    }

    @Override
    public void tick() {
        npc.getAssignedVehicle().ifPresent(vehicle -> {
            double distance = distanceSq(vehicle.blockPosition());
            if (npc.getBoundingBox().inflate(MOUNT_REACH).intersects(vehicle.getBoundingBox())) {
                vehicle.boardAs(npc, npc.getPreferredSeatRole()); // a free seat of the crew role it prefers, else the next one that works the weapon
            } else {
                moveToPosition(vehicle.blockPosition(), distance);
                npc.addAITask(TASK_MOVE);
            }
        });
    }

    @Override
    public void stop() {
        npc.resetAssignedVehicle();
        npc.removeAITask(TASK_MOVE);
    }
}
