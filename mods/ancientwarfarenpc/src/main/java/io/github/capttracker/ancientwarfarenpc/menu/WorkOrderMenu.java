/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/container/ContainerWorkOrder.
 */
package io.github.capttracker.ancientwarfarenpc.menu;

import com.mojang.serialization.Codec;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcMenus;
import io.github.capttracker.ancientwarfarenpc.item.order.ItemWorkOrder;
import io.github.capttracker.ancientwarfarenpc.orders.WorkOrder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The work order editor (1.12.2: {@code GUI_NPC_WORK_ORDER}, 34). No slots. The message key is {@code wo} as in 1.12.2.
 *
 * <p>The check ({@link OrderMenu}): at most {@value WorkOrder#MAX_SIZE} entries, each a worksite (position and dimension) the stored order
 * already has, with a work length of 0 or more.
 */
public class WorkOrderMenu extends OrderMenu<WorkOrder> {
    /** Client side: the hand comes from the buffer written by {@link #open}. */
    public WorkOrderMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readEnum(InteractionHand.class));
    }

    public WorkOrderMenu(int containerId, Inventory inventory, InteractionHand hand) {
        super(AwNpcMenus.WORK_ORDER_MENU.get(), containerId, inventory, hand);
    }

    /** Server side: opens the editor for the work order in the hand (installed with {@code OrderGuiBridge.setGuiOpener}). */
    public static void open(ServerPlayer player, InteractionHand hand) {
        openMenu(player, hand, (id, inventory, p) -> new WorkOrderMenu(id, inventory, hand));
    }

    @Override
    protected String dataKey() {
        return "wo";
    }

    @Override
    protected Codec<WorkOrder> codec() {
        return WorkOrder.CODEC;
    }

    @Nullable
    @Override
    protected WorkOrder readOrder(ItemStack stack) {
        return WorkOrder.getWorkOrder(stack);
    }

    @Override
    protected WorkOrder newOrder() {
        return new WorkOrder();
    }

    @Override
    protected void writeOrder(WorkOrder order, ItemStack stack) {
        order.write(stack);
    }

    @Override
    protected boolean isOrderItem(ItemStack stack) {
        return stack.getItem() instanceof ItemWorkOrder;
    }

    @Nullable
    @Override
    protected WorkOrder sanitize(WorkOrder current, WorkOrder sent) {
        if (sent.size() > WorkOrder.MAX_SIZE) {
            return null;
        }
        List<List<Object>> sentSites = new ArrayList<>();
        for (WorkOrder.WorkEntry entry : sent.getEntries()) {
            sentSites.add(List.of(entry.getPosition(), entry.getDimension()));
        }
        List<List<Object>> currentSites = new ArrayList<>();
        for (WorkOrder.WorkEntry entry : current.getEntries()) {
            currentSites.add(List.of(entry.getPosition(), entry.getDimension()));
        }
        if (!isSubset(sentSites, currentSites)) {
            return null;
        }
        for (WorkOrder.WorkEntry entry : sent.getEntries()) {
            entry.setWorkLength(Math.max(0, entry.getWorkLength()));
        }
        return sent;
    }
}
