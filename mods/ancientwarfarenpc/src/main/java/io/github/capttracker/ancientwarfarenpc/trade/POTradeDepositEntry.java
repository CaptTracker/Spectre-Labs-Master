/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/trade/POTradeDepositEntry.
 */
package io.github.capttracker.ancientwarfarenpc.trade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/** A line of the deposit list: items the trader puts into the deposit inventory when it restocks. */
public final class POTradeDepositEntry extends POTradeTransferEntry {
    public static final Codec<POTradeDepositEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.CODEC.lenientOptionalFieldOf("item").forGetter(POTradeTransferEntry::filterForCodec),
            Codec.INT.optionalFieldOf("type", 0).forGetter(entry -> entry.getType().ordinal())
    ).apply(instance, (filter, type) -> {
        POTradeDepositEntry entry = new POTradeDepositEntry();
        entry.load(filter, type);
        return entry;
    }));

    public POTradeDepositEntry copy() {
        POTradeDepositEntry ret = new POTradeDepositEntry();
        ret.load(filterForCodec(), getType().ordinal());
        return ret;
    }

    @Override
    public TransferType getDefaultType() {
        return POTradeDepositType.ALL_OF;
    }

    @Override
    public void toggleType() {
        int o = getType().ordinal();
        o++;
        if (o >= POTradeDepositType.values().length) {
            o = 0;
        }
        setType(POTradeDepositType.values()[o]);
    }

    @Override
    protected TransferType getTypeFrom(int type) {
        return POTradeDepositType.values()[type];
    }

    @Override
    protected TransferType[] getTypes() {
        return POTradeDepositType.values();
    }

    public enum POTradeDepositType implements TransferType {
        /** Everything of the item in the storage. */
        ALL_OF {
            @Override
            public void doTransfer(IItemHandler storage, IItemHandler move, ItemStack filter) {
                int count = InventoryTools.getCountOf(storage, filter);
                if (count > 0) {
                    InventoryTools.transferItems(storage, move, filter, count);
                }
            }
        },
        /** Up to the quantity of the filter. */
        QUANTITY {
            @Override
            public void doTransfer(IItemHandler storage, IItemHandler move, ItemStack filter) {
                int count = InventoryTools.getCountOf(storage, filter);
                if (count > filter.getCount()) {
                    count = filter.getCount();
                }
                InventoryTools.transferItems(storage, move, filter, count);
            }
        },
        /** What the storage holds above the quantity of the filter. */
        DEPOSIT_EXCESS {
            @Override
            public void doTransfer(IItemHandler storage, IItemHandler move, ItemStack filter) {
                int count = InventoryTools.getCountOf(storage, filter);
                if (count > filter.getCount()) {
                    InventoryTools.transferItems(storage, move, filter, count - filter.getCount());
                }
            }
        }
    }
}
