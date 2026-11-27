/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 12): an engineer in the driver seat of a siege tower or a mantlet pushes it towards his target.
 */
package io.github.capttracker.ancientwarfarenpc.ai.vehicle;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.vehicle.IVehicleUser;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;

/**
 * A siege engineer at the driver seat of a pushed vehicle ({@code IVehicleType#isPushed}: the siege tower and the mantlet) steers it in a straight line towards the target he was
 * given (an enemy or the block of an attack order) and stops a few blocks short of it; a tower that is stopped by a wall lowers its bridge by itself (config {@code siege_auto_bridge}).
 * It does not find a way round obstacles: the vehicle's own move helper steers, and the tower stops at what it cannot climb. The vehicle only moves with pushers aboard
 * (the driver is one; other NPCs can take the pusher seats), so the speed is that of the crew.
 */
public class NpcAISiegeAdvance<T extends NpcBase & IVehicleUser> extends NpcAI<T> {
    /** The vehicle stops this far from its target (middle to middle), in blocks. */
    private static final double STOP_DISTANCE = 2.5D;

    public NpcAISiegeAdvance(T npc) {
        super(npc);
    }

    private VehicleBase pushedVehicle() {
        VehicleBase vehicle = npc.getAssignedVehicle().orElse(null);
        return vehicle != null && vehicle.vehicleType.isPushed() && vehicle.getControllingPassenger() == npc ? vehicle : null;
    }

    @Override
    public boolean canUse() {
        return super.canUse() && npc.getVehicleTarget().isPresent() && npc.canContinueRidingVehicle() && npc.isRidingVehicle() && pushedVehicle() != null;
    }

    @Override
    public void tick() {
        VehicleBase vehicle = pushedVehicle();
        if (vehicle == null) {
            return;
        }
        npc.getVehicleTarget().ifPresent(target -> {
            double dx = target.getX() - vehicle.getX();
            double dz = target.getZ() - vehicle.getZ();
            if (Math.hypot(dx, dz) <= STOP_DISTANCE) {
                vehicle.moveHelper.setForwardInput((byte) 0);
                vehicle.moveHelper.setStrafeInput((byte) 0);
            } else {
                vehicle.moveHelper.setMoveTo(target.getX(), target.getY(), target.getZ());
            }
        });
    }

    @Override
    public void stop() {
        npc.getAssignedVehicle().ifPresent(v -> {
            v.moveHelper.setForwardInput((byte) 0);
            v.moveHelper.setStrafeInput((byte) 0);
        });
    }
}
