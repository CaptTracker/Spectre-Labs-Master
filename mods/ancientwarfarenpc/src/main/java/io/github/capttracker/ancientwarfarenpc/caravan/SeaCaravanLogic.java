/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 11): what the leader of a faction trade ship does each tick.
 */
package io.github.capttracker.ancientwarfarenpc.caravan;

import io.github.capttracker.ancientwarfarenpc.ai.vehicle.ShipSteering;
import io.github.capttracker.ancientwarfarenpc.caravan.CaravanState.Phase;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFaction;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionTrader;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementData;
import io.github.capttracker.ancientwarfarenpc.trade.SeaDocks;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipRoutePlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The behaviour of a faction trade ship, run by its leader (the trader, {@link NpcFactionTrader#tick}) like a caravan by land ({@link CaravanLogic}), with the same
 * phases: APPROACH sails from the sea to the mooring at the dock, TRADING holds the ship there while the trader sells (the players come aboard or alongside and use the
 * normal trade view of the faction trader; the buy orders of the stalls are filled out of the hold in the same all-or-nothing way) and LEAVING sails back out to sea,
 * where the whole ship vanishes once no player is near.
 *
 * <p>The ship is sailed by its helmsman (a faction soldier at the helm): the leader plans the route ({@link ShipRoutePlanner}, the planner of the fishing dock and of
 * the player's sea routes) and steers with the same steering as the owned helmsmen ({@link ShipSteering}). With no helmsman alive aboard the ship cannot sail: it
 * lies where it is until the caravan's time runs out. A route that cannot be found or a ship that gets stuck three times ends the visit: the ship turns back.
 * If the ship sinks the crew is freed and the caravan is over; killing the trader follows the caravan rules ({@link CaravanLogic#onLeaderKilled}: only the coins
 * it earned are dropped).
 */
public final class SeaCaravanLogic {
    /** Ticks a trade ship may take to reach the dock before it turns back. */
    private static final long APPROACH_LIMIT = 12000L;
    /** Ticks a trade ship may be about at all before it is sent away. */
    private static final long MAX_LIFE = 48000L;
    /** Ticks a leaving ship sails before it vanishes even in view of a player. */
    private static final long LEAVE_LIMIT = 12000L;
    private static final int MAX_PLAN_FAILURES = 3;
    private static final long PLAN_RETRY_TICKS = 100L;

    private SeaCaravanLogic() {}

    /** Called every tick by the leader while it has a trade ship. {@code ship} is null if it is not loaded or is gone. */
    static void tick(NpcFactionTrader leader, CaravanState state, ServerLevel level, @Nullable VehicleBase ship) {
        long now = level.getGameTime();
        boolean sunk = ship == null || ship.isRemoved() || ship.ship == null || ship.ship.isSinking();
        if (sunk) {
            if (leader.tickCount % 20 == 0 && leader.tickCount > 100 && leader.getVehicle() == null) { // not at once after a load: the riders attach with the ship
                abandon(leader, state, level);
            }
            return;
        }
        Optional<Settlement> settlement = Optional.empty();
        if (leader.tickCount % 20 == 0) {
            settlement = SettlementData.get(level).byId(state.settlement);
            if (state.phase != Phase.LEAVING && (settlement.isEmpty() || now - state.startTick > MAX_LIFE)) {
                leave(state, now);
            }
            if (state.phase == Phase.APPROACH && now - state.phaseTick > APPROACH_LIMIT) {
                leave(state, now);
            }
        }
        NpcFaction helm = helmOf(level, state, ship);
        switch (state.phase) {
            case APPROACH -> {
                if (steer(level, state, ship, helm, state.target, now) == ShipSteering.Result.ARRIVED) {
                    state.setPhase(Phase.TRADING, now);
                    state.stayUntil = now + SettlementConfig.seaCaravanStayTicks;
                    state.route = List.of();
                    state.routeGoal = null;
                    settlement = SettlementData.get(level).byId(state.settlement);
                    settlement.ifPresent(s -> CaravanLogic.announce(level, s, leader, "message.ancientwarfarenpc.caravan.ship_arrived"));
                }
            }
            case TRADING -> {
                if (leader.tickCount % 10 == 0 && ship.ship != null) {
                    ship.ship.setPilot(ship.position(), 1.5D, true); // hold the ship at the dock (the autopilot lapses after 2 seconds)
                }
                if (leader.tickCount % 60 == 0) {
                    SettlementData.get(level).byId(state.settlement).ifPresent(s -> CaravanLogic.fillBuyOrders(leader, level, s, ship, state,
                            (double) SettlementConfig.seaCaravanOrderRange * SettlementConfig.seaCaravanOrderRange));
                }
                if (leader.tickCount % 20 == 0 && now >= state.stayUntil) {
                    leave(state, now);
                }
            }
            case LEAVING -> {
                steer(level, state, ship, helm, state.exit, now);
                if (leader.tickCount % 20 == 0) {
                    boolean unseen = level.getNearestPlayer(leader, SettlementConfig.caravanDespawnRange) == null;
                    if (unseen || now - state.phaseTick > LEAVE_LIMIT) {
                        CaravanLogic.despawn(leader, level, state);
                    }
                }
            }
        }
    }

    /** The visit is over: the ship turns back out to sea. */
    private static void leave(CaravanState state, long now) {
        state.setPhase(Phase.LEAVING, now);
        state.route = List.of();
        state.routeGoal = null;
        state.planFailures = 0;
        state.nextPlan = 0L;
        state.steering.reset();
    }

    /** The helmsman: the faction soldier at the helm of the ship, if it is alive; null if there is none. */
    @Nullable
    private static NpcFaction helmOf(ServerLevel level, CaravanState state, VehicleBase ship) {
        Entity helm = ship.getControllingPassenger();
        if (helm instanceof NpcFaction npc && npc.isAlive() && (state.helmsman == null || state.helmsman.equals(npc.getUUID()))) {
            return npc;
        }
        return null;
    }

    /**
     * Sails the ship towards a goal on the water: plans the route when there is none (and again, after a pause, when there is none to be found), and steers it
     * through the waypoints. A ship with no helmsman alive does not sail. {@code ARRIVED} when the ship has stopped at the goal. Three failed plans or stuck
     * spells in the approach end the visit.
     */
    private static ShipSteering.Result steer(ServerLevel level, CaravanState state, VehicleBase ship, @Nullable NpcFaction helm, BlockPos goal, long now) {
        if (helm == null || ship.ship == null || !ship.ship.hasSpec()) {
            return ShipSteering.Result.SAILING;
        }
        if (state.routeGoal == null || !state.routeGoal.equals(goal)) {
            state.route = List.of();
            state.routeGoal = goal;
            state.planFailures = 0;
            state.nextPlan = 0L;
            state.steering.reset();
        }
        if (state.route.isEmpty() && now >= state.nextPlan && state.planFailures < MAX_PLAN_FAILURES) {
            ShipRoutePlanner.Plan plan = ShipRoutePlanner.planBetween(level, ship.position(), goal, ship.blockPosition().getY(), SeaDocks.clearanceOf(ship), false);
            if (plan != null) {
                state.route = plan.route();
                state.planFailures = 0;
            } else {
                state.planFailures++;
                state.nextPlan = now + PLAN_RETRY_TICKS;
                if (state.planFailures >= MAX_PLAN_FAILURES && state.phase == Phase.APPROACH) {
                    leave(state, now); // no way to the dock: turn back
                    return ShipSteering.Result.SAILING;
                }
            }
        }
        Vec3 target = new Vec3(goal.getX() + 0.5D, ship.getY(), goal.getZ() + 0.5D);
        double arrive = 2.0D + ship.ship.spec().length() * 0.3D;
        ShipSteering.Result result = state.steering.tick(ship, target, state.route.isEmpty() ? null : state.route, arrive, false, 10);
        if (result == ShipSteering.Result.STUCK) {
            state.route = List.of();
            state.planFailures++;
            state.nextPlan = now + PLAN_RETRY_TICKS;
            if (state.planFailures >= MAX_PLAN_FAILURES && state.phase == Phase.APPROACH) {
                leave(state, now); // stuck on the way to the dock: turn back
            }
            return ShipSteering.Result.SAILING;
        }
        return result;
    }

    /** The ship sank or is gone and the leader is not aboard: the crew is freed and the trade ship is over. */
    private static void abandon(NpcFactionTrader leader, CaravanState state, ServerLevel level) {
        for (UUID id : state.guards) {
            if (level.getEntity(id) instanceof NpcFaction member) {
                member.setCaravanLeader(null);
                member.setCanDespawn();
            }
        }
        CaravanLogic.finish(level, state, leader);
        leader.setCaravan(null);
        leader.setCanDespawn();
    }
}
