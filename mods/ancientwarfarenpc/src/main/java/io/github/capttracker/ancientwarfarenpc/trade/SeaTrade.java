/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 11): the sea trade routes of the player, a trader that sails a cargo ship from dock to dock.
 */
package io.github.capttracker.ancientwarfarenpc.trade;

import io.github.capttracker.ancientwarfare.api.ISeaDock;
import io.github.capttracker.ancientwarfare.owner.Owner;
import io.github.capttracker.ancientwarfarenpc.ai.vehicle.ShipApproach;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.entity.NpcTrader;
import io.github.capttracker.ancientwarfarenpc.orders.SeaRoute;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementApi;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.trade.SeaTradeState.Phase;
import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipRoutePlanner;
import io.github.capttracker.spectrefactions.api.FactionsApi;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The sea trade routes of the player (rework phase 11), the cargo ship's version of the trader's wagon route ({@link TradeWagon}). An owned {@link NpcTrader}
 * that holds a {@link SeaRoute} sea route chart in its orders slot takes a cargo ship (a cog of its owner, within {@code sea_trade_bind_range} blocks, with a free
 * helm or an owned helmsman) and sails it from dock to dock, round again, as a state machine ({@link SeaTradeState}):
 * <ul>
 *   <li><b>IDLE</b>: looks for a ship every 2 seconds. <b>BOARDING</b>: walks or swims to the helm ({@code NpcAISeaTrade}, {@link ShipApproach}).</li>
 *   <li><b>SAILING</b>: plans the leg over the water ({@link ShipRoutePlanner}: the planner of the fishing dock, for the hull of the ship) from the ship to the
 *       mooring of the next dock and gives the waypoints to the helmsman, who is the trader itself or an owned NPC at the helm ({@code NpcAIShipHelm}); the helmsman
 *       steers, looks ahead and gives up when stuck. Three failed tries at a stop give the stop up and the owner is told.</li>
 *   <li><b>MOORED</b>: unloads and loads by the rules of the stop between the cargo hold of the ship and the storage of the dock (and, if the dock lies in a
 *       settlement of the owner, the linked storages of the settlement), waits the time of the stop, and sails on.</li>
 * </ul>
 * A dock that is not loaded cannot trade: the ship sails to the last point of loaded water that is nearest to it, waits there ({@code sea_trade_dock_wait_ticks})
 * and tries again. A ship that is destroyed or sinks is forgotten and the owner is told; the route is kept and the trader takes the next ship it finds.
 */
public final class SeaTrade {
    private static final int CHECK = 10;
    private static final int LOST_TICKS = 200;
    private static final int MAX_FAILURES = 3;
    /** A ship needs this many slots of cargo space to be a cargo ship. */
    private static final int MIN_CARGO_SLOTS = 27;

    private SeaTrade() {}

    // ---------------------------------------------------------------- the ship

    /** The ship of the state if it is loaded and afloat; null otherwise. Does not change the state. */
    @Nullable
    public static VehicleBase shipOf(ServerLevel level, SeaTradeState state) {
        if (state.ship != null && level.getEntity(state.ship) instanceof VehicleBase ship && ship.isAlive() && ship.isShip() && !ship.ship.isSinking()) {
            return ship;
        }
        return null;
    }

    /** The roles the trader asks for at the ship: the helm if it is free, else a seat in the crew (the ship has an owned helmsman). */
    public static List<SeatRole> boardingRoles(VehicleBase ship) {
        return ship.hasFreeSeat(SeatRole.DRIVER) ? List.of(SeatRole.DRIVER) : List.of(SeatRole.PASSENGER, SeatRole.ROWER);
    }

    private static boolean sameOwner(NpcPlayerOwned npc, NpcTrader trader) {
        UUID a = npc.getOwner().getUUID();
        UUID b = trader.getOwner().getUUID();
        return a.equals(b) || FactionsApi.actorOf(a).equals(FactionsApi.actorOf(b));
    }

    /** True if the ship's owner is nobody or the trader's owner (or of the same actor). */
    private static boolean ownedBy(VehicleBase ship, NpcTrader trader) {
        Owner vehicleOwner = ship.getOwner();
        Owner traderOwner = trader.getOwner();
        if (vehicleOwner == null || vehicleOwner == Owner.EMPTY) {
            return true;
        }
        if (traderOwner == null || traderOwner == Owner.EMPTY) {
            return false;
        }
        UUID a = vehicleOwner.getUUID();
        UUID b = traderOwner.getUUID();
        return a.equals(b) || FactionsApi.actorOf(a).equals(FactionsApi.actorOf(b));
    }

