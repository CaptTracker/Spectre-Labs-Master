/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/block/BlockResearchStation.
 */
package io.github.capttracker.ancientwarfare.block;

import io.github.capttracker.ancientwarfare.block.entity.ResearchStationBlockEntity;
import io.github.capttracker.ancientwarfare.init.AwCoreStations;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * The research bench: a block entity that advances its book owner's research. Right-click opens the bench menu
 * (through {@code BaseBlock#useItemOn} and {@link ResearchStationBlockEntity#onBlockClicked}); the placer becomes
 * the owner.
 */
public class ResearchStationBlock extends StationBlock implements EntityBlock {
    public ResearchStationBlock(Properties props) {
        super(props, true);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ResearchStationBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != AwCoreStations.RESEARCH_STATION_BE.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> ((ResearchStationBlockEntity) be).serverTick();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof ResearchStationBlockEntity station) {
            station.setOwner(player);
        }
    }
}
