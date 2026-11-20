/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/item/ItemTradeOrder.
 */
package io.github.capttracker.ancientwarfarenpc.item.order;

import io.github.capttracker.ancientwarfare.util.RayTraceUtils;
import io.github.capttracker.ancientwarfarenpc.orders.TradeOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * The trade order slip: what a trader sells, where it walks and how it restocks. Key action 1 adds a route point, 2 sets the deposit
 * inventory and 3 the withdraw inventory, all to the block face the player looks at (within 5 blocks).
 */
public class ItemTradeOrder extends ItemOrders {
    public ItemTradeOrder(Properties properties) {
        super(properties, OrderGuiBridge.Gui.TRADE_ORDER);
    }

    @Override
    public List<BlockPos> getPositionsForRender(ItemStack stack) {
        List<BlockPos> positionList = new ArrayList<>();
        TradeOrder order = TradeOrder.getTradeOrder(stack);
        if (order != null && order.getRoute().size() > 0) {
            for (int i = 0; i < order.getRoute().size(); i++) {
                positionList.add(order.getRoute().get(i).getPosition());
            }
        }
        return positionList;
    }

    @Override
    public boolean onKeyActionClient(Player player, ItemStack stack, ItemAltFunction altFunction) {
        return altFunction == ItemAltFunction.ALT_FUNCTION_1 || altFunction == ItemAltFunction.ALT_FUNCTION_2 || altFunction == ItemAltFunction.ALT_FUNCTION_3;
    }

    @Override
    public void onKeyAction(Player player, ItemStack stack, ItemAltFunction altFunction) {
        HitResult hit = RayTraceUtils.getPlayerTarget(player, 5, 0);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        TradeOrder order = TradeOrder.getTradeOrder(stack);
        if (order == null) {
            return;
        }
        if (altFunction == ItemAltFunction.ALT_FUNCTION_1) {
            order.getRoute().addRoutePoint(blockHit.getBlockPos());
            order.write(stack);
        } else if (altFunction == ItemAltFunction.ALT_FUNCTION_2) {
            order.getRestockData().setDepositPoint(blockHit.getBlockPos(), blockHit.getDirection());
            order.write(stack);
        } else if (altFunction == ItemAltFunction.ALT_FUNCTION_3) {
            order.getRestockData().setWithdrawPoint(blockHit.getBlockPos(), blockHit.getDirection());
            order.write(stack);
        }
    }
}
