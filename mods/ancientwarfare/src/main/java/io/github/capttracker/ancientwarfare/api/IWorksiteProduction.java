/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 8): what the settlement may ask of a production worksite.
 */
package io.github.capttracker.ancientwarfare.api;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Predicate;

/**
 * Implemented by the production worksites (sawmill, bakery, smithy, tannery, and the smelter for its fuel). The settlement uses it to tell, for each need
 * it has, which site is making the product and whether there is a site that could, and to find what the sites are starved of.
 */
public interface IWorksiteProduction {

    /**
     * A raw input or fuel a site would use but has none of.
     *
     * @param sample  one item of it, for the name
     * @param matcher which items count
     * @param missing how many the site would like to have
     */
    record StarvedInput(ItemStack sample, Predicate<ItemStack> matcher, int missing) {}

    /** Whether the site has a recipe (on or off) for a product that matches. */
    default boolean hasRecipeFor(Predicate<ItemStack> wanted) {
        return false;
    }

    /** Whether the site would make such a product now if it was asked to: a recipe for it that is on, or that the settlement's demand turns on. */
    default boolean canMake(Predicate<ItemStack> wanted) {
        return false;
    }

    /** Whether the site is making such a product right now (the batch it would start is one). */
    default boolean isMaking(Predicate<ItemStack> wanted) {
        return false;
    }

    /** The inputs and fuel of its active recipes that the site has none of. */
    default List<StarvedInput> getStarvedInputs() {
        return List.of();
    }
}
