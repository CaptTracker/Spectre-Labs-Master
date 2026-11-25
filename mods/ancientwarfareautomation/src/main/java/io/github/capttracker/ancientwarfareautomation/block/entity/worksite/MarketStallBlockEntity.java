/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): the market stall worksite. No 1.12.2 original.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import io.github.capttracker.ancientwarfare.api.IMarketStall;
import io.github.capttracker.ancientwarfare.block.BlockRotationHandler.RelativeSide;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The market stall (rework phase 5). A trader NPC of the settlement stands in front of it during the work hours ({@code NpcAIMarketStall} in the NPC
 * module reports in with {@link #staff}); while it does, the stall is open and players buy by right-clicking the trader.
 *
 * <p><b>Offers.</b> The owner edits up to {@value IMarketStall#MAX_SELL_OFFERS} sell offers (item, amount per deal, price in one coin item) and
 * {@value IMarketStall#MAX_BUY_ORDERS} buy orders (the same, with an optional limit on the items bought) in the offers screen.
 *
 * <p><b>Inventories.</b> {@code mainInventory} (27) takes the coins the stall earns and the goods it buys; couriers haul it to storage
 * ({@link #getLogisticsOutputs}). {@code stockInventory} (18) holds the goods for the sell offers; couriers keep it stocked from the settlement's storage
 * ({@link #getLogisticsNeeds}, {@code market_stock_batches} deals of each offer). {@code tillInventory} (9) holds the coins for the buy orders, kept stocked
 * the same way ({@code market_till_batches} deals). Buy orders pay from the till first, then from coins in the main inventory.
 *
 * <p>The site is not on the job board (work type none): it has no workers of its own, the trader is not a worker. It is a logistics site for the couriers.
 */
public class MarketStallBlockEntity extends InventoryWorksiteBlockEntity implements IMarketStall {
    public static final int STOCK_SIZE = 18;
    public static final int TILL_SIZE = 9;
    /** Ticks after the last report of the trader until the stall counts as closed. */
    public static final int STAFF_TIMEOUT = 60;

    public final ItemStackHandler stockInventory;
    public final ItemStackHandler tillInventory;

    private final List<MarketEntry> sellOffers = new ArrayList<>();
    private final List<MarketEntry> buyOrders = new ArrayList<>();

    @Nullable
    private UUID trader;
    private long traderSeen = Long.MIN_VALUE / 2;

    public MarketStallBlockEntity(BlockPos pos, BlockState state) {
        super(AwAutomationBase.MARKET_STALL_BE.get(), pos, state);
        stockInventory = new ItemStackHandler(STOCK_SIZE) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };
        tillInventory = new ItemStackHandler(TILL_SIZE) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };
        setSideInventory(RelativeSide.FRONT, stockInventory, RelativeSide.FRONT);
        setSideInventory(RelativeSide.BOTTOM, tillInventory, RelativeSide.TOP);
        for (int i = 0; i < MAX_SELL_OFFERS; i++) {
            sellOffers.add(new MarketEntry());
        }
        for (int i = 0; i < MAX_BUY_ORDERS; i++) {
            buyOrders.add(new MarketEntry());
        }
    }

    // ---------------------------------------------------------------- worksite plumbing

    @Override
    public WorkType getWorkType() {
        return WorkType.NONE;
    }

    @Override
    public boolean hasWork() {
        return false;
    }

    @Override
    public boolean userAdjustableBlocks() {
        return false;
    }

    @Override
    protected java.util.Optional<IWorksiteAction> getNextAction() {
        return Optional.empty();
    }

    @Override
    protected boolean processAction(IWorksiteAction action) {
        return false;
    }

    @Override
    protected void updateWorksite() {
        // nothing to scan: the offers change in the screen, the trader reports in by itself
    }

    @Override
    public boolean onBlockClicked(Player player, @Nullable InteractionHand hand) {
        if (!player.level().isClientSide) {
            WorksiteMenus.open(player, WorksiteMenus.Kind.MARKET_STALL, this);
        }
        return true;
    }

    /** The offers screen (the alt controls of the stall). */
    @Override
    public void openAltGui(Player player) {
        WorksiteMenus.open(player, WorksiteMenus.Kind.MARKET_OFFERS, this);
    }

    @Override
    public void onBlockBroken(BlockState state) {
        super.onBlockBroken(state);
        if (level != null) {
            InventoryTools.dropItemsInWorld(level, stockInventory, worldPosition);
            InventoryTools.dropItemsInWorld(level, tillInventory, worldPosition);
        }
    }

    // ---------------------------------------------------------------- logistics

    /** Coins and goods bought go to storage. */
    @Override
    public List<Output> getLogisticsOutputs() {
        return List.of(new Output(mainInventory, stack -> true));
    }

    /** Stock for each distinct sell offer item, coins in the till for the buy orders that are still active. */
    @Override
    public List<Need> getLogisticsNeeds() {
        List<Need> needs = new ArrayList<>();
        Map<ItemStack, Integer> stock = new LinkedHashMap<>();
        for (MarketEntry entry : sellOffers) {
            if (entry.isComplete()) {
                addTarget(stock, entry.item, entry.amount() * AWAutomationStatics.marketStockBatches);
            }
        }
        for (Map.Entry<ItemStack, Integer> target : stock.entrySet()) {
            ItemStack template = target.getKey();
            needs.add(new Need(stockInventory, stack -> ItemStack.isSameItemSameComponents(stack, template), target.getValue()));
        }
        Map<ItemStack, Integer> coins = new LinkedHashMap<>();
        for (MarketEntry entry : buyOrders) {
            if (entry.isComplete() && (entry.limit == 0 || entry.remaining > 0)) {
                addTarget(coins, entry.price, entry.price.getCount() * AWAutomationStatics.marketTillBatches);
            }
        }
        for (Map.Entry<ItemStack, Integer> target : coins.entrySet()) {
            ItemStack template = target.getKey();
            needs.add(new Need(tillInventory, stack -> ItemStack.isSameItemSameComponents(stack, template), target.getValue()));
        }
        return needs;
    }

    private static void addTarget(Map<ItemStack, Integer> targets, ItemStack item, int amount) {
        for (Map.Entry<ItemStack, Integer> existing : targets.entrySet()) {
            if (ItemStack.isSameItemSameComponents(existing.getKey(), item)) {
                existing.setValue(existing.getValue() + amount);
                return;
            }
        }
        ItemStack template = item.copyWithCount(1);
        targets.put(template, amount);
    }

    // ---------------------------------------------------------------- staffing (IMarketStall)

    @Override
    public boolean isOpen() {
        return trader != null && level != null && level.getGameTime() - traderSeen <= STAFF_TIMEOUT;
    }

    @Override
    public Optional<UUID> getTrader() {
        return isOpen() ? Optional.of(trader) : Optional.empty();
    }

    @Override
    public BlockPos getStallPos() {
        return worldPosition;
    }

    @Override
    public BlockPos getStandPos() {
        return worldPosition.relative(getPrimaryFacing());
    }

    @Override
    public boolean staff(UUID who, long gameTime) {
        if (trader != null && !trader.equals(who) && gameTime - traderSeen <= STAFF_TIMEOUT) {
            return false;
        }
        trader = who;
        traderSeen = gameTime;
        return true;
    }

    @Override
    public void release(UUID who) {
        if (who.equals(trader)) {
            trader = null;
            traderSeen = Long.MIN_VALUE / 2;
        }
    }

    // ---------------------------------------------------------------- offers

    /** The raw entries (always the full list, complete or not). Live: use {@link #setOffers} to change them. */
    public List<MarketEntry> getSellEntries() {
        return sellOffers;
    }

    public List<MarketEntry> getBuyEntries() {
        return buyOrders;
    }

    /**
     * Replaces the offers with sanitised copies of the given lists (cut or padded to the maximum). A buy order whose limit did not change keeps what is left of
     * it; one with a new limit starts full.
     */
    public void setOffers(List<MarketEntry> sell, List<MarketEntry> buy) {
        for (int i = 0; i < MAX_SELL_OFFERS; i++) {
            MarketEntry entry = i < sell.size() ? sell.get(i).copy() : new MarketEntry();
            entry.sanitize();
            entry.limit = 0;
            entry.remaining = 0;
            sellOffers.set(i, entry);
        }
        for (int i = 0; i < MAX_BUY_ORDERS; i++) {
            MarketEntry entry = i < buy.size() ? buy.get(i).copy() : new MarketEntry();
            entry.sanitize();
            MarketEntry old = buyOrders.get(i);
            if (entry.limit == 0) {
                entry.remaining = 0;
            } else if (entry.limit == old.limit && ItemStack.isSameItemSameComponents(entry.item, old.item)) {
                entry.remaining = Math.min(entry.limit, old.remaining);
            } else {
                entry.remaining = entry.limit;
            }
            buyOrders.set(i, entry);
        }
        setChanged();
    }

    @Override
    public List<SellOffer> getSellOffers() {
        List<SellOffer> result = new ArrayList<>();
        for (int i = 0; i < sellOffers.size(); i++) {
            MarketEntry entry = sellOffers.get(i);
            if (entry.isComplete()) {
                result.add(new SellOffer(i, entry.item.copy(), entry.price.copy(), count(stockInventory, entry.item)));
            }
        }
        return result;
    }

    @Override
    public List<BuyOrder> getBuyOrders() {
        List<BuyOrder> result = new ArrayList<>();
        for (int i = 0; i < buyOrders.size(); i++) {
            MarketEntry entry = buyOrders.get(i);
            if (entry.isComplete()) {
                result.add(new BuyOrder(i, entry.item.copy(), entry.price.copy(), entry.limit == 0 ? -1 : entry.remaining, maxFillDeals(entry)));
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- inventory helpers

    private static int count(IItemHandler handler, ItemStack template) {
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, template)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Takes exactly {@code amount} matching items out (nothing if the handler cannot give that many). */
    private static ItemStack take(IItemHandler handler, ItemStack template, int amount) {
        if (amount <= 0 || count(handler, template) < amount) {
            return ItemStack.EMPTY;
        }
        ItemStack result = ItemStack.EMPTY;
        int left = amount;
        for (int slot = 0; slot < handler.getSlots() && left > 0; slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, template)) {
                continue;
            }
            ItemStack got = handler.extractItem(slot, left, false);
            if (got.isEmpty()) {
                continue;
            }
            if (result.isEmpty()) {
                result = got.copy();
            } else {
                result.grow(got.getCount());
            }
            left -= got.getCount();
        }
        if (left > 0) {
            // the handler refused part of it (a locked slot): put back what came out, nothing happens
            if (!result.isEmpty()) {
                give(handler, result);
            }
            return ItemStack.EMPTY;
        }
        return result;
    }

    /** Inserts the whole stack (in max-size pieces) and returns what did not fit. */
    private static ItemStack give(IItemHandler handler, ItemStack stack) {
        return ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false);
    }

    private static boolean fits(IItemHandler handler, ItemStack stack) {
        return ItemHandlerHelper.insertItemStacked(handler, stack.copy(), true).isEmpty();
    }

    /** True if both handlers together can take the stack, trying the first and then the second (a simulation that does not see its own effect on the first). */
    private boolean stallCanTake(ItemStack stack) {
        ItemStack left = ItemHandlerHelper.insertItemStacked(mainInventory, stack.copy(), true);
        return left.isEmpty();
    }

    /** Puts a stack into the stall (main inventory, then stock, then the till) and drops what does not fit; nothing is lost. */
    private void receive(ItemStack stack) {
        ItemStack left = give(mainInventory, stack);
        if (!left.isEmpty()) {
            left = give(stockInventory, left);
        }
        if (!left.isEmpty()) {
            left = give(tillInventory, left);
        }
        if (!left.isEmpty() && level != null) {
            InventoryTools.dropItemInWorld(level, left, worldPosition);
        }
    }

    // ---------------------------------------------------------------- selling

    @Override
    public TradeResult sell(int offerIndex, int deals, double priceMultiplier, IItemHandler buyer) {
        if (level == null || level.isClientSide || !isOpen()) {
            return TradeResult.CLOSED;
        }
        if (offerIndex < 0 || offerIndex >= sellOffers.size() || !sellOffers.get(offerIndex).isComplete() || deals < 1 || deals > 64) {
            return TradeResult.NO_SUCH_ENTRY;
        }
        MarketEntry offer = sellOffers.get(offerIndex);
        int goodsCount = offer.amount() * deals;
        int coinCount = IMarketStall.adjustPrice(offer.price.getCount(), priceMultiplier) * deals;
        if (count(stockInventory, offer.item) < goodsCount) {
            return TradeResult.NO_STOCK;
        }
        if (count(buyer, offer.price) < coinCount) {
            return TradeResult.NO_PAYMENT;
        }
        ItemStack coinsOut = offer.price.copyWithCount(coinCount);
        if (!stallCanTake(coinsOut)) {
            return TradeResult.STALL_FULL;
        }
        // 1. the coins leave the buyer
        ItemStack paid = take(buyer, offer.price, coinCount);
        if (paid.isEmpty()) {
            return TradeResult.NO_PAYMENT;
        }
        // 2. the goods must fit into the buyer (its coin slots are free now)
        ItemStack goods = offer.item.copyWithCount(goodsCount);
        if (!fits(buyer, goods)) {
            give(buyer, paid);
            return TradeResult.NO_ROOM;
        }
        // 3. the goods leave the stock
        ItemStack taken = take(stockInventory, offer.item, goodsCount);
        if (taken.isEmpty()) {
            give(buyer, paid);
            return TradeResult.NO_STOCK;
        }
        // 4. both sides get theirs; the simulations above say it fits, and a leftover goes back, never gets lost
        ItemStack goodsLeft = give(buyer, taken);
        if (!goodsLeft.isEmpty()) {
            receive(goodsLeft);
        }
        receive(paid);
        setChanged();
        return TradeResult.OK;
    }

    // ---------------------------------------------------------------- buying (buy orders)

    /** How many coins of the kind the stall can pay out now (till and main inventory). */
    private int coinsAvailable(ItemStack coin) {
        return count(tillInventory, coin) + count(mainInventory, coin);
    }

    /** The deals a buy order could do right now by its own limit and the stall's coins and room (not the seller's side). */
    private int maxFillDeals(MarketEntry entry) {
        int deals = 64;
        if (entry.limit > 0) {
            deals = Math.min(deals, entry.remaining / entry.amount());
        }
        deals = Math.min(deals, coinsAvailable(entry.price) / Math.max(1, entry.price.getCount()));
        while (deals > 0 && !stallCanTake(entry.item.copyWithCount(entry.amount() * deals))) {
            deals--;
        }
        return Math.max(0, deals);
    }

    @Override
    public Fill fillBuyOrder(int orderIndex, int deals, IItemHandler seller) {
        if (level == null || level.isClientSide) {
            return new Fill(TradeResult.CLOSED, 0);
        }
        if (orderIndex < 0 || orderIndex >= buyOrders.size() || !buyOrders.get(orderIndex).isComplete() || deals < 1) {
            return new Fill(TradeResult.NO_SUCH_ENTRY, 0);
        }
        MarketEntry order = buyOrders.get(orderIndex);
        if (order.limit > 0 && order.remaining < order.amount()) {
            return new Fill(TradeResult.LIMIT_REACHED, 0);
        }
        int possible = maxFillDeals(order);
        if (possible < 1) {
            return new Fill(coinsAvailable(order.price) < order.price.getCount() ? TradeResult.NO_COINS : TradeResult.STALL_FULL, 0);
        }
        int k = Math.min(Math.min(deals, 64), possible);
        k = Math.min(k, count(seller, order.item) / order.amount());
        if (k < 1) {
            return new Fill(TradeResult.NO_PAYMENT, 0);
        }
        while (k > 0) {
            // the seller's coin room is checked as it will be once its goods are out
            IItemHandlerModifiable simulated = InventoryTools.cloneItemHandler(seller);
            if (!take(simulated, order.item, order.amount() * k).isEmpty() && fits(simulated, order.price.copyWithCount(order.price.getCount() * k))) {
                break;
            }
            k--;
        }
        if (k < 1) {
            return new Fill(TradeResult.NO_ROOM, 0);
        }
        int goodsCount = order.amount() * k;
        int coinCount = order.price.getCount() * k;
        // 1. the items leave the seller
        ItemStack goods = take(seller, order.item, goodsCount);
        if (goods.isEmpty()) {
            return new Fill(TradeResult.NO_PAYMENT, 0);
        }
        ItemStack coins = order.price.copyWithCount(coinCount);
        // 2. the coins must fit into the seller now that its goods are out
        if (!fits(seller, coins)) {
            give(seller, goods);
            return new Fill(TradeResult.NO_ROOM, 0);
        }
        // 3. the coins leave the stall: the till first, then the main inventory
        int fromTill = Math.min(coinCount, count(tillInventory, order.price));
        ItemStack paidFromTill = fromTill > 0 ? take(tillInventory, order.price, fromTill) : ItemStack.EMPTY;
        int rest = coinCount - (paidFromTill.isEmpty() ? 0 : paidFromTill.getCount());
        ItemStack paidFromMain = rest > 0 ? take(mainInventory, order.price, rest) : ItemStack.EMPTY;
        int got = (paidFromTill.isEmpty() ? 0 : paidFromTill.getCount()) + (paidFromMain.isEmpty() ? 0 : paidFromMain.getCount());
        if (got < coinCount) {
            // the stall could not pay after all: everything goes back
            if (!paidFromTill.isEmpty()) {
                give(tillInventory, paidFromTill);
            }
            if (!paidFromMain.isEmpty()) {
                give(mainInventory, paidFromMain);
            }
            give(seller, goods);
            return new Fill(TradeResult.NO_COINS, 0);
        }
        // 4. both sides get theirs
        ItemStack coinsLeft = give(seller, coins);
        if (!coinsLeft.isEmpty()) {
            receive(coinsLeft);
        }
        receive(goods);
        if (order.limit > 0) {
            order.remaining = Math.max(0, order.remaining - goodsCount);
        }
        setChanged();
        return new Fill(TradeResult.OK, k);
    }

    // ---------------------------------------------------------------- saving

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stockInventory.deserializeNBT(registries, tag.getCompound("stockInventory"));
        tillInventory.deserializeNBT(registries, tag.getCompound("tillInventory"));
        List<MarketEntry> sell = MarketEntry.loadList(tag.getList("sellOffers", Tag.TAG_COMPOUND), registries, MAX_SELL_OFFERS);
        List<MarketEntry> buy = MarketEntry.loadList(tag.getList("buyOrders", Tag.TAG_COMPOUND), registries, MAX_BUY_ORDERS);
        for (int i = 0; i < MAX_SELL_OFFERS; i++) {
            sellOffers.set(i, i < sell.size() ? sell.get(i) : new MarketEntry());
        }
        for (int i = 0; i < MAX_BUY_ORDERS; i++) {
            buyOrders.set(i, i < buy.size() ? buy.get(i) : new MarketEntry());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("stockInventory", stockInventory.serializeNBT(registries));
        tag.put("tillInventory", tillInventory.serializeNBT(registries));
        tag.put("sellOffers", MarketEntry.saveList(sellOffers, registries));
        tag.put("buyOrders", MarketEntry.saveList(buyOrders, registries));
    }
}
