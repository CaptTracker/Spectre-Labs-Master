/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 8): a hook for the stock of goods a production worksite works toward.
 */
package io.github.capttracker.ancientwarfare.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * Lets a production worksite (the sawmill and the bakery of the automation module, which cannot see settlements) ask how much of a product its
 * settlement already holds in storage, for the "keep N in storage" setting of a recipe. The NPC module sets the {@link Counter} when it loads;
 * without one (no NPC module) nothing counts as stored. Server thread only.
 */
public final class WorksiteStock {
    /** Counts the items that match in the storages of the settlement that holds the position. */
    @FunctionalInterface
    public interface Counter {
        int count(ServerLevel level, BlockPos site, Predicate<ItemStack> matcher);
    }

    @Nullable
    private static Counter counter;

    private WorksiteStock() {}

    public static void setCounter(@Nullable Counter value) {
        counter = value;
    }

    /** The matching items in the storages of the settlement around the site, 0 if there is no settlement or no counter. */
    public static int count(ServerLevel level, BlockPos site, Predicate<ItemStack> matcher) {
        Counter active = counter;
        return active == null ? 0 : active.count(level, site, matcher);
    }
}
