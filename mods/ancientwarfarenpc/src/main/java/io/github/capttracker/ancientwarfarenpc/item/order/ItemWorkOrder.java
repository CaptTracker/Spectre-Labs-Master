/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/item/ItemWorkOrder.
 */
package io.github.capttracker.ancientwarfarenpc.item.order;

import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfarenpc.orders.WorkOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The work order slip: up to 8 worksites for a worker. The key action adds the block the player looks at; when the slip is full it opens the editor. */
public class ItemWorkOrder extends ItemOrders {
    public ItemWorkOrder(Properties properties) {
        super(properties, OrderGuiBridge.Gui.WORK_ORDER);
    }

    @Override
    public List<BlockPos> getPositionsForRender(ItemStack stack) {
        List<BlockPos> positionList = new ArrayList<>();
        WorkOrder order = WorkOrder.getWorkOrder(stack);
        if (order != null && !order.isEmpty()) {
            for (WorkOrder.WorkEntry e : order.getEntries()) {
                positionList.add(e.getPosition());
            }
        }
        return positionList;
    }

    @Override
    public void onKeyAction(Player player, ItemStack stack, ItemAltFunction altFunction) {
        WorkOrder wo = WorkOrder.getWorkOrder(stack);
        if (wo != null) {
            BlockPos hit = BlockTools.getBlockClickedOn(player, player.level(), false);
            if (wo.addWorkPosition(player.level(), hit)) {
                wo.write(stack);
                addMessage(player);
            } else {
                openGui(player, stack);
            }
        }
    }
}
