/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/trade/POTradeWithdrawEntry.
 */
package io.github.capttracker.ancientwarfarenpc.trade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/** A line of the withdraw list: items the trader takes from the withdraw inventory into its stock when it restocks. */
public final class POTradeWithdrawEntry extends POTradeTransferEntry {
    public static final Codec<POTradeWithdrawEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.CODEC.lenientOptionalFieldOf("item").forGetter(POTradeTransferEntry::filterForCodec),
            Codec.INT.optionalFieldOf("type", 0).forGetter(entry -> entry.getType().ordinal())
    ).apply(instance, (filter, type) -> {
        POTradeWithdrawEntry entry = new POTradeWithdrawEntry();
        entry.load(filter, type);
        return entry;
    }));

    public POTradeWithdrawEntry copy() {
        POTradeWithdrawEntry ret = new POTradeWithdrawEntry();
        ret.load(filterForCodec(), getType().ordinal());
        return ret;
    }

    @Override
    protected TransferType getDefaultType() {
        return POTradeWithdrawType.ALL_OF;
    }

    @Override
    public void toggleType() {
        int o = getType().ordinal();
        o++;
        if (o >= POTradeWithdrawType.values().length) {
            o = 0;
        }
        setType(getTypeFrom(o));
    }

    @Override
    protected TransferType getTypeFrom(int type) {
        return POTradeWithdrawType.values()[type];
    }

    @Override
    protected TransferType[] getTypes() {
        return POTradeWithdrawType.values();
    }

    public enum POTradeWithdrawType implements TransferType {
        /** Everything of the item in the withdraw inventory. */
        ALL_OF {
            @Override
            public void doTransfer(IItemHandler storage, IItemHandler move, ItemStack filter) {
                int count = InventoryTools.getCountOf(move, filter);
                if (count > 0) {
                    InventoryTools.transferItems(move, storage, filter, count);
                }
            }
        },
        /** Until the stock holds the quantity of the filter. */
        FILL_TO {
            @Override
            public void doTransfer(IItemHandler storage, IItemHandler move, ItemStack filter) {
                int count = InventoryTools.getCountOf(storage, filter);
                if (count < filter.getCount()) {
                    InventoryTools.transferItems(move, storage, filter, filter.getCount() - count);
                }
            }
        }
    }
}
