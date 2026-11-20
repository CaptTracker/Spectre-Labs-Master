/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/item/ItemBlockWarehouseStockLinker (and the part of ItemBlockOwnedRotatable it extended).
 */
package io.github.capttracker.ancientwarfareautomation.item;

import io.github.capttracker.ancientwarfare.item.OwnedBlockItem;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The warehouse stock linker block item. Sneak-using it on a warehouse control block remembers that warehouse's
 * position in the stack (the data component {@code warehouse_pos}, 1.12.2: the item NBT {@code warehousePosTag});
 * the placed linker block entity reads it ({@link #getWarehousePos}) and links itself to that warehouse.
 *
 * <p>1.12.2 used a 5 block ray trace from the player; the block the player clicked on is used now.
 */
public class WarehouseStockLinkerBlockItem extends OwnedBlockItem {
    public WarehouseStockLinkerBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** The warehouse control block position stored in the stack, or null if none was set. */
    @Nullable
    public static BlockPos getWarehousePos(ItemStack stack) {
        return stack.get(AwAutomationBase.WAREHOUSE_POS.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        BlockPos pos = getWarehousePos(stack);
        if (pos != null) {
            tooltipComponents.add(Component.translatable("block.ancientwarfareautomation.warehouse_stock_linker.tooltip", formatPos(pos)));
        }
    }

    private static String formatPos(BlockPos pos) {
        return String.format("x: %d y: %d z: %d", pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        if (player != null && player.isShiftKeyDown() && level.getBlockState(clicked).is(AwAutomationBase.WAREHOUSE_CONTROL.get())) {
            if (!level.isClientSide) {
                context.getItemInHand().set(AwAutomationBase.WAREHOUSE_POS.get(), clicked.immutable());
                player.sendSystemMessage(Component.translatable("guistrings.automation.warehouse_set", formatPos(clicked)));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useOn(context);
    }
}
