/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/block/BlockFlywheelStorage.
 */
package io.github.capttracker.ancientwarfareautomation.block.torque;

import io.github.capttracker.ancientwarfare.block.BaseBlock;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.multiblock.TileFlywheelStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
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
 * A block of a flywheel storage stack ({@code flywheel_storage_light}, {@code _medium}, {@code _heavy}). All state is in the
 * block entity. The three tiers share one block entity type (the tile read the tier from the metadata; it reads it
 * from the block now).
 *
 * <p>The 1.12.2 block drew in the solid and translucent layers (the render layers are a client matter now: the model or
 * the renderer says so) and was not an opaque or full cube ({@code noOcclusion()} in {@link #defaultProperties()}).
 */
public class BlockFlywheelStorage extends BaseBlock implements EntityBlock, IBlockEntityTypeProvider, ITieredBlock {
    private final TorqueTier tier;
    private final Supplier<? extends BlockEntityType<?>> blockEntityType;

    /** 1.12.2: {@code Material.ROCK}, hardness 2, not opaque. */
    public static BlockBehaviour.Properties defaultProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0F).sound(SoundType.STONE).noOcclusion();
    }

    public BlockFlywheelStorage(BlockBehaviour.Properties properties, TorqueTier tier, Supplier<? extends BlockEntityType<?>> blockEntityType) {
        super(properties);
        this.tier = tier;
        this.blockEntityType = blockEntityType;
    }

    @Override
    public TorqueTier getTier() {
        return tier;
    }

    @Override
    public BlockEntityType<?> getBlockEntityType() {
        return blockEntityType.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileFlywheelStorage(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != blockEntityType.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof TileFlywheelStorage storage) {
                storage.update();
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TileFlywheelStorage storage) {
            storage.blockPlaced();
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        TileFlywheelStorage storage = level.getBlockEntity(pos) instanceof TileFlywheelStorage found ? found : null;
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (storage != null && !state.is(newState.getBlock())) {
            //called after the block is gone, so the neighbours that re-validate see it as gone
            storage.blockBroken();
        }
    }
}
