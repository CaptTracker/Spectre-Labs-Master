/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/trade/POTradeList.
 */
package io.github.capttracker.ancientwarfarenpc.trade;

import com.mojang.serialization.Codec;
import io.github.capttracker.ancientwarfarenpc.orders.OrderCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** The trades of a player owned trader (the {@code tradeList} of its trade order). */
public class POTradeList extends TradeList<POTrade> {
    public static final Codec<POTradeList> CODEC = listCodec(POTrade.CODEC, POTradeList::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, POTradeList> STREAM_CODEC = OrderCodecs.stream(CODEC);

    @Override
    protected POTrade getNewTrade() {
        return new POTrade();
    }

    @Override
    protected Codec<POTrade> tradeCodec() {
        return POTrade.CODEC;
    }

    public POTradeList copy() {
        POTradeList ret = new POTradeList();
        for (POTrade trade : points) {
            ret.add(trade.copy());
        }
        return ret;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof POTradeList other && points.equals(other.points);
    }

    @Override
    public int hashCode() {
        return points.hashCode();
    }
}
