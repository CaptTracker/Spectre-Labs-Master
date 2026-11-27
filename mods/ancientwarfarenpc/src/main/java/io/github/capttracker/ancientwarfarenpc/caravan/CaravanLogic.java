/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): what the leader of a caravan does each tick.
 */
package io.github.capttracker.ancientwarfarenpc.caravan;

import io.github.capttracker.ancientwarfare.api.IMarketStall;
import io.github.capttracker.ancientwarfare.api.IMarketStall.Fill;
import io.github.capttracker.ancientwarfarenpc.caravan.CaravanState.Phase;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFaction;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionTrader;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementApi;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementData;
import io.github.capttracker.ancientwarfarenpc.trade.MarketApi;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The caravan's behaviour, run by its leader ({@link NpcFactionTrader#tick}) on the server: the wagon follows the leader, the phases change
 * (approach, trading, leaving), the stall buy orders are filled from the wagon, and the caravan vanishes when it has left. Killing the leader drops the coins the caravan earned (goods for buy orders are made only at the trade).
 *
 * <p>Rework phase 11: a trade ship ({@link CaravanState#ship}) is the same caravan with a cog for the wagon; its sailing is {@link SeaCaravanLogic}, and it shares the
 * buy order sales ({@link #fillBuyOrders}), the despawn and the kill rules (the cargo is dropped by the ship's own inventory rules, coins only) with the one by land.
 */
public final class CaravanLogic {
    /** Ticks a caravan may take to reach the settlement before it gives up. */
    private static final long APPROACH_LIMIT = 9000L;
    /** Ticks a caravan may be on the road at all before it is sent away (two and a half days). */
    private static final long MAX_LIFE = 60000L;
    /** Ticks a leaving caravan walks before it vanishes even in view of a player. */
    private static final long LEAVE_LIMIT = 12000L;
    /** The horizontal distance (blocks) at which the leader counts as arrived. */
    private static final double ARRIVED_SQ = 36.0D;
    private static final double ORDER_RANGE_SQ = 24.0D * 24.0D;
    private static final double CARGO_RANGE_SQ = 16.0D * 16.0D;

    private CaravanLogic() {}

    // ---------------------------------------------------------------- lookups

    @Nullable
    public static VehicleBase wagonOf(ServerLevel level, CaravanState state) {
        if (state.wagon != null && level.getEntity(state.wagon) instanceof VehicleBase wagon && !wagon.isRemoved()) {
            return wagon;
        }
        return null;
    }

    // ---------------------------------------------------------------- per tick

    /** Called every tick by the leader while it has a caravan. */
    public static void tick(NpcFactionTrader leader, CaravanState state) {
        if (!(leader.level() instanceof ServerLevel level)) {
            return;
        }
        VehicleBase wagon = wagonOf(level, state);
        if (state.ship) {
            SeaCaravanLogic.tick(leader, state, level, wagon);
            return;
        }
        if (wagon != null && wagon.hitch.isHitched()) {
            // the leader is not saved on the hitch, so it is set every tick
            wagon.hitch.setLeader(leader, 3.0D);
        }
        if (leader.tickCount % 20 != 0) {
            return;
        }
        long now = level.getGameTime();
        Optional<Settlement> settlement = SettlementData.get(level).byId(state.settlement);
        if (state.phase != Phase.LEAVING && (settlement.isEmpty() || now - state.startTick > MAX_LIFE)) {
            state.setPhase(Phase.LEAVING, now);
        }
        switch (state.phase) {
            case APPROACH -> {
                double dx = leader.getX() - (state.target.getX() + 0.5D);
                double dz = leader.getZ() - (state.target.getZ() + 0.5D);
                if (dx * dx + dz * dz < ARRIVED_SQ) {
                    state.setPhase(Phase.TRADING, now);
                    state.stayUntil = now + SettlementConfig.caravanStayTicks;
                    settlement.ifPresent(s -> announce(level, s, leader));
                } else if (now - state.phaseTick > APPROACH_LIMIT) {
                    state.setPhase(Phase.LEAVING, now);
                }
            }
            case TRADING -> {
                if (leader.tickCount % 60 == 0 && wagon != null && settlement.isPresent()) {
                    fillBuyOrders(leader, level, settlement.get(), wagon, state, ORDER_RANGE_SQ);
                }
                if (leader.tickCount % 100 == 0 && settlement.isPresent()) {
                    CaravanGuests.rentBeds(level, settlement.get(), leader, state);
                }
                if (now >= state.stayUntil) {
                    state.setPhase(Phase.LEAVING, now);
                }
            }
            case LEAVING -> {
                boolean unseen = level.getNearestPlayer(leader, SettlementConfig.caravanDespawnRange) == null;
                if (unseen || now - state.phaseTick > LEAVE_LIMIT) {
                    despawn(leader, level, state);
                }
            }
        }
    }

    /** Tells the players who run the settlement that the caravan has come. */
    private static void announce(ServerLevel level, Settlement settlement, NpcFactionTrader leader) {
        announce(level, settlement, leader, "message.ancientwarfarenpc.caravan.arrived");
    }

    /** As {@link #announce(ServerLevel, Settlement, NpcFactionTrader)} with the message of your choice (a trade ship has its own). */
    static void announce(ServerLevel level, Settlement settlement, NpcFactionTrader leader, String key) {
        String faction = leader.getFaction() == null ? "" : leader.getFaction();
        Component name = Component.literal(faction.isEmpty() ? faction : faction.substring(0, 1).toUpperCase(Locale.ROOT) + faction.substring(1));
        Component message = Component.translatable(key, name, settlement.getName());
        for (ServerPlayer player : level.players()) {
            if (SettlementApi.mayManage(settlement, player) && player.distanceToSqr(leader) < 200.0D * 200.0D) {
                player.sendSystemMessage(message);
            }
        }
    }

    /** Most items of one kind a caravan supplies to buy orders on one visit (two stacks). */
    private static final int SUPPLY_STACKS = 2;

    /**
     * Sells goods to the buy orders of the settlement's stalls, for coins that go into the wagon. The goods are made only now, for the one deal: they are put
     * into a scratch inventory next to room for the coins, the stall does its all-or-nothing trade with that inventory, and then the coins move into the wagon
     * and what is left of the goods is thrown away. Goods that were never paid for never exist as items in the wagon, so a killed caravan cannot drop them.
     */
    static void fillBuyOrders(NpcFactionTrader leader, ServerLevel level, Settlement settlement, VehicleBase wagon, CaravanState state, double orderRangeSq) {
        if (leader.distanceToSqr(wagon) > CARGO_RANGE_SQ) {
            return;
        }
        IItemHandlerModifiable cargo = wagon.inventory.storageInventory;
        if (!state.virtualCargo) {
            // a caravan from before the change: its wagon may hold goods, sell those as before
            for (MarketApi.PostedOrder posted : MarketApi.listBuyOrders(level, settlement)) {
                if (inRange(leader, posted.stall(), orderRangeSq)
                        && MarketApi.fillBuyOrder(posted.stall(), posted.order().index(), Math.max(1, posted.order().deals()), cargo).deals() > 0) {
                    leader.playSound(net.minecraft.sounds.SoundEvents.VILLAGER_TRADE, 1.0F, 1.0F);
                }
            }
            return;
        }
        for (MarketApi.PostedOrder posted : MarketApi.listBuyOrders(level, settlement)) {
            IMarketStall stall = posted.stall();
            ItemStack item = posted.order().item();
            ItemStack price = posted.order().price();
            if (!inRange(leader, stall, orderRangeSq) || item.isEmpty() || price.isEmpty() || item.getCount() < 1) {
                continue;
            }
            String id = BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
            if (!state.supply.contains(id) || CaravanStock.isCoin(item)) {
                continue;
            }
            int perDeal = item.getCount();
            int budget = item.getMaxStackSize() * SUPPLY_STACKS - state.supplied.getOrDefault(id, 0);
            int deals = Math.min(Math.max(1, posted.order().deals()), budget / perDeal);
            if (posted.order().remaining() >= 0) {
                deals = Math.min(deals, posted.order().remaining() / perDeal);
            }
            // the wagon must be able to take the whole payment, or nothing is made
            while (deals > 0 && !fits(cargo, price.copyWithCount(price.getCount() * deals))) {
                deals--;
            }
            if (deals < 1) {
                continue;
            }
            // scratch inventory: the goods for the deals, and room for the coins (the stall puts them back into the seller's inventory)
            int goodsSlots = (perDeal * deals + item.getMaxStackSize() - 1) / item.getMaxStackSize();
            int coinSlots = (price.getCount() * deals + Math.max(1, price.getMaxStackSize()) - 1) / Math.max(1, price.getMaxStackSize());
            ItemStackHandler scratch = new ItemStackHandler(goodsSlots + coinSlots + 1);
            ItemStack goods = item.copyWithCount(perDeal * deals);
            ItemStack leftover = ItemHandlerHelper.insertItem(scratch, goods, false);
            if (!leftover.isEmpty()) {
                continue;
            }
            Fill fill = MarketApi.fillBuyOrder(stall, posted.order().index(), deals, scratch);
            if (fill.deals() > 0) {
                state.supplied.merge(id, perDeal * fill.deals(), Integer::sum);
                // the coins (and nothing else) go into the wagon; the unsold goods are dropped from existence
                int total = price.getCount() * fill.deals();
                ItemStack earned = ItemHandlerHelper.insertItem(cargo, price.copyWithCount(total), false);
                if (!earned.isEmpty()) {
                    net.minecraft.world.Containers.dropItemStack(level, wagon.getX(), wagon.getY() + 0.5D, wagon.getZ(), earned);
                }
                leader.playSound(net.minecraft.sounds.SoundEvents.VILLAGER_TRADE, 1.0F, 1.0F);
            }
        }
    }

    private static boolean inRange(NpcFactionTrader leader, IMarketStall stall, double rangeSq) {
        return leader.distanceToSqr(stall.getStallPos().getX() + 0.5D, stall.getStallPos().getY(), stall.getStallPos().getZ() + 0.5D) <= rangeSq;
    }

    /** True when the whole stack can be put into the handler. */
    private static boolean fits(IItemHandler handler, ItemStack stack) {
        return ItemHandlerHelper.insertItem(handler, stack, true).isEmpty();
    }

    // ---------------------------------------------------------------- ending

    /** The caravan has left: the leader, the guards, the horses and the wagon vanish (what is left in the wagon goes with it). */
    public static void despawn(NpcFactionTrader leader, ServerLevel level, CaravanState state) {
        VehicleBase wagon = wagonOf(level, state);
        if (wagon != null && !state.ship) {
            wagon.hitch.unhitchAll(false);
        }
        for (UUID id : state.horses) {
            if (level.getEntity(id) instanceof AbstractHorse horse) {
                horse.discard();
            }
        }
        for (UUID id : state.guards) {
            Entity guard = level.getEntity(id);
            if (guard != null) {
                guard.discard();
            }
        }
        if (wagon != null) {
            // packed so that the vehicle does not drop its inventories as a destroyed one would
            wagon.discard();
        }
        finish(level, state, leader);
        leader.discard();
    }

    /**
     * The leader was killed: the cargo of the wagon is dropped where it stands, the horse is let go, the guards stop escorting and may despawn. The wagon
     * stays, empty, where it is.
     */
    public static void onLeaderKilled(NpcFactionTrader leader, CaravanState state) {
        if (!(leader.level() instanceof ServerLevel level)) {
            return;
        }
        VehicleBase wagon = wagonOf(level, state);
        if (wagon != null) {
            if (state.ship) {
                if (wagon.ship != null) {
                    wagon.ship.clearPilot(); // the crew is freed below: the ship runs down where it is
                }
                io.github.capttracker.ancientwarfarenpc.event.ShipPrizeEvents.markAbandoned(wagon); // a prize for the players, gone after a day if nobody takes it
            } else {
                wagon.hitch.setLeader(null, 3.0D);
                wagon.hitch.unhitchAll(false);
            }
            dropCargo(level, wagon, state.virtualCargo);
        }
        for (UUID id : state.guards) {
            if (level.getEntity(id) instanceof NpcFaction guard) {
                guard.setCaravanLeader(null);
                guard.setCanDespawn();
            }
        }
        finish(level, state, leader);
    }

    /**
     * Drops the cargo of the wagon at the wagon: the coins the caravan earned. A caravan of the new kind has nothing else in the wagon. A caravan saved before
     * the change ({@code allItems} false) may hold goods that were made for free, so only its coins are dropped and the rest is removed.
     */
    public static void dropCargo(ServerLevel level, VehicleBase wagon, boolean allItems) {
        IItemHandlerModifiable cargo = wagon.inventory.storageInventory;
        for (int slot = 0; slot < cargo.getSlots(); slot++) {
            ItemStack stack = cargo.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                ItemStack copy = stack.copy();
                cargo.setStackInSlot(slot, ItemStack.EMPTY);
                if (allItems || CaravanStock.isCoin(copy)) {
                    net.minecraft.world.Containers.dropItemStack(level, wagon.getX(), wagon.getY() + 0.5D, wagon.getZ(), copy);
                }
            }
        }
    }

    static void finish(ServerLevel level, CaravanState state, NpcFactionTrader leader) {
        CaravanData data = CaravanData.get(level.getServer());
        if (state.ship) {
            data.finishSea(leader.getUUID());
        } else {
            data.finish(state.settlement, leader.getUUID());
        }
    }

    /** The guards (loaded ones) of a caravan. */
    public static List<NpcFaction> guardsOf(ServerLevel level, CaravanState state) {
        List<NpcFaction> list = new java.util.ArrayList<>();
        for (UUID id : state.guards) {
            if (level.getEntity(id) instanceof NpcFaction guard) {
                list.add(guard);
            }
        }
        return list;
    }
}
