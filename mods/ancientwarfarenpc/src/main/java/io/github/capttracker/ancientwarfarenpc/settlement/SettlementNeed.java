/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 8): one thing a settlement is short of.
 */
package io.github.capttracker.ancientwarfarenpc.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * One need of a settlement, from {@link SettlementNeeds}: what is short ({@link #matcher} says which items count), how many are missing, how urgent it
 * is, where it is needed (the position of the worker or soldier who lacks it, for the couriers) and which production site works on it. Server side;
 * {@link #count}, {@link #status} and the producer fields change when the settlement recounts or a site reports a product.
 */
public final class SettlementNeed {
    /** The kind of need; the order is the order of the screen only by priority, see {@link #priority}. */
    public enum Category { TOOL, WEAPON, ARMOUR, FOOD, FUEL, MATERIAL, INPUT, EXTRA }

    /** What the production sites do about the need. */
    public enum Status {
        /** A site is making it now. */
        MAKING,
        /** A site is set up to make it but is not working on it yet (busy, or short of inputs). */
        QUEUED,
        /** A site has a recipe for it that is switched off (only when {@code demand_enables_recipes} is false). */
        OFF,
        /** No site has a recipe for it. */
        NONE
    }

    public final String id;
    public final Category category;
    /** The translation key of the name shown on the screen. */
    public final String label;
    public final Predicate<ItemStack> matcher;
    public final int priority;
    /** Where it is needed (a worker or soldier that lacks it), or null. */
    @Nullable
    public final BlockPos where;
    /** How many are missing. */
    public int count;
    public Status status = Status.NONE;
    /** The site that makes it, or the one that could. */
    @Nullable
    public BlockPos producer;
    /** The description id of the block of that site, empty if none. */
    public String producerBlock = "";
    /** The lang key of the hint shown when no site can make it, empty if none. */
    public String hint = "";

    public SettlementNeed(String id, Category category, String label, Predicate<ItemStack> matcher, int count, int priority, @Nullable BlockPos where) {
        this.id = id;
        this.category = category;
        this.label = label;
        this.matcher = matcher;
        this.count = count;
        this.priority = priority;
        this.where = where;
    }

    /** Whether the stack fills this need. */
    public boolean accepts(ItemStack stack) {
        return count > 0 && !stack.isEmpty() && matcher.test(stack);
    }
}
