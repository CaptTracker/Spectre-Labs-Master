/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/block/BlockWarehouseCraftingStation.
 */
package io.github.capttracker.ancientwarfareautomation.block.warehouse;

import io.github.capttracker.ancientwarfare.block.BaseBlock;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseCraftingStationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * The warehouse crafting table ({@code warehouse_crafting}): crafts from the stock of the warehouse whose control block
 * is directly below. Right-click opens the crafting screen (the block entity does that); the recipe memory drops when
 * the block is broken. It does not tick.
 */
public class WarehouseCraftingStationBlock extends BaseBlock implements EntityBlock {

    public WarehouseCraftingStationBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WarehouseCraftingStationBlockEntity(pos, state);
    }
}
