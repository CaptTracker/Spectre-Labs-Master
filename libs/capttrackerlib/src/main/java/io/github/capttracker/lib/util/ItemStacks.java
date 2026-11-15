package io.github.capttracker.lib.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Small ItemStack helpers that old mods tend to re-implement. */
public final class ItemStacks {
    private ItemStacks() {}

    /** Gives the stack to the player, dropping whatever doesn't fit at their feet. */
    public static void giveOrDrop(Player player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    /** True if both stacks are the same item with identical components (1.12.2's areItemStacksEqual + NBT check). */
    public static boolean sameItemAndData(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameComponents(a, b);
    }
}
