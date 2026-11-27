/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 11): the sea route chart.
 */
package io.github.capttracker.ancientwarfarenpc.item.order;

import io.github.capttracker.ancientwarfare.api.ISeaDock;
import io.github.capttracker.ancientwarfare.util.RayTraceUtils;
import io.github.capttracker.ancientwarfarenpc.orders.SeaRoute;
import io.github.capttracker.ancientwarfarenpc.trade.SeaDocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * The sea route chart: the docks a trader sails a cargo ship to (see {@link SeaRoute}). Using it on a dock block adds the dock as the next stop; using it
 * anywhere else opens the editor, where the stops are reordered and removed and the wait, the unload list and the load list of each stop are set. The primary alt
 * key adds the dock the player looks at (within 5 blocks), as for the other order slips. The chart goes into the orders slot of an owned trader.
 */
public class ItemSeaRoute extends ItemOrders {
    public ItemSeaRoute(Properties properties) {
        super(properties, OrderGuiBridge.Gui.SEA_ROUTE);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("guistrings.npc.orders.open_gui"));
        tooltip.add(Component.translatable("tooltip.ancientwarfarenpc.sea_route.add_dock", ItemAltFunction.ALT_FUNCTION_1.keyDisplay()));
        SeaRoute route = SeaRoute.get(stack);
        if (route != null) {
            tooltip.add(Component.translatable("tooltip.ancientwarfarenpc.sea_route.stops", route.size()));
        }
    }

    @Override
    public List<BlockPos> getPositionsForRender(ItemStack stack) {
        List<BlockPos> positionList = new ArrayList<>();
        SeaRoute route = SeaRoute.get(stack);
        if (route != null) {
            for (SeaRoute.Stop stop : route.getStops()) {
                positionList.add(stop.getDock());
            }
        }
        return positionList;
    }

    /** A dock block adds a stop; any other block falls through to {@link #use}, which opens the editor. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null || !(level.getBlockEntity(context.getClickedPos()) instanceof ISeaDock)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            addDock(player, context.getItemInHand(), context.getClickedPos());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onKeyAction(Player player, ItemStack stack, ItemAltFunction altFunction) {
        HitResult hit = RayTraceUtils.getPlayerTarget(player, 5, 0);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK && altFunction == ItemAltFunction.ALT_FUNCTION_1) {
            if (player.level().getBlockEntity(blockHit.getBlockPos()) instanceof ISeaDock) {
                addDock(player, stack, blockHit.getBlockPos());
            } else {
                player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.sea_route.not_dock"), true);
            }
        }
    }

    private void addDock(Player player, ItemStack stack, BlockPos pos) {
        SeaRoute route = SeaRoute.get(stack);
        if (route == null || !(player.level().getBlockEntity(pos) instanceof ISeaDock dock)) {
            return;
        }
        if (!SeaDocks.mayUse(dock, player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.sea_route.not_yours"), true);
        } else if (!route.addStop(pos)) {
            player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.sea_route.full", SeaRoute.MAX_STOPS), true);
        } else {
            route.write(stack);
            player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.sea_route.added", route.size()), true);
        }
    }
}
