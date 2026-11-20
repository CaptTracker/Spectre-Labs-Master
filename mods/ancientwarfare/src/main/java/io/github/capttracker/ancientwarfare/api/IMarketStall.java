/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): the view of a market stall that the trader NPCs and the caravans use.
 */
package io.github.capttracker.ancientwarfare.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A market stall (the worksite block of the Automation module): a list of sell offers (an item, an amount and a price in coins), a list of buy orders,
 * a stock that couriers keep full and a till. It is open while a trader NPC stands at it. Implemented by the block entity; the NPC module finds stalls
 * through {@link WorksiteRegistry}. Server thread only.
 *
 * <p>All trades are done by the stall itself and are atomic per deal: either the whole deal (items one way, coins the other) happens or nothing does.
 * Prices are whole coin counts of one coin item; {@link #adjustPrice} scales one by a reputation multiplier.
 */
public interface IMarketStall {
    /** The most sell offers and buy orders a stall holds. */
    int MAX_SELL_OFFERS = 8;
    int MAX_BUY_ORDERS = 4;

    /** Why a trade did or did not happen. */
    enum TradeResult {
        OK,
        /** No trader stands at the stall. */
        CLOSED,
        /** The offer or order does not exist (or is not complete). */
        NO_SUCH_ENTRY,
        /** The stall has less stock than the deal needs. */
        NO_STOCK,
        /** The buyer has fewer coins than the price (or the seller of a buy order has fewer items). */
        NO_PAYMENT,
        /** The buyer's inventory cannot take the goods. */
        NO_ROOM,
        /** The stall's inventories cannot take the coins or the goods it is given. */
        STALL_FULL,
        /** A buy order: the stall has not the coins to pay. */
        NO_COINS,
        /** A buy order: the stall wants no more of the item. */
        LIMIT_REACHED,
        /** The trade was refused (hostile buyer). Never returned by the stall itself; used by the NPC side. */
        REFUSED
    }

    /**
     * One sell offer. {@code item} is what one deal gives (its count is the amount per deal), {@code price} what one deal costs (a coin stack at the
     * unscaled price), {@code stock} how many items of it the stall holds right now.
     */
    record SellOffer(int index, ItemStack item, ItemStack price, int stock) {
        /** How many deals the stock covers. */
        public int deals() {
            return item.isEmpty() ? 0 : stock / Math.max(1, item.getCount());
        }
    }

    /**
     * One buy order: the stall pays {@code price} for {@code item} (its count is the amount per deal). {@code remaining} is how many items the stall still
     * wants before it stops buying (-1: no limit), {@code deals} how many deals it could do right now (limited by the limit, its coins and its room).
     */
    record BuyOrder(int index, ItemStack item, ItemStack price, int remaining, int deals) {}

    /** The result of {@link #fillBuyOrder}: how many deals were done (0 unless the result is {@link TradeResult#OK}). */
    record Fill(TradeResult result, int deals) {}

    /** True while a trader NPC is at the stall (it has told the stall in the last 3 seconds). */
    boolean isOpen();

    /** The trader that stands at the stall, if it is open. */
    Optional<UUID> getTrader();

    /** The block of the stall. */
    BlockPos getStallPos();

    /** Where the trader stands: the block in front of the stall. */
    BlockPos getStandPos();

    /** The sell offers that are complete (an item and a price), with the stock they have. */
    List<SellOffer> getSellOffers();

    /** The buy orders that are complete. */
    List<BuyOrder> getBuyOrders();

    /**
     * A trader NPC reports in: it is at the stall now. Call at least every 2 seconds while it stays.
     *
     * @return false if another trader holds the stall (the caller must stay away)
     */
    boolean staff(UUID trader, long gameTime);

    /** The trader leaves the stall (it is closed at once). */
    void release(UUID trader);

    /**
     * The buyer buys {@code deals} times an offer. The price of one deal is scaled by {@code priceMultiplier} ({@link #adjustPrice}). The coins are taken
     * from {@code buyer}, the goods are put into it, the coins go into the stall's inventory. Nothing is changed unless the result is OK.
     *
     * @param offerIndex the {@link SellOffer#index()}
     */
    TradeResult sell(int offerIndex, int deals, double priceMultiplier, IItemHandler buyer);

    /**
     * A seller (a caravan) fills a buy order up to {@code deals} deals: the items are taken from {@code seller}, the stall's coins (its till first, then its
     * main inventory) are put into it. It does as many whole deals as the order, the seller, the stall's coins and room allow, at least one or nothing.
     *
     * @param orderIndex the {@link BuyOrder#index()}
     */
    Fill fillBuyOrder(int orderIndex, int deals, IItemHandler seller);

    /**
     * The price of one deal after the multiplier: {@code count} times the multiplier rounded to the nearest whole coin, half a coin in the buyer's favour,
     * never under 1.
     */
    static int adjustPrice(int count, double multiplier) {
        if (count <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(count * multiplier - 0.5D - 1.0E-9D));
    }
}
