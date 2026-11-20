/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 2): the view of a worksite that auto-hauling couriers use.
 */
package io.github.capttracker.ancientwarfare.api;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;
import java.util.function.Predicate;

/**
 * What a courier may move at a worksite: the inventories that fill up with products (output) and the inventories that must be kept stocked with
 * seeds, saplings, bone meal, fuel or tools (input). Implemented by the worksite block entities. The lists are cheap to build and are asked for
 * at most a few times per second per courier.
 */
public interface IWorksiteLogistics {

    /**
     * An inventory that products are taken from.
     *
     * @param inventory the inventory
     * @param takes     which stacks of it a courier may take (an item the site still needs as input is not taken out again)
     */
    record Output(IItemHandler inventory, Predicate<ItemStack> takes) {}

    /**
     * An inventory that should hold at least {@code target} items that match {@code accepts}.
     */
    record Need(IItemHandler inventory, Predicate<ItemStack> accepts, int target) {
        /** How many more matching items the inventory should get, 0 if it is stocked. */
        public int deficit() {
            int have = 0;
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (!stack.isEmpty() && accepts.test(stack)) {
                    have += stack.getCount();
                }
            }
            return Math.max(0, target - have);
        }
    }

    /** The output inventories. */
    List<Output> getLogisticsOutputs();

    /** The input needs; empty if the site takes no input from outside. */
    default List<Need> getLogisticsNeeds() {
        return List.of();
    }
}
