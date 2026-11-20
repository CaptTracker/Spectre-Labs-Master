/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/block/BlockWarehouseStorage.
 */
package io.github.capttracker.ancientwarfareautomation.block.warehouse;

import io.github.capttracker.ancientwarfare.block.BaseBlock;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.ControlledBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseStorageBlockEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import io.github.capttracker.ancientwarfareautomation.init.WarehouseSize;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * A warehouse storage block ({@code warehouse_storage_small}, {@code _medium}, {@code _large}). The three sizes were the
 * metadata of one block in 1.12.2; each is its own block now, sharing one block entity type
 * ({@link WarehouseStorageBlockEntity} reads the size from the block). Right-click opens the storage screen (the block
 * entity does that, {@code BaseBlock} dispatches the click); the stored items drop when the block is broken (the block
 * entity is an {@code IBlockBreakHandler}).
 */
public class WarehouseStorageBlock extends BaseBlock implements EntityBlock {
    private final WarehouseSize size;

    public WarehouseStorageBlock(BlockBehaviour.Properties properties, WarehouseSize size) {
        super(properties);
        this.size = size;
    }

    public WarehouseSize getSize() {
        return size;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WarehouseStorageBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return blockEntityType == AwAutomationBase.WAREHOUSE_STORAGE_BE.get() ? ControlledBlockEntity.ticker(level) : null;
    }
}
