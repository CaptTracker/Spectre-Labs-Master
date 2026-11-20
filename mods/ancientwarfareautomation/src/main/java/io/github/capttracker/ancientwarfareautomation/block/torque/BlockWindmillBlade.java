/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/block/BlockWindmillBlade.
 */
package io.github.capttracker.ancientwarfareautomation.block.torque;

import io.github.capttracker.ancientwarfare.block.BaseBlock;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.multiblock.TileWindmillBlade;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * A blade block of a windmill ({@code windmill_blade}). It has no state of its own: the block entity validates the
 * blade plane and the centre one controls it. Flammable (fire spread 60, flammability 20), as the wooden original.
 */
public class BlockWindmillBlade extends BaseBlock implements EntityBlock, IBlockEntityTypeProvider {
    private final Supplier<? extends BlockEntityType<?>> blockEntityType;

    /** 1.12.2: {@code Material.WOOD}, hardness 2, not opaque. */
    public static BlockBehaviour.Properties defaultProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion();
    }

    public BlockWindmillBlade(BlockBehaviour.Properties properties, Supplier<? extends BlockEntityType<?>> blockEntityType) {
        super(properties);
        this.blockEntityType = blockEntityType;
    }

    @Override
    public BlockEntityType<?> getBlockEntityType() {
        return blockEntityType.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileWindmillBlade(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != blockEntityType.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof TileWindmillBlade blade) {
                blade.update();
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TileWindmillBlade blade) {
            blade.blockPlaced();
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        TileWindmillBlade blade = level.getBlockEntity(pos) instanceof TileWindmillBlade found ? found : null;
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (blade != null && !state.is(newState.getBlock())) {
            blade.blockBroken(); //called after the block is gone, so the tile properly sees the block/tile as gone
        }
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 60;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 20;
    }
}
