/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (ship crews): how NPCs get on and off the ships of the Vehicles module, and what a crew member does about hunger.
 */
package io.github.capttracker.ancientwarfarenpc.ai.vehicle;

import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipHelper;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipPart;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipSpec;
import io.github.capttracker.spectrefactions.api.Relation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Helpers for NPCs that crew a ship (rowers, gunners, passengers, and the helm for owned NPCs). Boarding takes a seat of exactly the role asked for
 * ({@link VehicleBase#boardStrict}); the NPC must be within {@link #BOARD_REACH} blocks of the hull, and {@link ShipApproach} gets it there (walking, swimming out
 * to a ship in open water, walking the deck to its seat). Getting off puts the NPC on dry land close by when there is some.
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

    /**
     * A point on the water {@code out} blocks beside the hull, on the side of a point: where an NPC that swims out to the ship heads for (the hull parts are not
     * in the pathfinder's world, so the target is the water next to them). At the height of the water surface.
     */
    public static Vec3 waterBesideHull(VehicleBase ship, double x, double z, double out) {
        ShipSpec spec = ship.ship == null ? null : ship.ship.spec();
        if (spec == null) {
            return ship.position();
        }
        double[] local = toLocal(ship, x, z);
        double halfLength = spec.length() / 2.0D;
        double halfWidth = spec.width() / 2.0D;
        double forward = Math.max(-halfLength, Math.min(halfLength, local[0]));
        double horizontal = Math.max(-halfWidth, Math.min(halfWidth, local[1]));
        double dx = local[0] - forward;
        double dz = local[1] - horizontal;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-3D) {
            // over the hull already: step out over the side
            dx = 0.0D;
            dz = local[1] >= 0.0D ? 1.0D : -1.0D;
            length = 1.0D;
        }
        Vec3 world = ship.ship.toWorld((float) (forward + dx / length * out), (float) (horizontal + dz / length * out), 0.0F);
        return new Vec3(world.x, ship.getY() + spec.draft, world.z);
    }

    /** The ship's frame: forward and sideways offset of a point from the middle of the ship (the inverse of {@code ShipHelper.toWorld}). */
    private static double[] toLocal(VehicleBase ship, double x, double z) {
        double rx = x - ship.getX();
        double rz = z - ship.getZ();
        double yaw = Math.toRadians(ship.getYRot());
        return new double[] {-(rx * Math.sin(yaw) + rz * Math.cos(yaw)), rx * Math.cos(yaw) - rz * Math.sin(yaw)};
    }

    /**
     * The roles an owned NPC asks for when it is ordered aboard {@code ship}, best first: its own preference ({@code getShipSeatPreference}) with the helm
     * added when it is free and the config allows helmsmen (first for workers and the like, after the first gun for soldiers).
     */
    public static List<SeatRole> boardRoles(NpcBase npc, VehicleBase ship) {
        List<SeatRole> preference = npc.getShipSeatPreference();
        if (!AWNPCStatics.shipHelmsmen || !(npc instanceof NpcPlayerOwned) || !ship.hasFreeSeat(SeatRole.DRIVER)) {
            return preference;
        }
        List<SeatRole> roles = new ArrayList<>(preference);
        roles.add(preference.contains(SeatRole.GUNNER) ? 1 : 0, SeatRole.DRIVER);
        return roles;
    }

    /** The first free seat of the first role of the list that has one, or -1. */
    public static int firstFreeSeat(VehicleBase ship, List<SeatRole> roles) {
        for (SeatRole role : roles) {
            for (int i = 0; i < ship.getSeatCount(); i++) {
                if (ship.getSeat(i).role() == role && ship.occupantAt(i) == null) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Where a rider of the seat sits (world position at deck height). */
    public static Vec3 seatPosition(VehicleBase ship, int seat) {
        if (ship.ship == null || ship.ship.spec() == null) {
            return ship.position();
        }
        return ship.ship.toWorld(ship.getSeat(seat).forward(), ship.getSeat(seat).horizontal(), ship.ship.spec().deckTop());
    }

    /**
     * The surface block of the water at a clicked position, or null if it is not water a ship can sail ({@link ShipHelper#MIN_DEPTH} deep). The baton's ray does
     * not stop at water, so the clicked block is often the bed under it: the water above counts.
     */
    @Nullable
    public static BlockPos waterSurface(Level level, BlockPos clicked) {
        BlockPos start = isWater(level, clicked) ? clicked : (isWater(level, clicked.above()) ? clicked.above() : null);
        if (start == null) {
            return null;
        }
        BlockPos top = start;
        for (int i = 0; i < 64 && isWater(level, top.above()); i++) {
            top = top.above();
        }
        int depth = 0;
        BlockPos below = top;
        while (depth < ShipHelper.MIN_DEPTH && isWater(level, below)) {
            depth++;
            below = below.below();
        }
        return depth >= ShipHelper.MIN_DEPTH ? top : null;
    }

    /** True if the clicked position (or the block above it) holds water, however shallow. */
    public static boolean isWaterNear(Level level, BlockPos clicked) {
        return isWater(level, clicked) || isWater(level, clicked.above());
    }

    private static boolean isWater(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getFluidState(pos).is(FluidTags.WATER);
    }

    /** The vessel an entity is or is in: a ship of the Vehicles module or a vanilla boat; null for anything else, and for {@code except} (may be null). */
    @Nullable
    public static Entity vesselOf(@Nullable Entity entity, @Nullable VehicleBase except) {
        if (entity == null) {
            return null;
        }
        VehicleBase ship = shipOf(entity);
        if (ship == null && entity.getVehicle() != null) {
            ship = shipOf(entity.getVehicle());
        }
        if (ship != null) {
            return ship == except ? null : ship;
        }
        if (entity instanceof Boat) {
            return entity;
        }
        return entity.getVehicle() instanceof Boat boat ? boat : null;
    }

    /** True if an allied player (or an NPC of an allied player) is at the helm of the ship: what the soldiers of an NPC faction need to crew it. */
    public static boolean isAlliedHelm(NpcBase npc, VehicleBase ship) {
        LivingEntity helm = ship.getControllingPassenger();
        return helm != null && helm != npc && npc.getRelationTo(helm).filter(relation -> relation == Relation.ALLIED).isPresent();
    }

    /**
     * Server side, called by the crew goals every tick: a hungry owned NPC in a seat eats from the ship's storage, else from what it carries, without leaving
     * its seat (the food values, hunger and morale are the ones of eating at a storage: {@code withdrawFood}). With no food it reports it now and then.
     */
    public static void eatAboard(NpcPlayerOwned npc, VehicleBase ship) {
        if (!AWNPCStatics.shipCrewEat || npc.level().isClientSide || (npc.tickCount + npc.getId()) % 20 != 0 || !npc.requiresUpkeep()
                || !(npc.isHungry() || npc.isStarving())) {
            return;
        }
        int target = (int) (npc.getMaxFood() * SettlementConfig.eatUntilFraction) + 1;
        int before = npc.getFoodRemaining();
        npc.withdrawFood(ship.inventory.storageInventory, target);
        IItemHandler carried = npc.getCarriedFood();
        if (npc.getFoodRemaining() < target && carried != null) {
            npc.withdrawFood(carried, target);
        }
        if (npc.getFoodRemaining() > before) {
            npc.think("ate");
        } else if ((npc.tickCount + npc.getId()) % 1200 == 0) {
            npc.think("ship_no_food");
        }
    }

    /** The result of {@link #tryBoard}. */
    public enum Result { BOARDED, TOO_FAR, NO_SEAT }

    /**
     * Puts the NPC in the first free seat of its roles (in the order given) if it is near enough. {@code NO_SEAT}: the ship has no free seat for it (or cannot be
     * boarded now: sinking, setting up); {@code TOO_FAR}: get nearer ({@link ShipApproach}) and try again.
     */
    public static Result tryBoard(NpcBase npc, VehicleBase ship, List<SeatRole> roles) {
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
    public static void disembark(NpcBase npc, VehicleBase ship, Vec3 towards) {
        if (!npc.isPassenger() || npc.getVehicle() != ship) {
            return;
        }
        if (ship.ship != null && ship.getControllingPassenger() == npc) {
            ship.ship.clearPilot(); // a helmsman that leaves lets the ship run down
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
