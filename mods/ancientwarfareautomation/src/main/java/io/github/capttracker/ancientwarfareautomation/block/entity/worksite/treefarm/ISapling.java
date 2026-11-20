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
}
