/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 1): settlements.
 */
package io.github.capttracker.ancientwarfarenpc.settlement;

import io.github.capttracker.ancientwarfarenpc.block.entity.TownHallBlockEntity;
import io.github.capttracker.spectrefactions.api.FactionsApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * The settlement ledger. Used on a container inside the area of the owner's settlement (sneak and use, so the container does not open) it links or
 * unlinks that container as settlement storage. Used on the hall it opens the screen. Holding it shows the border of the settlements.
 */
public class SettlementLedgerItem extends Item {
    public SettlementLedgerItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ancientwarfarenpc.settlement_ledger.tooltip").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.getBlockEntity(pos) instanceof TownHallBlockEntity hall) {
            hall.onBlockClicked(player, context.getHand());
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.sidedSuccess(true);
        }
        if (SettlementApi.handlerAt(serverLevel, pos) == null) {
            return InteractionResult.PASS;
        }
        Optional<Settlement> found = SettlementApi.findByPosition(serverLevel, pos, FactionsApi.actorOf(player.getUUID()));
        if (found.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.ledger.outside"), true);
            return InteractionResult.FAIL;
        }
        Settlement settlement = found.get();
        if (!SettlementApi.mayManage(settlement, player)) {
            player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.ledger.not_owner"), true);
            return InteractionResult.FAIL;
        }
        if (settlement.isStorage(pos)) {
            settlement.removeStorage(pos);
            player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.ledger.unlinked", settlement.getName()), true);
        } else {
            settlement.addStorage(pos);
            player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.ledger.linked", settlement.getName()), true);
        }
        return InteractionResult.SUCCESS;
    }
}
