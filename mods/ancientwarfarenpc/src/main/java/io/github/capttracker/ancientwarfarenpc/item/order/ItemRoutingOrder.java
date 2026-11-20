/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/item/ItemRoutingOrder.
 */
package io.github.capttracker.ancientwarfarenpc.item.order;

import io.github.capttracker.ancientwarfare.util.RayTraceUtils;
import io.github.capttracker.ancientwarfarenpc.orders.RoutingOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

/** The routing order slip: the route of a courier. The key action adds the block face the player looks at (within 5 blocks) as a route point. */
public class ItemRoutingOrder extends ItemOrders {
    public ItemRoutingOrder(Properties properties) {
        super(properties, OrderGuiBridge.Gui.ROUTING_ORDER);
    }

    @Override
    public List<BlockPos> getPositionsForRender(ItemStack stack) {
        List<BlockPos> positionList = new ArrayList<>();
        RoutingOrder order = RoutingOrder.getRoutingOrder(stack);
        if (order != null && !order.isEmpty()) {
            for (RoutingOrder.RoutePoint e : order.getEntries()) {
                positionList.add(e.getTarget());
            }
        }
        return positionList;
    }

    @Override
    public void onKeyAction(Player player, ItemStack stack, ItemAltFunction altFunction) {
        RoutingOrder order = RoutingOrder.getRoutingOrder(stack);
        if (order != null) {
            HitResult hit = RayTraceUtils.getPlayerTarget(player, 5, 0);
            if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
                order.addRoutePoint(blockHit.getDirection(), blockHit.getBlockPos());
                order.write(stack);
                addMessage(player);
            }
        }
    }
}
