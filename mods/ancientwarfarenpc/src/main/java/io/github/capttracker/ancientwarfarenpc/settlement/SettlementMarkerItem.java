/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 1): settlements.
 */
package io.github.capttracker.ancientwarfarenpc.settlement;

import io.github.capttracker.spectrefactions.api.ActorId;
import io.github.capttracker.spectrefactions.api.FactionsApi;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Places a boundary marker and links it to the nearest hall of the placer's actor within {@code markerMaxDistance}. A marker that would leave the
 * hall outside the polygon, or that has no hall to link to, is refused with a message (decided on the server; the client may show it for a
 * moment before the server puts the block back).
 */
public class SettlementMarkerItem extends BlockItem {
    public SettlementMarkerItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ancientwarfarenpc.settlement_marker.tooltip").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return super.placeBlock(context, state);
        }
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        if (player == null) {
            return false;
        }
        ActorId actor = FactionsApi.actorOf(player.getUUID());
        Settlement target = null;
        double best = Double.MAX_VALUE;
        double max = SettlementConfig.markerMaxDistance;
        for (Settlement settlement : SettlementData.get(serverLevel).inDimension(serverLevel.dimension())) {
            if (!settlement.getOwnerActor().equals(actor) && !settlement.getFounderId().equals(player.getUUID())) {
                continue;
            }
            double dx = settlement.getHallPos().getX() - pos.getX();
            double dz = settlement.getHallPos().getZ() - pos.getZ();
            double dist = dx * dx + dz * dz;
            if (dist <= max * max && dist < best) {
                best = dist;
                target = settlement;
            }
        }
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.marker.no_hall", SettlementConfig.markerMaxDistance), true);
            return false;
        }
        if (!target.canAddMarker(pos)) {
            player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.marker.hall_outside"), true);
            return false;
        }
        if (!super.placeBlock(context, state)) {
            return false;
        }
        target.addMarker(pos);
        if (serverLevel.getBlockEntity(pos) instanceof SettlementMarkerBlockEntity marker) {
            marker.setColor(SettlementEvents.colorOf(target.getOwnerActor()));
        }
        player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.marker.linked", target.getName(), target.getMarkers().size()), true);
        return true;
    }
}
