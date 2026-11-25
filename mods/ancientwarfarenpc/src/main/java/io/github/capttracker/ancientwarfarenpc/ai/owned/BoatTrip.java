/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): a worker's trips in the boat of a fishing dock.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfare.api.IBoatWorksite;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.entity.NpcWorker;
import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The boat side of a staffed worker at an {@link IBoatWorksite}: walk to the boat and board it as the driver, steer it along the waypoints the site plans
 * (the ship's autopilot is set every tick, the boat entity is driven directly, vanilla's boat input is not used), get off at the berth.
 * {@code StaffedWork} decides when to board, sail, fish and return; this class keeps the route and the trip's counters.
 */
final class BoatTrip {
    enum Sail { SAILING, ARRIVED, NO_ROUTE, STUCK }

    private static final double BOARD_REACH_SQ = 25.0D;
    private static final int STUCK_CHECK_TICKS = 100;
    private static final int BOARD_STUCK_TICKS = 300;

    private final NpcWorker npc;
    /** The fish caught on this trip. */
    int catches = 0;
    /** The trip is over: sail home. */
    boolean returning = false;
    /** The pouch must be emptied at the dock now (the boat came back). */
    boolean unloadPending = false;

    private List<Vec3> route = List.of();
    private int routeIndex = 0;
    @Nullable
    private BlockPos routeTarget;
    private Vec3 checkPos = Vec3.ZERO;
    private int checkTimer = 0;
    private int boardStuck = 0;

    BoatTrip(NpcWorker npc) {
        this.npc = npc;
    }

    /** The boat of the dock if the worker sits in it. */
    boolean aboard(IBoatWorksite dock) {
        Entity boat = dock.getBoat();
        return boat != null && npc.getVehicle() == boat;
    }

    /** Walks to the boat and boards it. @return true once aboard, false while walking, null if it cannot be done (the boat is gone or busy) */
    @Nullable
    Boolean board(IBoatWorksite dock, StaffedWork.Mover mover) {
        Entity entity = dock.getBoat();
        if (!(entity instanceof VehicleBase boat) || !boat.hasFreeSeat(SeatRole.DRIVER)) {
            return null;
        }
        if (npc.isPassenger()) {
            npc.stopRiding();
        }
        double dx = boat.getX() - npc.getX();
        double dz = boat.getZ() - npc.getZ();
        double sq = dx * dx + dz * dz;
        if (sq <= BOARD_REACH_SQ && Math.abs(boat.getY() - npc.getY()) < 4.0D) {
            npc.getNavigation().stop();
            npc.removeAITask(NpcAI.TASK_MOVE);
            if (boat.boardAs(npc, SeatRole.DRIVER)) {
                boardStuck = 0;
                clearRoute();
                return true;
            }
            return null;
        }
        npc.addAITask(NpcAI.TASK_MOVE);
        mover.move(boat.getX(), boat.getY(), boat.getZ(), sq);
        return ++boardStuck > BOARD_STUCK_TICKS ? null : Boolean.FALSE;
    }

    /**
     * Steers the boat towards the target by the planned waypoints. {@code arrive} is the distance (blocks) at which the worker counts as there (measured from the
     * worker for a fishing spot, from the boat for the berth).
     */
    Sail sail(IBoatWorksite dock, BlockPos target, double arrive, boolean forBerth) {
        if (!(dock.getBoat() instanceof VehicleBase boat) || boat.ship == null) {
            return Sail.NO_ROUTE;
        }
        Vec3 center = Vec3.atBottomCenterOf(target);
        double dx = center.x - (forBerth ? boat.getX() : npc.getX());
        double dz = center.z - (forBerth ? boat.getZ() : npc.getZ());
        if (dx * dx + dz * dz <= arrive * arrive) {
            if (!forBerth || boat.getDeltaMovement().horizontalDistanceSqr() < 0.004D) {
                boat.ship.setPilot(center, 1.5D, true); // hold
                return Sail.ARRIVED;
            }
        }
        if (routeTarget == null || !routeTarget.equals(target) || route.isEmpty()) {
            List<Vec3> planned = dock.planRoute(boat.position(), target);
            if (planned == null) {
                return Sail.NO_ROUTE;
            }
            route = new ArrayList<>(planned);
            routeIndex = 0;
            routeTarget = target;
            checkPos = boat.position();
            checkTimer = 0;
        }
        while (routeIndex < route.size() - 1) {
            Vec3 wp = route.get(routeIndex);
            double wx = wp.x - boat.getX();
            double wz = wp.z - boat.getZ();
            if (wx * wx + wz * wz < 12.0D) {
                routeIndex++;
            } else {
                break;
            }
        }
        boolean last = routeIndex >= route.size() - 1;
        Vec3 wp = route.get(routeIndex);
        boat.ship.setPilot(new Vec3(wp.x, boat.getY(), wp.z), last ? Math.max(1.5D, forBerth ? 1.5D : arrive * 0.5D) : 3.0D, last);
        if (++checkTimer >= STUCK_CHECK_TICKS) {
            checkTimer = 0;
            if (boat.position().distanceToSqr(checkPos) < 0.5D) {
                return Sail.STUCK;
            }
            checkPos = boat.position();
        }
        return Sail.SAILING;
    }

    /** Keeps the boat at the fishing spot. */
    void hold(IBoatWorksite dock, BlockPos target) {
        if (dock.getBoat() instanceof VehicleBase boat && boat.ship != null) {
            boat.ship.setPilot(Vec3.atBottomCenterOf(target), 2.0D, true);
        }
    }

    /** Gets off at the dock: the worker is put on the landing. */
    void dismount(IBoatWorksite dock) {
        if (npc.isPassenger()) {
            npc.stopRiding();
        }
        BlockPos landing = dock.getLanding();
        npc.teleportTo(landing.getX() + 0.5D, landing.getY(), landing.getZ() + 0.5D);
        if (dock.getBoat() instanceof VehicleBase boat && boat.ship != null) {
            boat.ship.clearPilot();
        }
        clearRoute();
    }

    /** The trip is over and the worker is ashore. */
    void endTrip() {
        catches = 0;
        returning = false;
        unloadPending = true;
        clearRoute();
    }

    void clearRoute() {
        route = List.of();
        routeIndex = 0;
        routeTarget = null;
        checkTimer = 0;
        boardStuck = 0;
    }
}
