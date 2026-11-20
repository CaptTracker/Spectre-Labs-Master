/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): the shop of a trader that staffs a market stall.
 */
package io.github.capttracker.ancientwarfarenpc.menu;

import io.github.capttracker.ancientwarfare.api.IMarketStall;
import io.github.capttracker.ancientwarfare.api.IMarketStall.BuyOrder;
import io.github.capttracker.ancientwarfare.api.IMarketStall.SellOffer;
import io.github.capttracker.ancientwarfare.api.IMarketStall.TradeResult;
import io.github.capttracker.ancientwarfarenpc.entity.NpcTrader;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcMenus;
import io.github.capttracker.ancientwarfarenpc.trade.MarketApi;
import io.github.capttracker.spectrefactions.api.Relation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The shop of a trader that staffs a market stall: the sell offers with the price this player pays (the reputation multiplier applied), a Buy button each,
 * and the buy orders of the stall for information. Anyone may use it. The client only asks ({@code buy} with the offer index); the server finds the stall of
 * the trader, checks everything again and does the whole deal through {@link MarketApi#buy}, then sends the list again with the result code.
 *
 * <p>Messages: server to client {@code shop} (offers, orders, {@code refused}, {@code open}, {@code multiplier}, {@code result}); client to server
 * {@code buy} (offer index).
 */
public class MarketShopMenu extends NpcMenuBase<NpcTrader> {
    private static final String SHOP_TAG = "shop";
    private static final String BUY_TAG = "buy";

    /** An offer as the client shows it: the price is already the one this player pays. */
    public record Offer(int index, ItemStack item, ItemStack price, int deals) {}

    /** A buy order of the stall as the client shows it. */
    public record Order(ItemStack item, ItemStack price, int remaining) {}

    public final List<Offer> offers = new ArrayList<>();
    public final List<Order> orders = new ArrayList<>();
    public boolean open = false;
    public boolean refused = false;
    public double multiplier = 1.0D;
    /** The {@link TradeResult} of the last purchase on this screen, or -1. */
    public int lastResult = -1;

    /** Client side: the entity id of the NPC comes from the buffer written by {@link NpcMenuOpener}. */
    public MarketShopMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, find(inventory.player, buf.readVarInt(), NpcTrader.class));
    }

    public MarketShopMenu(int containerId, Inventory inventory, @Nullable NpcTrader npc) {
        super(AwNpcMenus.MARKET_SHOP_MENU.get(), containerId, inventory, npc);
        addPlayerSlots(inventory, 8, 240 - 4 - 8 - 4 * 18, 4);
    }

    /** Anyone may buy. */
    @Override
    protected boolean mayUse(Player who) {
        return true;
    }

    @Override
    protected void sendInitialState() {
        sendShop(-1);
    }

    private void sendShop(int result) {
        CompoundTag tag = new CompoundTag();
        CompoundTag shop = new CompoundTag();
        Optional<IMarketStall> found = npc == null ? Optional.empty() : MarketApi.stallOf(npc);
        shop.putBoolean("open", found.isPresent());
        shop.putInt("result", result);
        ListTag offerList = new ListTag();
        ListTag orderList = new ListTag();
        if (found.isPresent() && player.level() instanceof ServerLevel level) {
            IMarketStall stall = found.get();
            Relation relation = MarketApi.sellerOf(level, stall).map(actor -> MarketApi.relationOf(player, actor)).orElse(Relation.NEUTRAL);
            boolean refuse = MarketApi.refuses(relation);
            shop.putBoolean("refused", refuse);
            shop.putDouble("multiplier", MarketApi.priceMultiplier(relation));
            for (SellOffer offer : stall.getSellOffers()) {
                CompoundTag entry = new CompoundTag();
                entry.putInt("index", offer.index());
                entry.put("item", offer.item().save(registries()));
                ItemStack quoted = MarketApi.quote(level, player, stall, offer);
                entry.put("price", (quoted.isEmpty() ? offer.price() : quoted).save(registries()));
                entry.putInt("deals", offer.deals());
                offerList.add(entry);
            }
            for (BuyOrder order : stall.getBuyOrders()) {
                CompoundTag entry = new CompoundTag();
                entry.put("item", order.item().save(registries()));
                entry.put("price", order.price().save(registries()));
                entry.putInt("remaining", order.remaining());
                orderList.add(entry);
            }
        }
        shop.put("offers", offerList);
        shop.put("orders", orderList);
        tag.put(SHOP_TAG, shop);
        sendDataToClient(tag);
    }

    @Override
    protected void handlePacketData(CompoundTag tag) {
        if (isClient()) {
            if (tag.contains(SHOP_TAG, Tag.TAG_COMPOUND)) {
                readShop(tag.getCompound(SHOP_TAG));
            }
            refreshGui();
            return;
        }
        if (!isRequestAllowed() || npc == null || !tag.contains(BUY_TAG, Tag.TAG_INT)) {
            return;
        }
        int result = MarketApi.stallOf(npc)
                .map(stall -> MarketApi.buy(player, npc, stall, tag.getInt(BUY_TAG), 1))
                .orElse(TradeResult.CLOSED).ordinal();
        sendShop(result);
    }

    private void readShop(CompoundTag shop) {
        open = shop.getBoolean("open");
        refused = shop.getBoolean("refused");
        multiplier = shop.contains("multiplier") ? shop.getDouble("multiplier") : 1.0D;
        lastResult = shop.contains("result") ? shop.getInt("result") : -1;
        offers.clear();
        orders.clear();
        ListTag offerList = shop.getList("offers", Tag.TAG_COMPOUND);
        for (int i = 0; i < offerList.size(); i++) {
            CompoundTag entry = offerList.getCompound(i);
            offers.add(new Offer(entry.getInt("index"), ItemStack.parseOptional(registries(), entry.getCompound("item")),
                    ItemStack.parseOptional(registries(), entry.getCompound("price")), entry.getInt("deals")));
        }
        ListTag orderList = shop.getList("orders", Tag.TAG_COMPOUND);
        for (int i = 0; i < orderList.size(); i++) {
            CompoundTag entry = orderList.getCompound(i);
            orders.add(new Order(ItemStack.parseOptional(registries(), entry.getCompound("item")),
                    ItemStack.parseOptional(registries(), entry.getCompound("price")), entry.getInt("remaining")));
        }
    }

    /** Client side: asks the server to buy one deal of the offer with this index. */
    public void buy(int offerIndex) {
        sendDataToServer(BUY_TAG, net.minecraft.nbt.IntTag.valueOf(offerIndex));
    }
}
