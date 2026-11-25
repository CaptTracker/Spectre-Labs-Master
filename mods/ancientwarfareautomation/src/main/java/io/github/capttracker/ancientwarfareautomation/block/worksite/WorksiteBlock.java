/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/block/BlockWorksiteBase (and BlockBaseAutomation).
 */
package io.github.capttracker.ancientwarfareautomation.block.worksite;

import io.github.capttracker.ancientwarfare.api.IInteractableTile;
import io.github.capttracker.ancientwarfare.api.IWorkSite;
import io.github.capttracker.ancientwarfare.block.BaseBlock;
import io.github.capttracker.ancientwarfare.block.BlockRotationHandler;
import io.github.capttracker.ancientwarfare.owner.IOwnable;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.WorksiteBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.WorksiteProperties;
import io.github.capttracker.ancientwarfareautomation.item.WorksiteUpgradeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * The block of the quarry, the five farms and the warehouse control block (1.12.2: one {@code BlockWorksiteBase}
 * class, instantiated with a tile factory; here the block entity type is passed in).
 *
 * <p><b>State.</b> {@link WorksiteProperties#FACING} and {@link WorksiteProperties#ACTIVE} are real block state
 * properties (the original derived them from the tile in {@code getActualState}, the metadata was always 0). The
 * front of the block faces the placer ({@code invertFacing}). {@code WorksiteBlockEntity} writes both properties.
 *
 * <p><b>Right click.</b> Only the owner, a player of the owner's team or a friend of the owner may open the worksite
 * (1.12.2 {@code onBlockActivated}). A worksite upgrade item in the hand is not a click on the block: it goes to the
 * item, which installs the upgrade, so no sneaking is needed to apply an upgrade to a worksite with a screen.
 *
 * <p>Fire odds (spread 5, flammability 20) are registered by {@code AwAutomationBase}; hardness 2 and the wood
 * properties come from its {@code wood()} properties.
 */
public class WorksiteBlock extends BaseBlock implements EntityBlock, BlockRotationHandler.IRotatableBlock {
    private final Supplier<? extends BlockEntityType<?>> blockEntityType;

    public WorksiteBlock(Properties properties, Supplier<? extends BlockEntityType<?>> blockEntityType) {
        super(properties);
        this.blockEntityType = blockEntityType;
        registerDefaultState(stateDefinition.any().setValue(WorksiteProperties.FACING, Direction.NORTH).setValue(WorksiteProperties.ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WorksiteProperties.FACING, WorksiteProperties.ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(WorksiteProperties.FACING, BlockRotationHandler.getFaceForPlacement(context, this));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return blockEntityType.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != blockEntityType.get()) {
            return null;
        }
        return WorksiteBlockEntity.ticker(level);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof WorksiteUpgradeItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof IInteractableTile tile && canInteract(blockEntity, player)) {
            if (tile.onBlockClicked(player, hand)) {
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** The owner, a team mate or a friend of the owner (1.12.2 {@code onBlockActivated}). */
    protected boolean canInteract(BlockEntity blockEntity, Player player) {
        if (blockEntity instanceof IOwnable ownable && ownable.isOwner(player)) {
            return true;
        }
        return blockEntity instanceof IWorkSite site && site.getOwner().isOwnerOrSameTeamOrFriend(player);
    }

    @Override
    public final BlockRotationHandler.RotationType getRotationType() {
        return BlockRotationHandler.RotationType.FOUR_WAY;
    }

    @Override
    public boolean invertFacing() {
        return true;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(WorksiteProperties.FACING, rotation.rotate(state.getValue(WorksiteProperties.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(WorksiteProperties.FACING)));
    }
}
