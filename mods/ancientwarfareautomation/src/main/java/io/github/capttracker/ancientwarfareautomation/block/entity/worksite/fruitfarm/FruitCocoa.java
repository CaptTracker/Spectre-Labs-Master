/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/fruitfarm/FruitCocoa.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.fruitfarm;

import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.WorksiteTools;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Cocoa pods, the one fruit of the fruit farm that is built in. Ripe at age 2; picking resets the pod to age 0 and
 * keeps one of the beans it dropped as the "replanted" one. Cocoa beans are planted on an empty position next to a
 * jungle log (the placement rules of the block itself decide: {@code canSurvive}).
 */
public class FruitCocoa implements IFruit {

    @Override
    public boolean matches(BlockState state) {
        return state.is(Blocks.COCOA);
    }

    /** The cocoa bean (1.12.2: dye with the brown dye damage). */
    @Override
    public boolean matches(ItemStack stack) {
        return stack.is(Items.COCOA_BEANS);
    }

    /** The cocoa state on the position, attached to the log in the direction {@code side}, or null if no log holds it. */
    @Nullable
    private static BlockState attachedState(Level level, BlockPos pos, Direction side) {
        BlockState cocoa = Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, side).setValue(CocoaBlock.AGE, 0);
        return cocoa.canSurvive(level, pos) ? cocoa : null;
    }

    @Nullable
    private static BlockState findPlantState(Level level, BlockPos pos) {
        if (!level.isEmptyBlock(pos)) {
            return null;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockState state = attachedState(level, pos, side);
            if (state != null) {
                return state;
            }
        }
        return null;
    }

    @Override
    public boolean canPlant(Level level, BlockPos pos, BlockState state) {
        return findPlantState(level, pos) != null;
    }

    @Override
    public boolean plant(Level level, BlockPos plantPos) {
        BlockState plantState = findPlantState(level, plantPos);
        return plantState != null && level.setBlock(plantPos, plantState, Block.UPDATE_ALL);
    }

    @Override
    public boolean isRipe(BlockState state) {
        return state.getValue(CocoaBlock.AGE) == 2;
    }

    @Override
    public boolean pick(Level level, BlockState state, BlockPos pos, int fortune, IItemHandler inventory) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        List<ItemStack> dropList = WorksiteTools.getDrops(serverLevel, pos, state, fortune);
        if (!InventoryTools.canInventoryHold(inventory, dropList)) {
            return false;
        }
        NonNullList<ItemStack> drops = InventoryTools.toNonNullList(dropList);
        BlockState newState = state.setValue(CocoaBlock.AGE, 0);
        level.setBlock(pos, newState, Block.UPDATE_ALL);
        level.levelEvent(2001, pos, Block.getId(newState));
        //remove that one cocoa bean that was just "replanted"
        InventoryTools.removeItem(drops, s -> s.is(Items.COCOA_BEANS), 1);
        InventoryTools.insertOrDropItems(inventory, drops, level, pos);
        return true;
    }

    @Override
    public boolean isPlantable() {
        return true;
    }
}
