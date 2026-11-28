/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (optional integration): what the tree farm may ask about Dynamic Trees, without ever naming one of its classes.
 */
package io.github.capttracker.ancientwarfareautomation.compat.dynamictrees;

import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;

/**
 * The entry point of the Dynamic Trees integration of the tree farm. It names no class of Dynamic Trees: the real work is behind {@link Adapter}, which
 * {@link DynamicTreesFarmCompat} installs only when the mod is loaded. Without the mod, or with {@code dynamic_trees.enabled} off, every question here answers
 * "no" and the tree farm behaves as it always did.
 */
public final class DynamicTreesSupport {
    public static final String MOD_ID = "dynamictrees";

    private static final TagKey<Block> ROOTY_SOIL = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MOD_ID, "rooty_soil"));
    private static final TagKey<Block> SAPLINGS = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MOD_ID, "saplings"));

    /** What the felling of one tree gave: the block it was cut at and the seed of its species that comes with the felling (empty if the species has none). */
    public record Felled(BlockPos foot, ItemStack seed) {
    }

    /** The calls that need Dynamic Trees classes; implemented in {@link DynamicTreesAdapter}. */
    public interface Adapter {
        /** A branch block of a trunk or limb (not the roots). */
        boolean isTrunkBranch(BlockState state);

        /**
         * Fells the tree the branch at {@code pos} belongs to the way a player's axe does, and watches for the items the fall drops.
         *
         * @return null if nothing was felled
         */
        @Nullable
        Felled fell(ServerLevel level, BlockPos pos, @Nullable LivingEntity actor, int fortune, BlockEntity site, IItemHandler collector);
    }

    @Nullable
    private static volatile Adapter adapter;

    private DynamicTreesSupport() {
    }

    static void install(Adapter installed) {
        adapter = installed;
    }

    /** True if the mod is loaded, the integration started and the config switch is on. */
    public static boolean isActive() {
        return adapter != null && AWAutomationStatics.dynamicTreesEnabled;
    }

    /** A block of a Dynamic Trees trunk or limb (not roots), while the integration is active. */
    public static boolean isTrunkBranch(BlockState state) {
        Adapter current = adapter;
        return current != null && AWAutomationStatics.dynamicTreesEnabled && current.isTrunkBranch(state);
    }

    /** The rooty soil under a Dynamic Trees tree (it counts as ground of the tree farm), while the integration is active. */
    public static boolean isRootySoil(BlockState state) {
        return isActive() && state.is(ROOTY_SOIL);
    }

    /** A Dynamic Trees sapling (a growing tree of the farm), while the integration is active. */
    public static boolean isSapling(BlockState state) {
        return isActive() && state.is(SAPLINGS);
    }

    /**
     * Fells the tree. Never throws: an error logs once and the integration switches itself off for the session.
     *
     * @param actor the worker whose main hand item is the axe, or null for a machine (a virtual axe with the fortune of the site is used)
     */
    @Nullable
    public static Felled fell(Level level, BlockPos pos, @Nullable LivingEntity actor, int fortune, BlockEntity site, IItemHandler collector) {
        Adapter current = adapter;
        if (current == null || !AWAutomationStatics.dynamicTreesEnabled || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        try {
            return current.fell(serverLevel, pos, actor, fortune, site, collector);
        } catch (Throwable error) {
            DynamicTreesFarmCompat.LOGGER.warn("Felling a Dynamic Trees tree failed; the integration is switched off for this session", error);
            adapter = null;
            return null;
        }
    }
}
