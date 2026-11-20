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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The caravan's behaviour, run by its leader ({@link NpcFactionTrader#tick}) on the server: the wagon follows the leader, the phases change
 * (approach, trading, leaving), the stall buy orders are filled from the wagon, and the caravan vanishes when it has left. Killing the leader drops the cargo.
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
                    fillBuyOrders(leader, level, settlement.get(), wagon);
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
        String faction = leader.getFaction() == null ? "" : leader.getFaction();
        Component name = Component.literal(faction.isEmpty() ? faction : faction.substring(0, 1).toUpperCase(Locale.ROOT) + faction.substring(1));
        Component message = Component.translatable("message.ancientwarfarenpc.caravan.arrived", name, settlement.getName());
        for (ServerPlayer player : level.players()) {
            if (SettlementApi.mayManage(settlement, player) && player.distanceToSqr(leader) < 200.0D * 200.0D) {
                player.sendSystemMessage(message);
            }
        }
    }

    /** Sells goods from the wagon to the buy orders of the settlement's stalls, for coins that go into the wagon. */
    private static void fillBuyOrders(NpcFactionTrader leader, ServerLevel level, Settlement settlement, VehicleBase wagon) {
        if (leader.distanceToSqr(wagon) > CARGO_RANGE_SQ) {
            return;
        }
        IItemHandler cargo = wagon.inventory.storageInventory;
        for (MarketApi.PostedOrder posted : MarketApi.listBuyOrders(level, settlement)) {
            IMarketStall stall = posted.stall();
            if (leader.distanceToSqr(stall.getStallPos().getX() + 0.5D, stall.getStallPos().getY(), stall.getStallPos().getZ() + 0.5D) > ORDER_RANGE_SQ) {
                continue;
            }
            Fill fill = MarketApi.fillBuyOrder(stall, posted.order().index(), Math.max(1, posted.order().deals()), cargo);
            if (fill.deals() > 0) {
                leader.playSound(net.minecraft.sounds.SoundEvents.VILLAGER_TRADE, 1.0F, 1.0F);
            }
        }
    }

    // ---------------------------------------------------------------- ending

    /** The caravan has left: the leader, the guards, the horses and the wagon vanish (what is left in the wagon goes with it). */
    public static void despawn(NpcFactionTrader leader, ServerLevel level, CaravanState state) {
        VehicleBase wagon = wagonOf(level, state);
        if (wagon != null) {
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
            wagon.hitch.setLeader(null, 3.0D);
            wagon.hitch.unhitchAll(false);
            dropCargo(level, wagon);
        }
        for (UUID id : state.guards) {
            if (level.getEntity(id) instanceof NpcFaction guard) {
                guard.setCaravanLeader(null);
                guard.setCanDespawn();
            }
        }
        finish(level, state, leader);
    }

    /** Drops everything in the wagon's storage at the wagon. */
    public static void dropCargo(ServerLevel level, VehicleBase wagon) {
        IItemHandlerModifiable cargo = wagon.inventory.storageInventory;
        for (int slot = 0; slot < cargo.getSlots(); slot++) {
            ItemStack stack = cargo.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                ItemStack copy = stack.copy();
                cargo.setStackInSlot(slot, ItemStack.EMPTY);
                net.minecraft.world.Containers.dropItemStack(level, wagon.getX(), wagon.getY() + 0.5D, wagon.getZ(), copy);
            }
        }
    }

    private static void finish(ServerLevel level, CaravanState state, NpcFactionTrader leader) {
        CaravanData.get(level.getServer()).finish(state.settlement, leader.getUUID());
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
