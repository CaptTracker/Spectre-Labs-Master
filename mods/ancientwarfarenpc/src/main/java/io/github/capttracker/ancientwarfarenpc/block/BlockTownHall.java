/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/block/BlockTownHall and BlockBaseNPC (creative tab and model registration are the registrar's and JSON's now).
 */
package io.github.capttracker.ancientwarfarenpc.block;

import io.github.capttracker.ancientwarfare.block.BaseBlock;
import io.github.capttracker.ancientwarfarenpc.block.entity.TownHallBlockEntity;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcs;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * The town hall block (1.12.2: rock, hardness 2, not opaque, cutout). Right-clicking it (through {@code BaseBlock} and
 * {@link TownHallBlockEntity#onBlockClicked}) opens the town hall for its owner; redstone power sets the alarm flag of the block entity.
 * The placer becomes the owner through {@code OwnedBlockItem}.
 */
public class BlockTownHall extends BaseBlock implements EntityBlock {
    public BlockTownHall(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TownHallBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != AwNpcs.TOWN_HALL_BE.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, be) -> ((TownHallBlockEntity) be).serverTick();
    }

    /** Placing a hall founds the settlement of the placer's actor (the owner was set by the block item). */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TownHallBlockEntity townHall) {
            townHall.ensureSettlement();
        }
    }

    /** A neighbouring block changed: the alarm is on while any neighbour gives redstone power. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TownHallBlockEntity townHall) {
            boolean powered = level.getBestNeighborSignal(pos) > 0;
            if (townHall.isAlarmActive() != powered) {
                townHall.setAlarm(powered);
            }
        }
    }
}
