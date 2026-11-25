/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/item/ItemUpkeepOrder.
 */
package io.github.capttracker.ancientwarfarenpc.item.order;

import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfarenpc.orders.UpkeepOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;

/** The upkeep order slip: the one inventory an NPC fetches its food from. The key action sets it to the inventory the player looks at. */
public class ItemUpkeepOrder extends ItemOrders {
    public ItemUpkeepOrder(Properties properties) {
        super(properties, OrderGuiBridge.Gui.UPKEEP_ORDER);
    }

    @Override
    public List<BlockPos> getPositionsForRender(ItemStack stack) {
        return UpkeepOrder.getUpkeepOrder(stack).map(o -> o.getUpkeepPosition().map(Collections::singletonList).orElse(Collections.<BlockPos>emptyList()))
                .orElse(Collections.emptyList());
    }

    @Override
    public void onKeyAction(Player player, ItemStack stack, ItemAltFunction altFunction) {
        UpkeepOrder.getUpkeepOrder(stack).ifPresent(upkeepOrder -> {
            BlockPos hit = BlockTools.getBlockClickedOn(player, player.level(), false);
            if (hit != null) {
                if (upkeepOrder.addUpkeepPosition(player.level(), hit)) {
                    upkeepOrder.write(stack);
                    player.sendSystemMessage(Component.translatable("guistrings.npc.upkeep_point_set"));
                }
            } else {
                openGui(player, stack);
            }
        });
    }
}
