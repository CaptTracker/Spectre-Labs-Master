/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/trade/FactionTradeList.
 */
package io.github.capttracker.ancientwarfarenpc.trade;

import com.mojang.serialization.Codec;
import io.github.capttracker.ancientwarfarenpc.orders.OrderCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Iterator;

/**
 * The trades of a faction trader. The owning entity must call {@link #tick} once per update tick; the server calls
 * {@link #updateTradesForView} before it opens the trade view.
 */
public final class FactionTradeList extends TradeList<FactionTrade> {
    public static final Codec<FactionTradeList> CODEC = listCodec(FactionTrade.CODEC, FactionTradeList::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, FactionTradeList> STREAM_CODEC = OrderCodecs.stream(CODEC);

    private long totalWorldTime = 0;

    @Override
    protected FactionTrade getNewTrade() {
        return new FactionTrade();
    }

    @Override
    protected Codec<FactionTrade> tradeCodec() {
        return FactionTrade.CODEC;
    }

    /** Must be called from the owning entity once per update tick (on the server). */
    public void tick(Level world) {
        totalWorldTime = world.getGameTime();
    }

    /**
     * Should be called on the server before the trades view is opened. Uses the time stored by {@link #tick} to refill the trades.
     */
    public void updateTradesForView() {
        for (FactionTrade aTrade : points) {
            aTrade.updateTrade(totalWorldTime);
        }
    }

    /**
     * Removes any trades that have no input or output items. Should be called before the changed list is sent from the client to the server
     * by the setup screen.
     */
    public void removeEmptyTrades() {
        Iterator<FactionTrade> it = points.iterator();
        while (it.hasNext()) {
            if (!it.next().hasItems()) {
                it.remove();
            }
        }
    }

    public boolean performTrade(Player player, int tradeNum) {
        return tradeNum >= 0 && tradeNum < size() && get(tradeNum).performTrade(player, null);
    }

    /** As {@link #performTrade(Player, int)} with the coins scaled by a price multiplier (rework phase 5). */
    public boolean performTrade(Player player, int tradeNum, double priceMultiplier) {
        return tradeNum >= 0 && tradeNum < size() && get(tradeNum).performTrade(player, priceMultiplier);
    }

    public FactionTradeList copy() {
        FactionTradeList ret = new FactionTradeList();
        for (FactionTrade trade : points) {
            ret.add(trade.copy());
        }
        ret.totalWorldTime = totalWorldTime;
        return ret;
    }
}
