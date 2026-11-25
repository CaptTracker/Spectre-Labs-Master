/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/cropfarm/CropDefault.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.cropfarm;

import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.WorksiteTools;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Collections;
import java.util.List;

/**
 * The fallback crop handler: ordinary crops that are harvested when fully grown, replanted with one of their own
 * drops and fertilised while they grow.
 *
 * <p><b>1.21 crop rules.</b> 1.12.2 treated every {@code IGrowable} block as a crop (fully grown meant
 * {@code !canGrow}, fertilising meant {@code canGrow}). In 1.21 {@code BonemealableBlock} also covers grass, flowers,
 * saplings and moss, so that rule would waste bone meal on them and break them. A crop is a {@link CropBlock} (wheat,
 * carrots, potatoes, beetroots and every mod crop that extends it; mature is {@code isMaxAge}) or a
 * {@link SweetBerryBushBlock} (mature at age 3). Other plants are added with the data driven handlers
 * ({@code crop_blocks}, for example {@link CropMatureDefined} for nether wart). Stems have {@link CropStem}.
 *
 * <p>Replanting: the first drop that is an item of a plantable block ({@code IPlantable} in 1.12.2: a
 * {@link BlockItem} of a bush, sugar cane or cactus) is placed on the soil where the crop was. If it cannot be
 * placed it stays among the drops (the original lost it).
 */
public class CropDefault implements ICrop {

    /** Block classes that count as a plant a farm may sow (1.12.2: {@code IPlantable}). */
    public static boolean isPlantableBlock(Block block) {
        return block instanceof BushBlock || block instanceof SugarCaneBlock || block instanceof CactusBlock;
    }

    /** An item that places a plantable block (seeds, carrots, potatoes, saplings, nether wart, ...). */
    public static boolean isPlantableItem(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && isPlantableBlock(blockItem.getBlock());
    }

    /** The blocks the default handler harvests and fertilises. */
    protected static boolean isCrop(Block block) {
        return block instanceof CropBlock || block instanceof SweetBerryBushBlock;
    }

    /** True if the crop is fully grown. */
    protected boolean isMature(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (block instanceof SweetBerryBushBlock) {
            return state.getValue(SweetBerryBushBlock.AGE) >= SweetBerryBushBlock.MAX_AGE;
        }
        return false;
    }

    @Override
    public List<BlockPos> getPositionsToHarvest(Level level, BlockPos pos, BlockState state) {
        if (isMature(state)) {
            return Collections.singletonList(pos);
        }
        return Collections.emptyList();
    }

    @Override
    public boolean canBeFertilized(BlockState state, Level level, BlockPos pos) {
        return isCrop(state.getBlock()) && state.getBlock() instanceof BonemealableBlock growable
                && growable.isValidBonemealTarget(level, pos, state);
    }

    @Override
    public boolean harvest(Level level, BlockState state, BlockPos pos, int fortune, IItemHandler inventory) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        List<ItemStack> drops = getDrops(serverLevel, pos, state, fortune);
        if (!InventoryTools.canInventoryHold(inventory, drops)) {
            return false;
        }
        if (!breakCrop(level, pos, state)) {
            return false;
        }
        NonNullList<ItemStack> stacks = InventoryTools.toNonNullList(drops);
        ItemStack plantable = InventoryTools.removeItem(stacks, CropDefault::isPlantableItem, 1);
        if (!plantable.isEmpty() && !BlockTools.placeItemBlock(plantable, level, pos, Direction.UP)) {
            stacks.add(plantable); // could not be replanted, keep it
        }
        InventoryTools.insertOrDropItems(inventory, stacks, level, pos);
        return true;
    }

    protected boolean breakCrop(Level level, BlockPos pos, BlockState state) {
        return BlockTools.breakBlockNoDrops(level, pos, state);
    }

    protected List<ItemStack> getDrops(ServerLevel level, BlockPos pos, BlockState state, int fortune) {
        return WorksiteTools.getDrops(level, pos, state, fortune);
    }

    @Override
    public boolean matches(BlockState state) {
        return true;
    }

    @Override
    public boolean matches(ItemStack stack) {
        return true;
    }

    @Override
    public boolean isPlantable(ItemStack stack) {
        return isPlantableItem(stack);
    }
}
