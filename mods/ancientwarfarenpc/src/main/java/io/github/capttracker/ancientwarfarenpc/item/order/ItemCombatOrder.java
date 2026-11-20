/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/item/ItemCombatOrder.
 */
package io.github.capttracker.ancientwarfarenpc.item.order;

import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfarenpc.orders.CombatOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The combat order slip: the patrol points of a soldier. The key action adds the block the player looks at; sneaking with it clears the list. */
public class ItemCombatOrder extends ItemOrders {
    public ItemCombatOrder(Properties properties) {
        super(properties, OrderGuiBridge.Gui.COMBAT_ORDER);
    }

    /** The points are highlighted one block above the ground. */
    @Override
    public List<BlockPos> getPositionsForRender(ItemStack stack) {
        List<BlockPos> positionList = new ArrayList<>();
        CombatOrder order = CombatOrder.getCombatOrder(stack);
        if (order != null && !order.isEmpty()) {
            for (int i = 0; i < order.size(); i++) {
                positionList.add(order.get(i).above());
            }
        }
        return positionList;
    }

    @Override
    public void onKeyAction(Player player, ItemStack stack, ItemAltFunction altFunction) {
        CombatOrder order = CombatOrder.getCombatOrder(stack);
        if (order == null) {
            return;
        }
        if (player.isShiftKeyDown()) {
            order.clear();
            order.write(stack);
        } else {
            BlockPos pos = BlockTools.getBlockClickedOn(player, player.level(), false);
            if (pos != null) {
                order.addPatrolPoint(player.level(), pos);
                order.write(stack);
                addMessage(player);
            }
        }
    }
}
