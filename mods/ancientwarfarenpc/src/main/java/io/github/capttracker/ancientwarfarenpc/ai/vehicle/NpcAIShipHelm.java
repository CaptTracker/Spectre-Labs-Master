/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (ship crews): an owned NPC at the helm of a ship sails it by the orders of its owner.
 */
package io.github.capttracker.ancientwarfarenpc.ai.vehicle;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.trade.SeaDocks;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipHelper;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipRoutePlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The duty of an owned NPC that sits in the DRIVER seat of a ship (it took the helm when it was ordered aboard). It holds the move flags at the top priority,
 * like the rest of the crew. The orders are the ones the owner gave with the command baton ({@code NpcPlayerOwned.getSailTarget}, {@code getSailFollow}): sail to a
 * water position, or follow a vessel (a Move order at another ship or boat, or the follow order of the owner while the owner is in another vessel).
 *
 * <p>Steering: the ship's autopilot ({@code ShipHelper.setPilot}) sets the sail and oar trim, turns towards the waypoint and slows down and stops near it. On top
 * of it the helmsman looks ahead over the water ({@code ShipHelper.isWaterClear}: the hull turned to a heading, a ship length or more ahead, and half way): if the
 * way to the destination is not clear (shallows, the shore, a block in the hull's way) it steers to the nearest heading that is, to the side it chose last, and goes
 * back to the straight line when that is clear again. It gives up, stops the ship and says so when nothing is clear for 3 seconds or when it made no headway for
 * {@code ship_helm_stuck_seconds}. Rowers add speed by sitting in their seats, as with a player at the helm.
 *
 * <p>Rework phase 11: the steering itself is {@link ShipSteering} (shared with the faction trade ships). A sail order may carry planned waypoints
 * ({@code NpcPlayerOwned.setSailRouteOrder}, the sea trade routes): the helmsman passes them without stopping. How an order ended (arrived, stuck) is kept in
 * {@code NpcPlayerOwned.takeSailResult} for whoever gave it.
 */
public class NpcAIShipHelm extends NpcAI<NpcPlayerOwned> {
    private final ShipSteering steering = new ShipSteering();
    /** The steering of the flight to a dock (rework phase 11), apart from the one of the orders so that the two do not share their course. */
    private final ShipSteering fleeSteering = new ShipSteering();
    /** The destination a route was last planned for (or tried for), so that one order is planned once. Not saved: a loaded NPC plans its order again. */
    @Nullable
    private BlockPos plannedFor;

    public NpcAIShipHelm(NpcPlayerOwned npc) {
        super(npc);
        setMutexBits(MOVE | ATTACK);
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public boolean canUse() {
        return !npc.level().isClientSide && npc.getIsAIEnabled() && npc.getHelmShip() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        steering.reset();
        plannedFor = null;
    }

    @Override
    public void stop() {
        npc.clearSailOrder(); // it is not at the helm any more (a player took it, it was ordered off): the order goes with the helm
        steering.reset();
    }

    @Override
    public void tick() {
        VehicleBase ship = npc.getHelmShip();
        if (ship == null || ship.ship == null || !ship.ship.hasSpec() || ship.ship.isSinking()) {
            return;
        }
        ShipHelper helper = ship.ship;
        npc.getNavigation().stop();
        ShipCrew.eatAboard(npc, ship);
        if (!AWNPCStatics.shipHelmsmen) {
            return;
        }
        if (ShipCombat.helmTick(npc, ship, fleeSteering)) {
            return; // ramming a hostile ship, or fleeing to a dock: the sail order waits (rework phase 11)
        }
        double length = helper.spec().length();

        // what to steer for: a vessel to follow, else the destination
        Entity vessel = followedVessel(ship);
        BlockPos sail = npc.getSailTarget();
        Vec3 target;
        double arrive;
        boolean follow = vessel != null;
        if (follow) {
            target = vessel.position();
            double other = vessel instanceof VehicleBase v && v.ship != null && v.ship.hasSpec() ? v.ship.spec().length() / 2.0D : 1.5D;
            arrive = 6.0D + length / 2.0D + other;
        } else if (sail != null) {
            target = new Vec3(sail.getX() + 0.5D, ship.getY(), sail.getZ() + 0.5D);
            arrive = 2.0D + length * 0.3D;
        } else {
            steering.reset();
            return; // no order: the ship runs down by itself
        }
        if (!follow && npc.getSailRoute().isEmpty()) {
            planRoute(ship, sail);
        } else if (follow || sail == null) {
            plannedFor = null;
        }
        npc.setDoing(follow ? "ship.follow" : "ship.helm", BlockPos.containing(target), Component.empty());

        switch (steering.tick(ship, target, follow ? null : npc.getSailRoute(), arrive, follow, AWNPCStatics.shipHelmStuckSeconds)) {
            case ARRIVED -> {
                npc.clearSailOrder();
                npc.think("ship_arrived");
                npc.setSailResult(NpcPlayerOwned.SailResult.ARRIVED);
            }
            case STUCK -> {
                npc.clearSailOrder();
                npc.think("ship_stuck");
                npc.setSailResult(NpcPlayerOwned.SailResult.STUCK);
            }
            case SAILING -> { }
        }
    }

    /**
     * A plain Move order (no waypoints yet): plans the route over the water to the clicked spot for the hull's width ({@link ShipRoutePlanner}, the planner of the
     * sea trade) and keeps it as the sail route, which {@link ShipSteering} then follows. Done once for each destination. When there is no route (or the spot is
     * too far for the planner) the helmsman falls back to the look-ahead and the owner is told.
     */
    private void planRoute(VehicleBase ship, BlockPos sail) {
        if (sail.equals(plannedFor)) {
            return;
        }
        plannedFor = sail;
        ShipRoutePlanner.Plan plan = ShipRoutePlanner.planBetween(npc.level(), ship.position(), sail, ship.blockPosition().getY(), SeaDocks.clearanceOf(ship), false);
        if (plan != null && !plan.route().isEmpty()) {
            npc.setSailRouteOrder(sail, plan.route());
            return;
        }
        npc.tellOwner(Component.translatable("message.ancientwarfarenpc.ship.no_route", sail.getX(), sail.getY(), sail.getZ()));
    }

    /** The vessel to follow: the one of the follow order, else the one the owner's follow order points to; null if there is none (or it is the NPC's own ship). */
    @Nullable
    private Entity followedVessel(VehicleBase ship) {
        UUID id = npc.getSailFollow();
        if (id != null) {
            Entity followed = npc.level() instanceof ServerLevel level ? level.getEntity(id) : null;
            Entity vessel = ShipCrew.vesselOf(followed, ship);
            if (vessel == null) {
                npc.clearSailOrder(); // it is gone, or it sits in this very ship
            }
            return vessel;
        }
        if (npc.getSailTarget() != null) {
            return null;
        }
        LivingEntity following = npc.getFollowingEntity();
        return following == null ? null : ShipCrew.vesselOf(following, ship);
    }
}
