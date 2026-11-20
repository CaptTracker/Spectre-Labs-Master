/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/fruitfarm/IFruit.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.fruitfarm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/** One kind of fruit the fruit farm can pick (and, for some, plant). The handlers are kept in the {@code FruitFarmRegistry}. */
public interface IFruit {
    boolean matches(BlockState state);

    /** Whether the item plants this fruit (only fruits that {@link #isPlantable() can be planted}). */
    default boolean matches(ItemStack stack) {
        return false;
    }

    boolean isRipe(BlockState state);

    /**
     * Picks the fruit at the position with the given fortune level.
     *
     * @param inventory where the drops go; nothing is picked if they do not fit
     * @return true if the fruit was picked
     */
    boolean pick(Level level, BlockState state, BlockPos pos, int fortune, IItemHandler inventory);

    boolean isPlantable();

    default boolean canPlant(Level level, BlockPos currentPos, BlockState state) {
        return false;
    }

    default boolean plant(Level level, BlockPos plantPos) {
        return false;
    }
}