    /** The NPC that steers the ship: the trader at the helm, or an owned helmsman of the same owner; null if the helm is empty or a player or a stranger sits there. */
    @Nullable
    private static NpcPlayerOwned captainOf(NpcTrader trader, VehicleBase ship) {
        Entity helm = ship.getControllingPassenger();
        if (helm == trader) {
            return trader;
        }
        return helm instanceof NpcPlayerOwned owned && sameOwner(owned, trader) ? owned : null;
    }

    /** True if the trader may take the ship: a cargo ship that is afloat, of the owner or nobody, with a free helm or an owned helmsman, that no other trader has. */
    private static boolean mayTake(NpcTrader trader, VehicleBase ship, ServerLevel level) {
        if (!ship.isAlive() || !ship.isShip() || ship.ship.isSinking() || ship.inventory.storageInventory.getSlots() < MIN_CARGO_SLOTS || !ownedBy(ship, trader)) {
            return false;
        }
        Entity helm = ship.getControllingPassenger();
        if (helm != null && helm != trader && !(helm instanceof NpcPlayerOwned owned && sameOwner(owned, trader))) {
            return false;
        }
        if (helm == null && !ship.hasFreeSeat(SeatRole.DRIVER)) {
            return false;
        }
        for (NpcTrader other : level.getEntitiesOfClass(NpcTrader.class, ship.getBoundingBox().inflate(64.0D), t -> t != trader && t.isAlive())) {
            if (ship.getUUID().equals(other.getSeaState().ship)) {
                return false;
            }
        }
        return true;
    }

    /** Looks for a cargo ship to take within {@code sea_trade_bind_range} blocks; the nearest. */
    @Nullable
    private static VehicleBase bind(NpcTrader trader, ServerLevel level) {
        AABB area = trader.getBoundingBox().inflate(SettlementConfig.seaTradeBindRange);
        VehicleBase best = null;
        for (VehicleBase ship : level.getEntitiesOfClass(VehicleBase.class, area, v -> mayTake(trader, v, level))) {
            if (best == null || ship.distanceToSqr(trader) < best.distanceToSqr(trader)) {
                best = ship;
            }
        }
        return best;
    }

    /** Lets go of the ship and the route (the chart was taken out of the orders slot): the trader goes back to being idle. */
    public static void release(NpcTrader trader) {
        SeaTradeState state = trader.getSeaState();
        if (state.ship != null && trader.level() instanceof ServerLevel level && state.order != null) {
            VehicleBase ship = shipOf(level, state);
            NpcPlayerOwned captain = ship == null ? null : captainOf(trader, ship);
            if (captain != null) {
                captain.clearSailOrder();
            }
        }
        state.dropShip();
    }

    // ---------------------------------------------------------------- the state machine

    /** Called every tick by the trader on the server; does its work every 10 ticks. */
    public static void tick(NpcTrader trader) {
        if (!(trader.level() instanceof ServerLevel level) || !trader.isAlive()) {
            return;
        }
        SeaTradeState state = trader.getSeaState();
        SeaRoute route = trader.getSeaRoute();
        if (route == null || route.size() < 2) {
            if (state.hasShip() || state.phase != Phase.IDLE) {
                release(trader);
            }
            return;
        }
        if ((trader.tickCount + trader.getId()) % CHECK != 0 || !SettlementConfig.seaTradeEnabled) {
            return;
        }
        if (!AWNPCStatics.shipHelmsmen) {
            tell(trader, SeaTradeState.TOLD_DISABLED, "message.ancientwarfarenpc.sea_trade.no_helmsmen", trader.getDisplayName());
            return;
        }
        boolean cooling = state.cooldown > 0;
        if (cooling) {
            state.cooldown = Math.max(0, state.cooldown - CHECK);
        }
        if (state.stop >= route.size()) {
            state.stop = 0;
        }
        VehicleBase ship = resolveShip(trader, level, state);
        switch (state.phase) {
            case IDLE -> idle(trader, level, state, ship, cooling);
            case BOARDING -> boarding(trader, state, ship);
            case SAILING -> sailing(trader, level, state, route, ship, cooling);
            case MOORED -> moored(trader, state, route, ship);
        }
    }

