/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/util/parsing/ItemStackMatcher.
 */
package io.github.capttracker.ancientwarfare.util.parsing;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * Matches stacks of one item and, unless told to ignore it, the same data component changes.
 * 1.12.2 also matched the damage value ("meta"); that no longer exists, so there is no meta test.
 * A matcher without components only accepts stacks that carry no changes to the item's defaults.
 */
public class ItemStackMatcher implements Predicate<ItemStack> {
    private final Item item;
    @Nullable
    private final DataComponentPatch components;
    private final boolean ignoreComponents;

    private ItemStackMatcher(Item item, @Nullable DataComponentPatch components, boolean ignoreComponents) {
        this.item = item;
        this.components = components;
        this.ignoreComponents = ignoreComponents;
    }

    @Override
    public boolean test(ItemStack input) {
        if (input.getItem() != item) {
            return false;
        }
        if (ignoreComponents) {
            return true;
        }
        DataComponentPatch expected = components == null ? DataComponentPatch.EMPTY : components;
        return expected.equals(input.getComponentsPatch());
    }

    public Item getItem() {
        return item;
    }

    public static class Builder {
        private final Item item;
        @Nullable
        private DataComponentPatch components = null;
        private boolean ignoreComponents = false;

        public Builder(Item item) {
            this.item = item;
        }

        public Builder setComponents(@Nullable DataComponentPatch components) {
            this.components = components;
            return this;
        }

        public Builder setIgnoreComponents(boolean ignoreComponents) {
            this.ignoreComponents = ignoreComponents;
            return this;
        }

        public ItemStackMatcher build() {
            return new ItemStackMatcher(item, components, ignoreComponents);
        }
    }
}
