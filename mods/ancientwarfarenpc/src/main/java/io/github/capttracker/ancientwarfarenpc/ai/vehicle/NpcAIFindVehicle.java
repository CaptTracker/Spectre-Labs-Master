/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/vehicle/NpcAIFindVehicle.
 */
package io.github.capttracker.ancientwarfarenpc.ai.vehicle;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.vehicle.IVehicleUser;
import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import net.minecraft.world.entity.Entity;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Searches 30 blocks around the NPC for a free vehicle that is drivable and that soldiers may pilot, and takes the nearest; or adopts the vehicle the NPC
 * already rides. Works through the Vehicles module's {@link VehicleBase}.
 */
public class NpcAIFindVehicle<T extends NpcBase & IVehicleUser> extends NpcAI<T> {
    private static final double SEARCH_DISTANCE = 30D;
    private static final Predicate<VehicleBase> SELECTOR = v -> v != null && v.isDrivable() && v.hasFreeSeat(SeatRole.DRIVER);

    public NpcAIFindVehicle(T npc) {
        super(npc);
    }

    @Override
    public boolean canUse() {
        return super.canUse() && npc.getAssignedVehicle().isEmpty() && npc.canContinueRidingVehicle() && !(npc.getVehicle() instanceof VehicleBase ship && ship.isShip())
                && (!npc.isPassenger() || npc.getVehicle() instanceof VehicleBase);
    }

    @Override
    public void tick() {
        Entity mount = npc.getVehicle();
        if (mount instanceof VehicleBase ridden) {
            npc.setAssignedVehicle(ridden);
            return;
        }
        List<VehicleBase> vehicles = npc.level().getEntitiesOfClass(VehicleBase.class, npc.getBoundingBox().inflate(SEARCH_DISTANCE), SELECTOR);
        vehicles.stream().filter(v -> v.hasFreeSeat(SeatRole.DRIVER) && v.vehicleType.canSoldiersPilot())
                .min(Comparator.comparingDouble(v -> v.distanceToSqr(npc))).ifPresent(v -> npc.setAssignedVehicle(v));
    }
}
