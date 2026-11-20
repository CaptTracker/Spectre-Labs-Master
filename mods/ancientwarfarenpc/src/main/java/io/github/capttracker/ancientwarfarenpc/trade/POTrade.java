/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/trade/POTrade.
 */
package io.github.capttracker.ancientwarfarenpc.trade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfarenpc.orders.OrderCodecs;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * A trade of a player owned trader: the stock is the backpack the trader holds. Keeps the compacted input and output (one stack per item, split
 * at the maximum stack size) to check quickly whether the trade can be done.
 */
public class POTrade extends Trade {
    /** {@code inputItems} and {@code outputItems}: the stacks with their slot. */
    public static final Codec<POTrade> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            OrderCodecs.SLOT_STACKS.optionalFieldOf("inputItems", List.of()).forGetter(Trade::inputSlots),
            OrderCodecs.SLOT_STACKS.optionalFieldOf("outputItems", List.of()).forGetter(Trade::outputSlots)
    ).apply(instance, POTrade::fromSlots));

    private NonNullList<ItemStack> compactInput = NonNullList.create();
    private NonNullList<ItemStack> compactOutput = NonNullList.create();

    private static POTrade fromSlots(List<OrderCodecs.SlotStack> inputSlots, List<OrderCodecs.SlotStack> outputSlots) {
        POTrade trade = new POTrade();
        trade.setSlots(inputSlots, outputSlots);
        trade.updateCompactInput();
        trade.updateCompactOutput();
        return trade;
    }

    public POTrade copy() {
        return fromSlots(inputSlots(), outputSlots());
    }

    @Override
    public void setInputStack(int index, ItemStack stack) {
        super.setInputStack(index, stack);
        updateCompactInput();
    }

    @Override
    public void setOutputStack(int index, ItemStack stack) {
        super.setOutputStack(index, stack);
        updateCompactOutput();
    }

    private void updateCompactInput() {
        NonNullList<ItemStack> list = NonNullList.create();
        for (ItemStack temp : input) {
            if (!temp.isEmpty()) {
                list.add(temp.copy());
            }
        }
        compactInput = InventoryTools.compactStackList(list);
    }

    private void updateCompactOutput() {
        NonNullList<ItemStack> list = NonNullList.create();
        for (ItemStack temp : output) {
            if (!temp.isEmpty()) {
                list.add(temp.copy());
            }
        }
        compactOutput = InventoryTools.compactStackList(list);
    }

    /**
     * Checks that the storage holds everything the trade gives away and has room for everything the customer pays.
     */
    public boolean isAvailable(IItemHandler storage) {
        for (ItemStack stack : compactOutput) {
            if (InventoryTools.getCountOf(storage, stack) < stack.getCount()) {
                return false;
            }
        }
        return InventoryTools.canInventoryHold(storage, compactInput);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof POTrade other && sameStacks(other);
    }

    @Override
    public int hashCode() {
        return stacksHash();
    }
}
