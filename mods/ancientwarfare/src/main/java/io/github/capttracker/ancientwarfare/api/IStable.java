/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): the view of a stable that the stable hand NPC uses.
 */
package io.github.capttracker.ancientwarfare.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Optional;
import java.util.UUID;

/**
 * A stable (the worksite block of the Automation module): a feed store and a radius in which the stable hand looks after horses, donkeys and mules. A stable
 * hand NPC reports in with {@link #staff}. Implemented by the block entity; the NPC module finds stables through {@link WorksiteRegistry}. Server thread only.
 */
public interface IStable {
    /** Ticks after the last report of the stable hand until the stable counts as unstaffed. */
    int STAFF_TIMEOUT = 60;

    /** Whether an item is feed for equines: wheat, hay, apples, carrots, sugar and the golden foods. */
    static boolean isFeed(ItemStack stack) {
        return stack.is(Items.WHEAT) || stack.is(Items.HAY_BLOCK) || stack.is(Items.APPLE) || stack.is(Items.CARROT) || stack.is(Items.SUGAR)
                || isBreedingFeed(stack);
    }

    /** Whether an item puts a tamed adult equine in love: a golden carrot or a golden apple. */
    static boolean isBreedingFeed(ItemStack stack) {
        return stack.is(Items.GOLDEN_CARROT) || stack.is(Items.GOLDEN_APPLE);
    }

    BlockPos getStablePos();

    /** Where the stable hand stands: the block in front of the stable. */
    BlockPos getStandPos();

    /** The radius (blocks) in which the stable hand cares for equines and looks for carts. */
    int getRadius();

    /** The most horses, donkeys and mules the stable hand lets live in the radius of the stable (the breeding stops there). */
    int getMaxEquines();

    /** The feed store: wheat, hay, apples, carrots and golden food that the couriers bring from the settlement's storage. */
    IItemHandler getFeedStore();

    boolean isStaffed();

    Optional<UUID> getKeeper();

    /** The stable hand reports in; false if another stable hand holds the stable. */
    boolean staff(UUID keeper, long gameTime);

    void release(UUID keeper);
}