    /** The ship of the state; a ship that is gone from a loaded place for 10 seconds, or is sinking, is lost (the owner is told). */
    @Nullable
    private static VehicleBase resolveShip(NpcTrader trader, ServerLevel level, SeaTradeState state) {
        if (state.ship == null) {
            return null;
        }
        if (level.getEntity(state.ship) instanceof VehicleBase ship && ship.isAlive() && ship.isShip()) {
            if (ship.ship.isSinking()) {
                lost(trader, state);
                return null;
            }
            state.shipPos = ship.blockPosition();
            state.missing = 0;
            return ship;
        }
        if (state.shipPos == null || level.isLoaded(state.shipPos)) {
            state.missing += CHECK;
            if (state.missing >= LOST_TICKS) {
                lost(trader, state);
            }
        }
        return null;
    }

    /** The ship was destroyed: the record of the route is kept, the owner is told, and the trader looks for another ship. */
    private static void lost(NpcTrader trader, SeaTradeState state) {
        if (trader.isPassenger() && trader.getVehicle() instanceof VehicleBase vehicle && vehicle.getUUID().equals(state.ship)) {
            trader.stopRiding();
        }
        trader.clearSailOrder();
        state.dropShip();
        state.cooldown = 200;
        tell(trader, SeaTradeState.TOLD_LOST, "message.ancientwarfarenpc.sea_trade.lost", trader.getDisplayName());
    }

    private static void idle(NpcTrader trader, ServerLevel level, SeaTradeState state, @Nullable VehicleBase ship, boolean cooling) {
        if (ship != null) {
            state.phase = trader.getVehicle() == ship ? Phase.SAILING : Phase.BOARDING;
            return;
        }
        if (cooling || state.hasShip() || (trader.tickCount / CHECK) % 4 != 0) {
            return;
        }
        VehicleBase found = bind(trader, level);
        if (found != null) {
            state.ship = found.getUUID();
            state.shipPos = found.blockPosition();
            state.phase = Phase.BOARDING;
            state.failures = 0;
            state.told &= ~(SeaTradeState.TOLD_LOST | SeaTradeState.TOLD_SEAT);
        }
    }

    private static void boarding(NpcTrader trader, SeaTradeState state, @Nullable VehicleBase ship) {
        if (ship == null) {
            if (!state.hasShip()) {
                state.phase = Phase.IDLE;
            }
            return;
        }
        if (trader.getVehicle() == ship) {
            state.phase = Phase.SAILING;
            state.legTicks = 0;
            state.order = null;
            state.failures = 0;
            state.told &= ~SeaTradeState.TOLD_SEAT;
        }
    }

    /** The boarding goal reports that the trader could not get a seat (no free one, or it did not get there): after three tries the ship is given up. */
    public static void boardingFailed(NpcTrader trader, ShipApproach.Status status) {
        SeaTradeState state = trader.getSeaState();
        state.failures++;
        if (status == ShipApproach.Status.NO_SEAT || state.failures >= MAX_FAILURES) {
            tell(trader, SeaTradeState.TOLD_SEAT, "message.ancientwarfarenpc.sea_trade.no_seat", trader.getDisplayName());
            state.dropShip();
            state.cooldown = 600;
        } else {
            state.cooldown = 100;
        }
    }

