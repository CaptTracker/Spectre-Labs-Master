/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (ship crews): how an NPC gets to a seat of a ship: over land, out through the water, or along the deck.
 */
package io.github.capttracker.ancientwarfarenpc.ai.vehicle;

import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * One NPC's way to a seat of a ship, ticked by the goal that wants it aboard. The pathfinder knows nothing of the hull parts of a ship, so the target is the
 * water beside the hull ({@link ShipCrew#waterBesideHull}): the NPC goes there by its navigation when there is a path (a dock, the shore), else it swims
 * straight out through the water (from the water, or after walking to the shore along the line to the ship), and boards the first free seat of its roles once it is
 * within {@link ShipCrew#BOARD_REACH} blocks of the hull. An NPC that already stands on the deck walks to the seat. It gives up when the ship is farther than the
 * range asked for, when it makes no headway for a few seconds, or when the time allowed ({@code 30 s + 1.5 s per block}, 3 minutes at most) runs out.
 */
public final class ShipApproach {
    public enum Status {
        /** The NPC is aboard. */
        BOARDED,
        /** Still on the way. */
        MOVING,
        /** The ship has no free seat for the NPC (or cannot be boarded now). */
        NO_SEAT,
        /** Too far, no way there, or out of time. */
        FAILED
    }

    private static final int DECK_WALK_TICKS = 100;
    private static final int STUCK_CHECK_TICKS = 40;
    private static final int MAX_STUCK_CHECKS = 4;
    /** How far from the hull the NPC aims, so it ends up inside the boarding reach without pushing against the hull. */
    private static final double STAND_OFF = 1.5D;

    private int ticks = 0;
    private int budget = -1;
    private int pathDelay = 0;
    private int deckTicks = 0;
    private int stuckTimer = 0;
    private int stuckChecks = 0;
    private boolean usingPath = false;
    private Vec3 lastPos = Vec3.ZERO;

    public void reset() {
        ticks = 0;
        budget = -1;
        pathDelay = 0;
        deckTicks = 0;
        stuckTimer = 0;
        stuckChecks = 0;
        usingPath = false;
    }

    /** One tick. {@code range} is how far from the hull (blocks) the NPC may start; the ship may move a little farther while it is on its way. */
    public Status tick(NpcBase npc, VehicleBase ship, List<SeatRole> roles, double range) {
        double hull = ShipCrew.hullDistance(ship, npc.getX(), npc.getZ());
        if (hull <= ShipCrew.BOARD_REACH && !isOnDeck(npc, ship)) {
            ShipCrew.Result attempt = ShipCrew.tryBoard(npc, ship, roles);
            if (attempt != ShipCrew.Result.TOO_FAR) {
                reset();
                return attempt == ShipCrew.Result.BOARDED ? Status.BOARDED : Status.NO_SEAT;
            }
        }
        if (budget < 0) {
            if (hull > range) {
                return Status.FAILED;
            }
            budget = Math.min(3600, 600 + (int) (hull * 30.0D));
            lastPos = npc.position();
        }
        if (++ticks > budget || hull > range * 1.5D + 8.0D) {
            return Status.FAILED;
        }
        if (isOnDeck(npc, ship)) {
            return walkDeck(npc, ship, roles);
        }
        if (!moveOut(npc, ship)) {
            return Status.FAILED;
        }
        if (++stuckTimer >= STUCK_CHECK_TICKS) {
            stuckTimer = 0;
            double moved = Math.hypot(npc.getX() - lastPos.x, npc.getZ() - lastPos.z);
            lastPos = npc.position();
            stuckChecks = moved < 0.3D ? stuckChecks + 1 : 0;
            if (stuckChecks >= MAX_STUCK_CHECKS) {
                return Status.FAILED;
            }
        }
        return Status.MOVING;
    }

    private static boolean isOnDeck(NpcBase npc, VehicleBase ship) {
        return ship.ship != null && ship.ship.isOnDeck(npc);
    }

    /** On the deck already: walk to the seat, and sit down there (or, if the rails or the mast are in the way, after a few seconds where it stands). */
    private Status walkDeck(NpcBase npc, VehicleBase ship, List<SeatRole> roles) {
        int seat = ShipCrew.firstFreeSeat(ship, roles);
        if (seat < 0) {
            return Status.NO_SEAT;
        }
        Vec3 at = ShipCrew.seatPosition(ship, seat);
        double distance = Math.hypot(at.x - npc.getX(), at.z - npc.getZ());
        if (distance <= 1.3D || ++deckTicks > DECK_WALK_TICKS) {
            ShipCrew.Result result = ShipCrew.tryBoard(npc, ship, roles);
            reset();
            return result == ShipCrew.Result.BOARDED ? Status.BOARDED : Status.NO_SEAT;
        }
        npc.getNavigation().stop();
        npc.getMoveControl().setWantedPosition(at.x, npc.getY(), at.z, 1.0D);
        npc.getLookControl().setLookAt(at.x, npc.getEyeY(), at.z);
        return Status.MOVING;
    }

    /** Heads for the water beside the hull. @return false if there is no way (on land with no shore on the line to the ship) */
    private boolean moveOut(NpcBase npc, VehicleBase ship) {
        Vec3 stand = ShipCrew.waterBesideHull(ship, npc.getX(), npc.getZ(), STAND_OFF);
        boolean wet = npc.isInWater();
        if (--pathDelay <= 0) {
            pathDelay = 20;
            Path path = npc.getNavigation().createPath(stand.x, stand.y, stand.z, 1);
            usingPath = path != null && path.canReach();
            if (usingPath) {
                npc.getNavigation().moveTo(path, 1.0D);
            }
        }
        if (usingPath) {
            return true;
        }
        if (wet || waterAhead(npc, stand)) {
            // swim straight at the hull
            npc.getNavigation().stop();
            npc.getMoveControl().setWantedPosition(stand.x, stand.y, stand.z, 1.0D);
            return true;
        }
        // on land: go to the shore on the line to the ship first
        BlockPos shore = shoreOnLine(npc, stand);
        if (shore == null) {
            return false;
        }
        Path path = npc.getNavigation().createPath(shore, 0);
        if (path == null) {
            return false;
        }
        npc.getNavigation().moveTo(path, 1.0D);
        return true;
    }

    /** True if there is water in the next block or two on the line to the point. */
    private static boolean waterAhead(NpcBase npc, Vec3 to) {
        Vec3 dir = new Vec3(to.x - npc.getX(), 0.0D, to.z - npc.getZ());
        if (dir.lengthSqr() < 1.0E-4D) {
            return false;
        }
        dir = dir.normalize();
        for (int step = 1; step <= 2; step++) {
            if (waterAt(npc.level(), npc.getX() + dir.x * step, npc.getY(), npc.getZ() + dir.z * step)) {
                return true;
            }
        }
        return false;
    }

    private static boolean waterAt(Level level, double x, double y, double z) {
        for (int dy = 0; dy >= -1; dy--) {
            BlockPos pos = BlockPos.containing(x, y + dy, z);
            if (level.isLoaded(pos) && level.getFluidState(pos).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    /** The last ground position on the straight line from the NPC to the point before water starts; null if the line has no water or leaves the loaded world. */
    @Nullable
    private static BlockPos shoreOnLine(NpcBase npc, Vec3 to) {
        Level level = npc.level();
        double dx = to.x - npc.getX();
        double dz = to.z - npc.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0D) {
            return null;
        }
        dx /= length;
        dz /= length;
        BlockPos previous = null;
        int steps = (int) Math.min(length, AWNPCStatics.shipBoardRange + 16);
        for (int step = 1; step <= steps; step++) {
            double x = npc.getX() + dx * step;
            double z = npc.getZ() + dz * step;
            BlockPos column = BlockPos.containing(x, npc.getY(), z);
            if (!level.isLoaded(column)) {
                return null;
            }
            // the water of this column, within a few blocks of the NPC's own height
            boolean water = false;
            for (int dy = 1; dy >= -3 && !water; dy--) {
                water = level.getFluidState(column.above(dy)).is(FluidTags.WATER);
            }
            if (water) {
                return previous;
            }
            previous = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        }
        return null;
    }
}
