/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 8): one selectable recipe of a processing worksite (sawmill, bakery).
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

/**
 * A recipe of a {@link ProcessingWorksiteBlockEntity}: the player switches it on or off and sets how much of its product the settlement should keep in
 * storage. A recipe is a kind of product ("slabs"), not one fixed list of items: {@link #plan} looks at what the input store holds now and says what one
 * batch would use and make (the sawmill picks the log, and so the wood type, from the input store).
 */
public interface ProcessingRecipe {

    /**
     * A raw input the couriers keep stocked in the input store: what matches, and how many (0 or less: the config's {@code production_input_target}).
     * {@code sample} is one item of it, for the name when the settlement reports a site starved of it (empty: not reported).
     */
    record Input(Predicate<ItemStack> matcher, int target, ItemStack sample) {
        public Input(Predicate<ItemStack> matcher, int target) {
            this(matcher, target, ItemStack.EMPTY);
        }
    }

    /**
     * One batch, the work of one task.
     *
     * @param consume what is taken out of the input store (stacks of one exact item each; the counts add up)
     * @param produce what goes into the output store (a container left over, as the bucket of a milk bucket, is one of these)
     * @param ticks   the base duration of the work at the block (a worker of skill 1)
     * @param fuel    the burn time (ticks of a furnace fire) the batch uses, 0 if it needs no fuel
     */
    record Batch(List<ItemStack> consume, List<ItemStack> produce, int ticks, int fuel) {}

    /** The id the settings are saved under; never change it. */
    String id();

    /** The translation key of the name the screen shows. */
    String nameKey();

    /** An item for the icon of the screen. */
    ItemStack icon();

    /** Whether the recipe is on in a new site. */
    default boolean defaultEnabled() {
        return false;
    }

    /** Which items count as the product of this recipe for the "keep N in storage" number. */
    Predicate<ItemStack> product();

    /** The raw inputs of the recipe (the same instance for the same kind of input in different recipes, so couriers get one stock order for it). */
    List<Input> inputs();

    /** Whether the stack may go into the input store because of this recipe (a raw input of it, whatever the amount). */
    boolean usesInput(ItemStack stack);

    /** One batch from what the input store holds now, or null if it cannot be made. Reads only; nothing is taken. */
    @Nullable
    Batch plan(Level level, IItemHandler input);
}