    private static void sailing(NpcTrader trader, ServerLevel level, SeaTradeState state, SeaRoute route, @Nullable VehicleBase ship, boolean cooling) {
        if (ship == null) {
            if (!state.hasShip()) {
                state.phase = Phase.IDLE;
            }
            return;
        }
        if (trader.getVehicle() != ship) {
            state.phase = Phase.BOARDING; // thrown off, or it got off: walk back to the helm
            state.order = null;
            return;
        }
        NpcPlayerOwned captain = captainOf(trader, ship);
        if (captain == null) {
            if (ship.getControllingPassenger() == null && ship.hasFreeSeat(SeatRole.DRIVER)) {
                ship.switchSeat(trader, 0); // the helm is empty: take it
            }
            return; // a player has the helm: the ship is his
        }
        if (cooling) {
            hold(ship);
            return;
        }
        SeaRoute.Stop stop = route.get(state.stop);
        BlockPos dockPos = stop.getDock();
        if (state.order == null && Math.hypot(dockPos.getX() + 0.5D - ship.getX(), dockPos.getZ() + 0.5D - ship.getZ()) > SettlementConfig.seaTradeMaxLeg) {
            skipStop(trader, state, route, SeaTradeState.TOLD_BLOCKED, "message.ancientwarfarenpc.sea_trade.too_far", dockPos);
            return;
        }
        if (!level.isLoaded(dockPos)) {
            waitForDock(trader, level, state, route, ship, captain, dockPos);
            return;
        }
        state.dockWait = 0;
        ISeaDock dock = SeaDocks.dockAt(level, dockPos);
        if (dock == null || !SeaDocks.mayUse(dock, trader.getOwner().getUUID())) {
            skipStop(trader, state, route, SeaTradeState.TOLD_DOCK, "message.ancientwarfarenpc.sea_trade.no_dock", dockPos);
            return;
        }
        if (state.order == null) {
            planLeg(trader, level, state, route, ship, captain, dock);
            return;
        }
        state.legTicks += CHECK;
        NpcPlayerOwned.SailResult result = captain.takeSailResult();
        if (result == NpcPlayerOwned.SailResult.ARRIVED) {
            state.order = null;
            if (Math.hypot(dockPos.getX() + 0.5D - ship.getX(), dockPos.getZ() + 0.5D - ship.getZ()) <= SettlementConfig.seaTradeMoorRange + 12.0D) {
                moor(trader, level, state, stop, ship, dock);
            } else {
                failed(trader, state, route, dockPos);
            }
        } else if (result == NpcPlayerOwned.SailResult.STUCK) {
            state.order = null;
            failed(trader, state, route, dockPos);
        } else if (captain.getSailTarget() == null) {
            state.order = null; // the order was cleared (the owner took the helm, a new helmsman): plan the leg again
        } else if (state.legTicks > SettlementConfig.seaTradeLegTicks) {
            captain.clearSailOrder();
            state.order = null;
            failed(trader, state, route, dockPos);
        }
    }

    /** Plans the leg from the ship to the mooring of the dock and gives it to the helmsman. */
    private static void planLeg(NpcTrader trader, ServerLevel level, SeaTradeState state, SeaRoute route, VehicleBase ship, NpcPlayerOwned captain, ISeaDock dock) {
        BlockPos mooring = SeaDocks.mooringOf(level, dock, ship);
        BlockPos goal = mooring != null ? mooring : dock.getBerth();
        int refY = ship.blockPosition().getY();
        ShipRoutePlanner.Plan plan = ShipRoutePlanner.planBetween(level, ship.position(), goal, refY, SeaDocks.clearanceOf(ship), false);
        if (plan == null) {
            failed(trader, state, route, dock.getDockPos());
            return;
        }
        captain.setSailRouteOrder(goal, plan.route());
        state.order = goal;
        state.legTicks = 0;
    }

    /** The dock is not loaded: sail to the last point of loaded water nearest to it, wait there, and ask again. */
    private static void waitForDock(NpcTrader trader, ServerLevel level, SeaTradeState state, SeaRoute route, VehicleBase ship, NpcPlayerOwned captain, BlockPos dockPos) {
        state.dockWait += CHECK;
        if (state.dockWait > SettlementConfig.seaTradeDockWaitTicks) {
            skipStop(trader, state, route, SeaTradeState.TOLD_DOCK, "message.ancientwarfarenpc.sea_trade.dock_unloaded", dockPos);
            return;
        }
        if (state.order != null) {
            NpcPlayerOwned.SailResult result = captain.takeSailResult();
            if (result != NpcPlayerOwned.SailResult.NONE || captain.getSailTarget() == null) {
                state.order = null;
            }
            return;
        }
        hold(ship);
        if (state.dockWait % 100 != CHECK % 100) {
            return;
        }
        ShipRoutePlanner.Plan plan = ShipRoutePlanner.planBetween(level, ship.position(), dockPos, ship.blockPosition().getY(), SeaDocks.clearanceOf(ship), true);
        if (plan != null && plan.length() > 8.0D) {
            Vec3 last = plan.route().get(plan.route().size() - 1);
            BlockPos target = BlockPos.containing(last.x, ship.getY(), last.z);
            captain.setSailRouteOrder(target, plan.route());
            state.order = target;
            trader.think("sea_waiting");
        }
    }

