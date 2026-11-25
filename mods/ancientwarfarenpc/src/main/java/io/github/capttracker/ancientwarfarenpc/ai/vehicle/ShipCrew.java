/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (ship crews): how owned NPCs get on and off the ships of the Vehicles module.
 */
package io.github.capttracker.ancientwarfarenpc.ai.vehicle;

import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipPart;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Helpers for owned NPCs that crew a ship (rowers, gunners, passengers; the helm stays with the player). Boarding takes a seat of exactly the role asked for
 * ({@link VehicleBase#boardStrict}); the NPC must be within {@link #BOARD_REACH} blocks of the hull, which in practice means a ship moored at a dock or the shore
 * (NPCs cannot walk or swim out to a ship; the pathfinder knows nothing of the hull parts). Getting off puts the NPC on dry land close by when there is some.
 */
public final class ShipCrew {
    /** How near (blocks, to the edge of the hull) an NPC must be to board. */
    public static final double BOARD_REACH = 3.0D;
    /** How far from the NPC's seat a landing place is looked for when it gets off. */
    private static final int LANDING_RADIUS = 7;

    private ShipCrew() {}

    /** The ship an entity stands for: the ship itself or one of its hull parts; null for anything else, a sinking ship and a design-less ship included. */
    @Nullable
    public static VehicleBase shipOf(@Nullable Entity entity) {
        Entity e = entity instanceof ShipPart part ? part.getParent() : entity;
        return e instanceof VehicleBase vehicle && vehicle.isShip() && vehicle.isAlive() && !vehicle.ship.isSinking() ? vehicle : null;
    }

    /** The first role of the list that has a free seat on the ship, or null. */
    @Nullable
    public static SeatRole firstFreeRole(VehicleBase ship, List<SeatRole> roles) {
        for (SeatRole role : roles) {
            if (ship.hasFreeSeat(role)) {
                return role;
            }
        }
        return null;
    }

    /** How many seats of the roles are free on the ship. */
    public static int freeSeats(VehicleBase ship, List<SeatRole> roles) {
        int free = 0;
        for (int i = 0; i < ship.getSeatCount(); i++) {
            if (roles.contains(ship.getSeat(i).role()) && ship.occupantAt(i) == null) {
                free++;
            }
        }
        return free;
    }

    /** The distance (blocks, horizontal) from a point to the edge of the ship's hull; 0 over it. */
    public static double hullDistance(VehicleBase ship, double x, double z) {
        ShipSpec spec = ship.ship == null ? null : ship.ship.spec();
        if (spec == null) {
            return Math.sqrt(ship.distanceToSqr(x, ship.getY(), z));
        }
        double[] local = toLocal(ship, x, z);
        double over = Math.max(0.0D, Math.abs(local[0]) - spec.length() / 2.0D);
        double side = Math.max(0.0D, Math.abs(local[1]) - spec.width() / 2.0D);
        return Math.sqrt(over * over + side * side);
    }

    /** The point of the hull (at the waterline) that is nearest to a point: where an NPC on the shore goes to board. */
    public static Vec3 nearestHullPoint(VehicleBase ship, double x, double z) {
        ShipSpec spec = ship.ship == null ? null : ship.ship.spec();
        if (spec == null) {
            return ship.position();
        }
        double[] local = toLocal(ship, x, z);
        double forward = Math.max(-spec.length() / 2.0D, Math.min(spec.length() / 2.0D, local[0]));
        double horizontal = Math.max(-spec.width() / 2.0D, Math.min(spec.width() / 2.0D, local[1]));
        return ship.ship.toWorld((float) forward, (float) horizontal, 0.0F);
    }

    /** The ship's frame: forward and sideways offset of a point from the middle of the ship (the inverse of {@code ShipHelper.toWorld}). */
    private static double[] toLocal(VehicleBase ship, double x, double z) {
        double rx = x - ship.getX();
        double rz = z - ship.getZ();
        double yaw = Math.toRadians(ship.getYRot());
        return new double[] {-(rx * Math.sin(yaw) + rz * Math.cos(yaw)), rx * Math.cos(yaw) - rz * Math.sin(yaw)};
    }

    /** The result of {@link #tryBoard}. */
    public enum Result { BOARDED, TOO_FAR, NO_SEAT }

    /**
     * Puts the NPC in the first free seat of its roles (in the order given) if it is near enough. {@code NO_SEAT}: the ship has no free seat for it (or cannot be
     * boarded now: sinking, setting up); {@code TOO_FAR}: walk nearer ({@link #nearestHullPoint}) and try again.
     */
    public static Result tryBoard(NpcPlayerOwned npc, VehicleBase ship, List<SeatRole> roles) {
        if (shipOf(ship) == null || ship.isSettingUp()) {
            return Result.NO_SEAT;
        }
        SeatRole role = firstFreeRole(ship, roles);
        if (role == null) {
            return Result.NO_SEAT;
        }
        if (hullDistance(ship, npc.getX(), npc.getZ()) > BOARD_REACH || Math.abs(npc.getY() - ship.getY()) > 5.0D) {
            return Result.TOO_FAR;
        }
        npc.getNavigation().stop();
        if (npc.isPassenger()) {
            npc.stopRiding();
        }
        return ship.boardStrict(npc, role) ? Result.BOARDED : Result.NO_SEAT;
    }

    /**
     * Gets the NPC off the ship it crews: it stops riding and is put on the nearest dry land to {@code towards} within a few blocks of its seat, or, if there
     * is none, it stands on the deck beside its seat.
     */
    public static void disembark(NpcPlayerOwned npc, VehicleBase ship, Vec3 towards) {
        if (!npc.isPassenger() || npc.getVehicle() != ship) {
            return;
        }
        npc.stopRiding();
        Vec3 land = landingNear(npc.level(), npc.position(), towards);
        if (land != null) {
            npc.teleportTo(land.x, land.y, land.z);
        }
        npc.getNavigation().stop();
    }

    /** Dry land (a solid block with two free blocks above it) within {@link #LANDING_RADIUS} blocks of the point, the one nearest to {@code towards}; null if there is none. */
    @Nullable
    private static Vec3 landingNear(Level level, Vec3 from, Vec3 towards) {
        BlockPos center = BlockPos.containing(from);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -LANDING_RADIUS; dx <= LANDING_RADIUS; dx++) {
            for (int dz = -LANDING_RADIUS; dz <= LANDING_RADIUS; dz++) {
                for (int dy = 3; dy >= -4; dy--) {
                    BlockPos ground = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(ground);
                    if (!state.getFluidState().isEmpty() || !state.isFaceSturdy(level, ground, Direction.UP)) {
                        continue;
                    }
                    if (!level.getBlockState(ground.above()).getCollisionShape(level, ground.above()).isEmpty()
                            || !level.getBlockState(ground.above(2)).getCollisionShape(level, ground.above(2)).isEmpty()
                            || !level.getFluidState(ground.above()).isEmpty()) {
                        continue;
                    }
                    double distance = ground.getCenter().distanceToSqr(towards);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = ground;
                    }
                    break; // the highest standing place of this column is enough
                }
            }
        }
        return best == null ? null : new Vec3(best.getX() + 0.5D, best.getY() + 1.0D, best.getZ() + 0.5D);
    }
}
