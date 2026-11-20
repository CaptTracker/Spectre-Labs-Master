/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/util/SortItemsFirstComparator.
 */
package io.github.capttracker.ancientwarfare.util;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Sorts stacks of the given items, blocks, item classes or block classes first, in the order they were given;
 * everything else compares equal (so a stable sort keeps its order).
 */
public class SortItemsFirstComparator implements Comparator<ItemStack> {
    private final Map<Predicate<ItemStack>, Integer> firstElements = new HashMap<>();

    /** @param firstElements {@link Item}, {@link Block}, or {@code Class} objects of either kind */
    public SortItemsFirstComparator(Object... firstElements) {
        for (int i = 0; i < firstElements.length; i++) {
            Object element = firstElements[i];

            Predicate<ItemStack> matches;
            if (element instanceof Item) {
                matches = s -> s.getItem() == element;
            } else if (element instanceof Block) {
                matches = s -> s.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == element;
            } else if (element instanceof Class<?> clazz && Block.class.isAssignableFrom(clazz)) {
                matches = s -> s.getItem() instanceof BlockItem blockItem && clazz.isAssignableFrom(blockItem.getBlock().getClass());
            } else if (element instanceof Class<?> clazz && Item.class.isAssignableFrom(clazz)) {
                matches = s -> clazz.isAssignableFrom(s.getItem().getClass());
            } else {
                continue;
            }

            this.firstElements.put(matches, firstElements.length - i);
        }
    }

    @Override
    public int compare(ItemStack o1, ItemStack o2) {
        if (o1 == o2 || o1.getItem() == o2.getItem()) {
            return 0;
        }

        int sortWeight1 = 0;
        int sortWeight2 = 0;
        for (Map.Entry<Predicate<ItemStack>, Integer> entry : firstElements.entrySet()) {
            if (entry.getKey().test(o1)) {
                sortWeight1 = entry.getValue();
            }
            if (entry.getKey().test(o2)) {
                sortWeight2 = entry.getValue();
            }

            if (sortWeight1 > 0 && sortWeight2 > 0) {
                break;
            }
        }

        return sortWeight2 - sortWeight1;
    }
}