    /** The ship has come to the dock: unload and load, and wait there. */
    private static void moor(NpcTrader trader, ServerLevel level, SeaTradeState state, SeaRoute.Stop stop, VehicleBase ship, ISeaDock dock) {
        transfer(trader, level, stop, ship, dock);
        state.phase = Phase.MOORED;
        state.timer = 0;
        state.failures = 0;
        state.told &= ~(SeaTradeState.TOLD_BLOCKED | SeaTradeState.TOLD_DOCK);
        trader.think("sea_moored");
        hold(ship);
    }

    private static void moored(NpcTrader trader, SeaTradeState state, SeaRoute route, @Nullable VehicleBase ship) {
        if (ship == null) {
            if (!state.hasShip()) {
                state.phase = Phase.IDLE;
            }
            return;
        }
        if (trader.getVehicle() != ship) {
            state.phase = Phase.BOARDING;
            state.order = null;
            return;
        }
        hold(ship);
        state.timer += CHECK;
        if (state.timer >= route.get(state.stop).getWaitTicks()) {
            advance(state, route);
            state.phase = Phase.SAILING;
        }
    }

    /** Holds the moored ship where it is (the autopilot's stop mode; it lapses after 2 seconds, so this is repeated). */
    private static void hold(VehicleBase ship) {
        if (ship.ship != null) {
            ship.ship.setPilot(ship.position(), 1.5D, true);
        }
    }

    private static void advance(SeaTradeState state, SeaRoute route) {
        state.stop = (state.stop + 1) % route.size();
        state.timer = 0;
        state.legTicks = 0;
        state.failures = 0;
        state.dockWait = 0;
        state.order = null;
    }

    private static void failed(NpcTrader trader, SeaTradeState state, SeaRoute route, BlockPos dockPos) {
        state.failures++;
        state.legTicks = 0;
        if (state.failures >= MAX_FAILURES) {
            skipStop(trader, state, route, SeaTradeState.TOLD_BLOCKED, "message.ancientwarfarenpc.sea_trade.blocked", dockPos);
        } else {
            state.cooldown = 100 * state.failures;
            trader.think("sea_blocked");
        }
    }

    /** Gives the stop up for this round (a dock that is gone, a way that is blocked): the owner is told once and the ship sails on to the next stop. */
    private static void skipStop(NpcTrader trader, SeaTradeState state, SeaRoute route, int told, String key, BlockPos dockPos) {
        tell(trader, told, key, trader.getDisplayName(), dockPos.getX() + " " + dockPos.getY() + " " + dockPos.getZ());
        trader.think("sea_blocked");
        advance(state, route);
        state.cooldown = 60;
    }

    // ---------------------------------------------------------------- loading and unloading

    /**
     * Applies the rules of the stop between the cargo hold of the ship and the storages of the dock: the dock's own inventory first, then (if the dock lies in a
     * settlement of the trader's owner actor and {@code sea_trade_settlement_storage} is on) the linked storages of the settlement. What the stop unloads goes
     * out of the hold first, then what it loads comes in.
     */
    private static void transfer(NpcTrader trader, ServerLevel level, SeaRoute.Stop stop, VehicleBase ship, ISeaDock dock) {
        if (!stop.hasRules()) {
            return;
        }
        IItemHandler cargo = ship.inventory.storageInventory;
        List<IItemHandler> storages = new ArrayList<>();
        storages.add(dock.getDockStorage());
        if (SettlementConfig.seaTradeSettlementStorage) {
            Optional<Settlement> settlement = trader.getFactionActor().flatMap(actor -> SettlementApi.findByPosition(level, dock.getDockPos(), actor));
            if (settlement.isPresent()) {
                for (SettlementApi.StorageHandle storage : SettlementApi.allStorages(level, settlement.get())) {
                    if (!storages.contains(storage.handler())) {
                        storages.add(storage.handler());
                    }
                }
            }
        }
        for (IItemHandler storage : storages) {
            stop.unload(cargo, storage);
        }
        for (IItemHandler storage : storages) {
            stop.load(cargo, storage);
        }
    }

    // ---------------------------------------------------------------- telling the owner

    /** Tells the owner (if he is online) once until the bit is cleared again by a stop that went well. */
    private static void tell(NpcTrader trader, int bit, String key, Object... args) {
        SeaTradeState state = trader.getSeaState();
        if ((state.told & bit) != 0 || !(trader.level() instanceof ServerLevel level)) {
            return;
        }
        state.told |= bit;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(trader.getOwner().getUUID());
        if (player != null) {
            player.sendSystemMessage(Component.translatable(key, args));
        }
    }
}
