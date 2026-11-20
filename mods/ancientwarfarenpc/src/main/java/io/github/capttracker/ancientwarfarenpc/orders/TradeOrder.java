/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/orders/TradeOrder.
 */
package io.github.capttracker.ancientwarfarenpc.orders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcItemComponents;
import io.github.capttracker.ancientwarfarenpc.item.order.ItemTradeOrder;
import io.github.capttracker.ancientwarfarenpc.trade.POTradeList;
import io.github.capttracker.ancientwarfarenpc.trade.POTradeRestockData;
import io.github.capttracker.ancientwarfarenpc.trade.POTradeRoute;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * The trade order slip: what a player owned trader sells ({@link POTradeList}), the route it walks ({@link POTradeRoute}) and how it restocks
 * ({@link POTradeRestockData}). Stored in the {@code ancientwarfarenpc:trade_order} data component with the layout of the 1.12.2 tag
 * {@code orders}: {@code tradeList}, {@code tradeRoute}, {@code restockEntry}.
 */
public class TradeOrder {
    public static final Codec<TradeOrder> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            POTradeList.CODEC.optionalFieldOf("tradeList", new POTradeList()).forGetter(TradeOrder::getTradeList),
            POTradeRoute.CODEC.optionalFieldOf("tradeRoute", new POTradeRoute()).forGetter(TradeOrder::getRoute),
            POTradeRestockData.CODEC.optionalFieldOf("restockEntry", new POTradeRestockData()).forGetter(TradeOrder::getRestockData)
    ).apply(instance, TradeOrder::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, TradeOrder> STREAM_CODEC = OrderCodecs.stream(CODEC);

    private POTradeRoute tradeRoute = new POTradeRoute();
    private POTradeRestockData restockEntry = new POTradeRestockData();
    private POTradeList tradeList = new POTradeList();

    public TradeOrder() {
    }

    private TradeOrder(POTradeList tradeList, POTradeRoute tradeRoute, POTradeRestockData restockEntry) {
        this.tradeList = tradeList.copy();
        this.tradeRoute = tradeRoute.copy();
        this.restockEntry = restockEntry.copy();
    }

    public TradeOrder copy() {
        return new TradeOrder(tradeList, tradeRoute, restockEntry);
    }

    /** The order of the stack (a new one if the stack has no data), or null if the stack is not a trade order. */
    @Nullable
    public static TradeOrder getTradeOrder(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof ItemTradeOrder) {
            TradeOrder order = stack.get(AwNpcItemComponents.TRADE_ORDER.get());
            return order == null ? new TradeOrder() : order.copy();
        }
        return null;
    }

    /** Stores a copy of the order in the stack (if it is a trade order). */
    public void write(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof ItemTradeOrder) {
            stack.set(AwNpcItemComponents.TRADE_ORDER.get(), copy());
        }
    }

    public POTradeList getTradeList() {
        return tradeList;
    }

    public POTradeRoute getRoute() {
        return tradeRoute;
    }

    public POTradeRestockData getRestockData() {
        return restockEntry;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TradeOrder other && tradeList.equals(other.tradeList) && tradeRoute.equals(other.tradeRoute)
                && restockEntry.equals(other.restockEntry);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tradeList, tradeRoute, restockEntry);
    }
}
