/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (third round, mod integrations): rice paddies for the crop farm (Farmer's Delight). No class of the other mod is used: the rice is named by
 * the registry id in the config (paddy_crop_items), and "can it stand here" is asked of the block itself.
 */
package io.github.capttracker.ancientwarfareautomation.compat.farmersdelight;

import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rice grows in a flooded cell: one block of water over soil, open to the air. The crop farm treats such a cell as a place to plant, but only for the items of
 * {@code farmers_delight.paddy_crop_items} (the rice item); every other seed keeps out of the water. The rice block stays in the water and grows the panicles
 * above it, which the farm harvests like any other crop (see {@link RicePanicles}). Everything is off when Farmer's Delight is not installed or the switch
 * {@code farmers_delight.enabled} is off.
 */
public final class RiceFields {
    private static List<String> resolvedFor = List.of();
    private static Set<Block> paddyBlocks = Set.of();
    private static Set<Item> paddyItems = Set.of();

    private RiceFields() {}

    /** Whether paddies are in use at all. */
    public static boolean enabled() {
        return AWAutomationStatics.farmersDelightEnabled && ModList.get().isLoaded("farmersdelight") && !paddyBlocks().isEmpty();
    }

    private static synchronized Set<Block> paddyBlocks() {
        List<String> wanted = AWAutomationStatics.paddyCropItems;
        if (wanted != resolvedFor) {
            Set<Item> items = new HashSet<>();
            Set<Block> blocks = new HashSet<>();
            for (String id : wanted) {
                ResourceLocation key = ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
                if (key == null) {
                    continue;
                }
                BuiltInRegistries.ITEM.getOptional(key).ifPresent(item -> {
                    if (item instanceof BlockItem blockItem) {
                        items.add(item);
                        blocks.add(blockItem.getBlock());
                    }
                });
            }
            paddyItems = items;
            paddyBlocks = blocks;
            resolvedFor = wanted;
        }
        return paddyBlocks;
    }

    /** True for an item the farm plants in a flooded cell. */
    public static boolean isPaddyItem(ItemStack stack) {
        return enabled() && paddyItems.contains(stack.getItem());
    }

    private static boolean isPaddyPlant(BlockState state) {
        return paddyBlocks().contains(state.getBlock());
    }

    /** A flooded cell: a source block of water with air (or something that is replaced) and no fluid above it. */
    public static boolean isPaddyCell(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.WATER) || !state.getFluidState().isSource() || !state.getFluidState().is(Fluids.WATER)) {
            return false;
        }
        BlockState above = level.getBlockState(pos.above());
        return above.isAir() || (above.canBeReplaced() && above.getFluidState().isEmpty() && !above.blocksMotion());
    }

    /** Whether the block of the item would stand in the cell (its own survival rule: soil below, water in the cell). */
    public static boolean canPlantPaddy(Level level, BlockPos cell, ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock().defaultBlockState().canSurvive(level, cell);
    }

    /**
     * The position the farm scans for a column that has no dry surface (the top of it is water, or a rice plant): the flooded cell where rice can be planted, the
     * space above a rice plant, or the panicles that stand on one. Null if the column is not a paddy.
     */
    @Nullable
    public static BlockPos findPaddy(Level level, int x, int z, int minY, int maxY) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = maxY; y >= minY; y--) {
            cursor.set(x, y, z);
            BlockState state = level.getBlockState(cursor);
            if (state.isAir()) {
                continue;
            }
            if (isPaddyPlant(state)) {
                return cursor.above().immutable();
            }
            if (isPaddyPlant(level.getBlockState(cursor.below()))) {
                return cursor.immutable(); // what stands on the rice plant: the panicles
            }
            return isPaddyCell(level, cursor) ? cursor.immutable() : null;
        }
        return null;
    }
}
