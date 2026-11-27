/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): market stalls, prices and trade reputation.
 */
package io.github.capttracker.ancientwarfarenpc.trade;

import io.github.capttracker.ancientwarfare.api.IMarketStall;
import io.github.capttracker.ancientwarfare.api.IMarketStall.BuyOrder;
import io.github.capttracker.ancientwarfare.api.IMarketStall.Fill;
import io.github.capttracker.ancientwarfare.api.IMarketStall.SellOffer;
import io.github.capttracker.ancientwarfare.api.IMarketStall.TradeResult;
import io.github.capttracker.ancientwarfare.api.IWorkSite;
import io.github.capttracker.ancientwarfare.api.WorksiteRegistry;
import io.github.capttracker.ancientwarfare.owner.Owner;
import io.github.capttracker.ancientwarfarenpc.entity.NpcTrader;
import io.github.capttracker.ancientwarfarenpc.settlement.JobBoard;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementData;
import io.github.capttracker.spectrefactions.api.ActorId;
import io.github.capttracker.spectrefactions.api.FactionsApi;
import io.github.capttracker.spectrefactions.api.Relation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The server side of the market (rework phase 5): finding the stalls of a settlement, the price multiplier of a relation, the player's purchase at a stall
 * and the reputation it earns. It is also the small API for the caravans of the next stage:
 * <ul>
 *   <li>{@link #stallsOf(ServerLevel, Settlement)}: the stalls of a settlement (open or not);</li>
 *   <li>{@link #listSellOffers(IMarketStall)}, {@link #listBuyOrders(IMarketStall)} and {@link #listBuyOrders(ServerLevel, Settlement)}: what the stalls sell
 *       and buy;</li>
 *   <li>{@link #fillBuyOrder}: a caravan sells to a buy order out of its own inventory and is paid in coins.</li>
 * </ul>
 * Prices follow {@link #priceMultiplier(Relation)}: HOSTILE refuses, NEUTRAL x1, FRIENDLY x{@code friendlyDiscount}, ALLIED x{@code alliedDiscount}
 * (the buyer's relation to the seller's actor; the same actor counts as allied). Each trade adds {@code tradeReputation} to the buyer's reputation with the
 * seller, at most {@code tradeReputationDailyCap} per in-game day per pair.
 */
public final class MarketApi {
    private MarketApi() {}

    // ---------------------------------------------------------------- prices and reputation

    /** The price factor for a relation; 1 for HOSTILE too (check {@link #refuses} first: a hostile buyer is not served). */
    public static double priceMultiplier(Relation relation) {
        return switch (relation) {
            case HOSTILE, NEUTRAL -> 1.0D;
            case FRIENDLY -> SettlementConfig.friendlyDiscount;
            case ALLIED -> SettlementConfig.alliedDiscount;
        };
    }

    public static boolean refuses(Relation relation) {
        return relation == Relation.HOSTILE;
    }

    /** The relation of a buyer to a seller (the same actor is allied). */
    public static Relation relationOf(Player buyer, ActorId seller) {
        return FactionsApi.getRelation(FactionsApi.actorOf(buyer.getUUID()), seller);
    }

    /** Adds the reputation of one trade within the daily cap. The same actor gains nothing. Returns what was added. */
    public static int awardTradeReputation(ServerLevel level, Player buyer, ActorId seller) {
        return awardTradeReputation(level, buyer, seller, SettlementConfig.tradeReputation);
    }

    /** As above with the amount wanted before the cap. */
    public static int awardTradeReputation(ServerLevel level, Player buyer, ActorId seller, int wanted) {
        ActorId buyerActor = FactionsApi.actorOf(buyer.getUUID());
        if (wanted <= 0 || buyerActor.equals(seller)) {
            return 0;
        }
        int grant = TradeReputationData.get(level).take(buyerActor, seller, wanted, SettlementConfig.tradeReputationDailyCap, level.getGameTime());
        if (grant > 0) {
            FactionsApi.adjustReputation(buyerActor, seller, grant, "trade");
        }
        return grant;
    }

    // ---------------------------------------------------------------- stalls

    /** The market stalls inside the land of the settlement that its owner may use (loaded ones), nearest to the hall first. */
    public static List<IMarketStall> stallsOf(ServerLevel level, Settlement settlement) {
        List<IMarketStall> result = new ArrayList<>();
        for (BlockEntity be : WorksiteRegistry.inLevel(level, settlement::contains)) {
            if (be instanceof IMarketStall stall && be instanceof IWorkSite site && JobBoard.mayUse(settlement, site)) {
                result.add(stall);
            }
        }
        BlockPos hall = settlement.getHallPos();
        result.sort(Comparator.comparingDouble(stall -> stall.getStallPos().distSqr(hall)));
        return result;
    }

    /** The stall a trader NPC stands at (the one that lists it as its trader), if any. */
    public static Optional<IMarketStall> stallOf(NpcTrader trader) {
        BlockPos pos = trader.getMarketStallPos();
        if (pos == null || !(trader.level() instanceof ServerLevel level) || !level.isLoaded(pos)) {
            return Optional.empty();
        }
        if (level.getBlockEntity(pos) instanceof IMarketStall stall && stall.getTrader().filter(id -> id.equals(trader.getUUID())).isPresent()) {
            return Optional.of(stall);
        }
        return Optional.empty();
    }

    /** The actor that sells at a stall: the owner of the block (the player or their faction), else the owner of the settlement around it. */
    public static Optional<ActorId> sellerOf(ServerLevel level, IMarketStall stall) {
        if (stall instanceof IWorkSite site) {
            Owner owner = site.getOwner();
            if (owner != null && owner != Owner.EMPTY) {
                return Optional.of(FactionsApi.actorOf(owner.getUUID()));
            }
        }
        for (Settlement settlement : SettlementData.get(level).all()) {
            if (settlement.getDimension().equals(level.dimension()) && settlement.contains(stall.getStallPos())) {
                return Optional.of(settlement.getOwnerActor());
            }
        }
        return Optional.empty();
    }

    public static List<SellOffer> listSellOffers(IMarketStall stall) {
        return stall.getSellOffers();
    }

    public static List<BuyOrder> listBuyOrders(IMarketStall stall) {
        return stall.getBuyOrders();
    }

    /** One buy order with the stall that posted it. */
    public record PostedOrder(IMarketStall stall, BuyOrder order) {}

    /** All buy orders of the stalls of the settlement that could be filled by at least one deal now. */
    public static List<PostedOrder> listBuyOrders(ServerLevel level, Settlement settlement) {
        List<PostedOrder> result = new ArrayList<>();
        for (IMarketStall stall : stallsOf(level, settlement)) {
            for (BuyOrder order : stall.getBuyOrders()) {
                if (order.deals() > 0) {
                    result.add(new PostedOrder(stall, order));
                }
            }
        }
        return result;
    }

    /** A caravan (or anyone) sells to a buy order: items out of {@code seller}, coins into it. Up to {@code deals} whole deals. */
    public static Fill fillBuyOrder(IMarketStall stall, int orderIndex, int deals, IItemHandler seller) {
        return stall.fillBuyOrder(orderIndex, deals, seller);
    }

    // ---------------------------------------------------------------- the player buys

    /** What a player pays for one deal of an offer: the coin stack with the multiplier of the player's relation applied; empty if the seller refuses. */
    public static ItemStack quote(ServerLevel level, Player buyer, IMarketStall stall, SellOffer offer) {
        Relation relation = sellerOf(level, stall).map(actor -> relationOf(buyer, actor)).orElse(Relation.NEUTRAL);
        if (refuses(relation)) {
            return ItemStack.EMPTY;
        }
        return offer.price().copyWithCount(IMarketStall.adjustPrice(offer.price().getCount(), priceMultiplier(relation)));
    }

    /**
     * A player buys {@code deals} times a sell offer from the trader at the stall. Validated here, on the server: the trader is alive, at the stall and
     * within reach of the buyer, the seller is not hostile, and the stall does the whole transaction or none of it. A successful trade earns reputation.
     */
    public static TradeResult buy(Player buyer, NpcTrader trader, IMarketStall stall, int offerIndex, int deals) {
        if (!(buyer.level() instanceof ServerLevel level) || !trader.isAlive() || buyer.distanceToSqr(trader) > 64.0D) {
            return TradeResult.CLOSED;
        }
        if (stall.getTrader().filter(id -> id.equals(trader.getUUID())).isEmpty()) {
            return TradeResult.CLOSED;
        }
        Optional<ActorId> seller = sellerOf(level, stall);
        Relation relation = seller.map(actor -> relationOf(buyer, actor)).orElse(Relation.NEUTRAL);
        if (refuses(relation)) {
            return TradeResult.REFUSED;
        }
        IItemHandler inventory = new PlayerMainInvWrapper(buyer.getInventory());
        TradeResult result = stall.sell(offerIndex, deals, priceMultiplier(relation), inventory);
        if (result == TradeResult.OK) {
            trader.gainSkillXp(io.github.capttracker.ancientwarfarenpc.skill.Skill.TRADING, SettlementConfig.skillXpTrade);
            seller.ifPresent(actor -> awardTradeReputation(level, buyer, actor));
        }
        return result;
    }
}
