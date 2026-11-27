/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/trade/TradeList.
 */
package io.github.capttracker.ancientwarfarenpc.trade;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import io.github.capttracker.ancientwarfare.util.OrderingList;
import io.github.capttracker.ancientwarfarenpc.orders.OrderCodecs;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * An ordered list of trades with the NBT form {@code {tradeList: [trade, ...]}} (the same layout as 1.12.2). Trades that are not valid
 * (nothing to pay or nothing to get, for example because an item of a missing mod could not be read) are left out when the list is read.
 */
public abstract class TradeList<T extends Trade> extends OrderingList<T> implements INBTSerializable<CompoundTag> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TRADE_LIST_TAG = "tradeList";

    public final void addNewTrade() {
        add(getNewTrade());
    }

    protected abstract T getNewTrade();

    /** The codec of one trade of this list. */
    protected abstract Codec<T> tradeCodec();

    /** A read-only view of the trades. */
    public List<T> getTrades() {
        return Collections.unmodifiableList(points);
    }

    /**
     * Does the trade of the list index for the player. An index out of range (a client may send anything) does nothing.
     *
     * @param storage where the paid items go and the output comes from; null for a list without stock
     */
    public boolean performTrade(Player player, @Nullable IItemHandler storage, int index) {
        return index >= 0 && index < size() && get(index).performTrade(player, storage);
    }

    /**
     * The codec of a whole list as an object with the field {@code tradeList}, for the data components and the network. Trades that cannot be
     * read or are not valid are dropped.
     */
    protected static <T extends Trade, L extends TradeList<T>> Codec<L> listCodec(Codec<T> tradeCodec, Supplier<L> factory) {
        return OrderCodecs.lenientList(tradeCodec).optionalFieldOf(TRADE_LIST_TAG, List.<T>of()).xmap(trades -> {
            L list = factory.get();
            list.fill(trades);
            return list;
        }, list -> new ArrayList<>(list.getTrades())).codec();
    }

    final void fill(List<T> trades) {
        clear();
        for (T trade : trades) {
            if (trade.isValid()) {
                add(trade);
            }
        }
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        RegistryOps<Tag> ops = provider.createSerializationContext(NbtOps.INSTANCE);
        CompoundTag tag = new CompoundTag();
        tradeCodec().listOf().encodeStart(ops, new ArrayList<>(points)).resultOrPartial(error -> LOGGER.error("Couldn't save trade list: {}", error))
                .ifPresent(list -> tag.put(TRADE_LIST_TAG, list));
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        clear();
        Tag list = tag.get(TRADE_LIST_TAG);
        if (list == null) {
            return;
        }
        RegistryOps<Tag> ops = provider.createSerializationContext(NbtOps.INSTANCE);
        OrderCodecs.lenientList(tradeCodec()).parse(ops, list).resultOrPartial(error -> LOGGER.error("Couldn't read trade list: {}", error))
                .ifPresent(this::fill);
    }
}
