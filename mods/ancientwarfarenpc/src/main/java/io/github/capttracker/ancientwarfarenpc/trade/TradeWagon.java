/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): player trade routes with a wagon.
 */
package io.github.capttracker.ancientwarfarenpc.trade;

import io.github.capttracker.ancientwarfare.owner.Owner;
import io.github.capttracker.ancientwarfarenpc.entity.NpcTrader;
import io.github.capttracker.ancientwarfarenpc.orders.TradeOrder;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementApi;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * The wagon of a player-owned trader that follows a trade route (rework phase 5). The trader takes a horse-drawn cart or wagon with a hitched horse that
 * stands within {@link #BIND_RANGE} blocks of it and belongs to its owner (or to nobody), when its trade order has a route of at least two points. The
 * horse follows the trader ({@code CartHitch#setLeader}); the trader waits when the wagon falls behind. At each stop that lies inside a settlement of the
 * owner's actor, the deposit and withdraw lists of the trade order are applied between the wagon's storage and the linked storages of that settlement (the
 * hall's inventory and the linked containers): what the deposit list names goes from the wagon into the storages, then what the withdraw list names comes
 * from the storages into the wagon.
 */
public final class TradeWagon {
    /** The search range for a wagon to take, in blocks. */
    public static final double BIND_RANGE = 12.0D;
    /** The wagon may be this far from the trader at a stop for the loading to happen, and this far behind while walking before the trader waits. */
    public static final double STOP_RANGE = 16.0D;
    public static final double WAIT_RANGE = 14.0D;
    /** Ticks the trader waits for a wagon that does not come before it leaves it. */
    public static final int PATIENCE_TICKS = 1200;

    private TradeWagon() {}

    /** The wagon the trader has taken, if it is loaded and still hitched to a horse. */
    @Nullable
    public static VehicleBase wagonOf(NpcTrader trader) {
        if (trader.getWagonId() != null && trader.level() instanceof ServerLevel level
                && level.getEntity(trader.getWagonId()) instanceof VehicleBase wagon && !wagon.isRemoved()) {
            return wagon;
        }
        return null;
    }

    /** Looks for a wagon to take (call every couple of seconds while the trader has none). Returns it, or null. */
    @Nullable
    public static VehicleBase bind(NpcTrader trader, @Nullable TradeOrder order) {
        if (order == null || order.getRoute().size() < 2 || !(trader.level() instanceof ServerLevel level)) {
            return null;
        }
        AABB area = trader.getBoundingBox().inflate(BIND_RANGE);
        List<VehicleBase> found = level.getEntitiesOfClass(VehicleBase.class, area, v -> mayTake(trader, v));
        VehicleBase best = null;
        for (VehicleBase wagon : found) {
            if (best == null || wagon.distanceToSqr(trader) < best.distanceToSqr(trader)) {
                best = wagon;
            }
        }
        if (best != null) {
            trader.setWagonId(best.getUUID());
        }
        return best;
    }

    private static boolean mayTake(NpcTrader trader, VehicleBase wagon) {
        if (!wagon.isAlive() || !wagon.vehicleType.isHorseDrawn() || !wagon.hitch.isHitched() || wagon.getControllingPassenger() != null) {
            return false;
        }
        if (wagon.hitch.getLeader() != null && wagon.hitch.getLeader() != trader && wagon.hitch.getLeader().isAlive()) {
            return false;
        }
        Owner vehicleOwner = wagon.getOwner();
        Owner traderOwner = trader.getOwner();
        return vehicleOwner == null || vehicleOwner == Owner.EMPTY || traderOwner == null || traderOwner == Owner.EMPTY
                || vehicleOwner.getUUID().equals(traderOwner.getUUID());
    }

    /** Lets go of the wagon (the horse stops following). */
    public static void release(NpcTrader trader) {
        VehicleBase wagon = wagonOf(trader);
        if (wagon != null && wagon.hitch.getLeader() == trader) {
            wagon.hitch.setLeader(null, 3.0D);
        }
        trader.setWagonId(null);
    }

    /** Called every tick the trader walks its route: keeps the horse following. Returns false if the wagon has no horse any more (it is let go). */
    public static boolean follow(NpcTrader trader, VehicleBase wagon) {
        if (!wagon.hitch.isHitched()) {
            release(trader);
            return false;
        }
        wagon.hitch.setLeader(trader, 3.0D);
        return true;
    }

    /** Applies the trade order's deposit and withdraw lists at a stop. Does nothing when the stop is not inside a settlement of the trader's owner. */
    public static void loadAndUnload(NpcTrader trader, VehicleBase wagon, BlockPos stop, TradeOrder order) {
        if (!SettlementConfig.tradeRouteLoading || !(trader.level() instanceof ServerLevel level)) {
            return;
        }
        var restock = order.getRestockData();
        if (restock.getDepositList().isEmpty() && restock.getWithdrawList().isEmpty()) {
            return;
        }
        if (wagon.distanceToSqr(stop.getX() + 0.5D, stop.getY(), stop.getZ() + 0.5D) > STOP_RANGE * STOP_RANGE) {
            return;
        }
        Optional<Settlement> settlement = trader.getFactionActor().flatMap(actor -> SettlementApi.findByPosition(level, stop, actor));
        if (settlement.isEmpty()) {
            return;
        }
        IItemHandler cargo = wagon.inventory.storageInventory;
        List<SettlementApi.StorageHandle> storages = SettlementApi.allStorages(level, settlement.get());
        for (SettlementApi.StorageHandle storage : storages) {
            restock.doDeposit(cargo, storage.handler());
        }
        for (SettlementApi.StorageHandle storage : storages) {
            restock.doWithdraw(cargo, storage.handler());
        }
    }
}
