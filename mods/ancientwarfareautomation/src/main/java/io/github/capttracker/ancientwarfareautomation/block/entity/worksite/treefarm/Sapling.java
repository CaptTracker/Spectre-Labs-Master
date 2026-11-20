/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/treefarm/Sapling.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.treefarm;

import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

public class Sapling implements ISapling {
    private final Predicate<ItemStack> itemStackPredicate;
    private final boolean rightClick;

    public Sapling(Predicate<ItemStack> itemStackPredicate, boolean rightClick) {
        this.itemStackPredicate = itemStackPredicate;
        this.rightClick = rightClick;
    }

    @Override
    public boolean matches(ItemStack stack) {
        return itemStackPredicate.test(stack);
    }

    @Override
    public boolean isRightClick() {
        return rightClick;
    }
}
