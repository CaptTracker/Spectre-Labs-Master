/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/block/BlockWarehouseInterface.
 */
package io.github.capttracker.ancientwarfareautomation.block.warehouse;

import io.github.capttracker.ancientwarfare.block.BaseBlock;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.ControlledBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseInterfaceBlockEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
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
 * The warehouse interface block ({@code warehouse_interface}): nine slots that hoppers, couriers and other mods can use,
 * kept filled or emptied by the warehouse. Right-click opens the interface screen (the block entity does that); the
 * item handler capability is registered by {@code WarehouseSetup}.
 */
public class WarehouseInterfaceBlock extends BaseBlock implements EntityBlock {

    public WarehouseInterfaceBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WarehouseInterfaceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return blockEntityType == AwAutomationBase.WAREHOUSE_INTERFACE_BE.get() ? ControlledBlockEntity.ticker(level) : null;
    }
}
