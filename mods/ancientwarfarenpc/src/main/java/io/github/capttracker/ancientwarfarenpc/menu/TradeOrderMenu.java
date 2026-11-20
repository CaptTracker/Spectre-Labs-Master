/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/container/ContainerTradeOrder.
 */
package io.github.capttracker.ancientwarfarenpc.menu;

import com.mojang.serialization.Codec;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcMenus;
import io.github.capttracker.ancientwarfarenpc.item.order.ItemTradeOrder;
import io.github.capttracker.ancientwarfarenpc.orders.TradeOrder;
import io.github.capttracker.ancientwarfarenpc.trade.POTradeRoute;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The trade order editor (1.12.2: {@code GUI_NPC_TRADE_ORDER}, 6): the trades, the route and the restock lists of a player-owned trader, and the
 * player's inventory below so that items can be picked up to fill the trade slots. The message key is {@code tradeOrder}.
 *
 * <p>The check ({@link OrderMenu}): at most {@value #MAX_TRADES} trades and {@value #MAX_ENTRIES} entries in each other list; every route point is a
 * position the stored order already has, with a delay of 0 or more; the withdraw and deposit points are the stored ones (the screen can only change
 * their sides and the lists).
 */
public class TradeOrderMenu extends OrderMenu<TradeOrder> {
    public static final int MAX_TRADES = 512;
    public static final int MAX_ENTRIES = 64;

    /** Client side: the hand comes from the buffer written by {@link #open}. */
    public TradeOrderMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readEnum(InteractionHand.class));
    }

    public TradeOrderMenu(int containerId, Inventory inventory, InteractionHand hand) {
        super(AwNpcMenus.TRADE_ORDER_MENU.get(), containerId, inventory, hand);
        addPlayerSlots(inventory, (256 - (9 * 18)) / 2, 240 - 4 - 8 - 4 * 18, 4);
    }

    /** Server side: opens the editor for the trade order in the hand (installed with {@code OrderGuiBridge.setGuiOpener}). */
    public static void open(ServerPlayer player, InteractionHand hand) {
        openMenu(player, hand, (id, inventory, p) -> new TradeOrderMenu(id, inventory, hand));
    }

    @Override
    protected String dataKey() {
        return "tradeOrder";
    }

    @Override
    protected Codec<TradeOrder> codec() {
        return TradeOrder.CODEC;
    }

    @Nullable
    @Override
    protected TradeOrder readOrder(ItemStack stack) {
        return TradeOrder.getTradeOrder(stack);
    }

    @Override
    protected TradeOrder newOrder() {
        return new TradeOrder();
    }

    @Override
    protected void writeOrder(TradeOrder order, ItemStack stack) {
        order.write(stack);
    }

    @Override
    protected boolean isOrderItem(ItemStack stack) {
        return stack.getItem() instanceof ItemTradeOrder;
    }

    @Override
    protected boolean isRawValid(CompoundTag raw) {
        return raw.getCompound("tradeList").getList("tradeList", Tag.TAG_COMPOUND).size() <= MAX_TRADES
                && raw.getCompound("tradeRoute").getList("route", Tag.TAG_COMPOUND).size() <= MAX_ENTRIES
                && raw.getCompound("restockEntry").getList("depositList", Tag.TAG_COMPOUND).size() <= MAX_ENTRIES
                && raw.getCompound("restockEntry").getList("withdrawList", Tag.TAG_COMPOUND).size() <= MAX_ENTRIES;
    }

    @Nullable
    @Override
    protected TradeOrder sanitize(TradeOrder current, TradeOrder sent) {
        // route: only points the order has
        POTradeRoute sentRoute = sent.getRoute();
        POTradeRoute currentRoute = current.getRoute();
        List<BlockPos> sentPoints = new ArrayList<>();
        for (int i = 0; i < sentRoute.size(); i++) {
            sentPoints.add(sentRoute.get(i).getPosition());
        }
        List<BlockPos> currentPoints = new ArrayList<>();
        for (int i = 0; i < currentRoute.size(); i++) {
            currentPoints.add(currentRoute.get(i).getPosition());
        }
        if (!isSubset(sentPoints, currentPoints)) {
            return null;
        }
        for (int i = 0; i < sentRoute.size(); i++) {
            sentRoute.setPointDelay(i, Math.max(0, sentRoute.get(i).getDelay()));
        }
        // restock: the points are set with the item, not in the screen
        if (!Objects.equals(sent.getRestockData().getDepositPoint(), current.getRestockData().getDepositPoint())
                || !Objects.equals(sent.getRestockData().getWithdrawPoint(), current.getRestockData().getWithdrawPoint())) {
            return null;
        }
        return sent;
    }
}
