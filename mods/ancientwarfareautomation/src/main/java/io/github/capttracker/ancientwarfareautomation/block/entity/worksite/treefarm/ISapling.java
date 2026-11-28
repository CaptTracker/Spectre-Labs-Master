/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/treefarm/ISapling.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.treefarm;

import net.minecraft.world.item.ItemStack;

/** An item the tree farm can plant. */
public interface ISapling {
    boolean matches(ItemStack stack);

    /** True if the item is planted by using it ({@code useItemRightClick} in 1.12.2) instead of placing it against the soil. */
    boolean isRightClick();

    /** True if the plantable plants itself through {@link #plant} instead of being placed or used by the fake player (the seeds of Dynamic Trees). */
    default boolean plantsItself() {
        return false;
    }

    /**
     * Plants one of the item at the position (a replaceable position above soil); the caller takes the item from the inventory when this returns true.
     * Only called for {@link #plantsItself()}.
     */
    default boolean plant(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, ItemStack stack) {
        return false;
    }
}
