/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/block/BlockAutoCrafting.
 */
package io.github.capttracker.ancientwarfareautomation.block.worksite;

import io.github.capttracker.ancientwarfare.api.IInteractableTile;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import io.github.capttracker.ancientwarfareautomation.item.WorksiteUpgradeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The auto crafting table. Same state ({@code facing}, {@code active}) and ticker as the other worksites. Unlike
 * them it opens its screen for every player, as the original did (its {@code onBlockActivated} had no owner check).
 *
 * <p>1.12.2 had an unlisted horizontal facing property taken from the tile for the model; the block state's facing
 * replaces it. The block is not a full cube: register it with {@code noOcclusion()} (done in {@code AwAutomationBase}).
 */
public class AutoCraftingBlock extends WorksiteBlock {
    public AutoCraftingBlock(Properties properties) {
        super(properties, () -> AwAutomationBase.AUTO_CRAFTING_BE.get());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof WorksiteUpgradeItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof IInteractableTile tile && tile.onBlockClicked(player, hand)) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
