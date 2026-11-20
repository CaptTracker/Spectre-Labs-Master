/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/container/ContainerRoutingOrder.
 */
package io.github.capttracker.ancientwarfarenpc.menu;

import com.mojang.serialization.Codec;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcMenus;
import io.github.capttracker.ancientwarfarenpc.item.order.ItemRoutingOrder;
import io.github.capttracker.ancientwarfarenpc.orders.RoutingOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The routing order editor (1.12.2: {@code GUI_NPC_ROUTING_ORDER}, 37): the route points of a courier with their twelve filter slots, and the
 * player's inventory below so that items can be picked up to set the filters. The message key is {@code routingOrder}.
 *
 * <p>The check ({@link OrderMenu}): every route point is a position the stored order already has, a filter count is at most
 * {@value #MAX_FILTER_COUNT}, and the raw data has at most {@value #MAX_ENTRIES} entries with filter slots 0 to 11 (the order's decoder grows its
 * filter list to the slot number it reads, so an unchecked slot number could make the server allocate gigabytes).
 */
public class RoutingOrderMenu extends OrderMenu<RoutingOrder> {
    public static final int MAX_ENTRIES = 64;
    public static final int MAX_FILTER_COUNT = 1_000_000;

    /** Client side: the hand comes from the buffer written by {@link #open}. */
    public RoutingOrderMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readEnum(InteractionHand.class));
    }

    public RoutingOrderMenu(int containerId, Inventory inventory, InteractionHand hand) {
        super(AwNpcMenus.ROUTING_ORDER_MENU.get(), containerId, inventory, hand);
        addPlayerSlots(inventory, (256 - (9 * 18)) / 2, 240 - 4 * 18 - 8 - 4, 4);
    }

    /** Server side: opens the editor for the routing order in the hand (installed with {@code OrderGuiBridge.setGuiOpener}). */
    public static void open(ServerPlayer player, InteractionHand hand) {
        openMenu(player, hand, (id, inventory, p) -> new RoutingOrderMenu(id, inventory, hand));
    }

    @Override
    protected String dataKey() {
        return "routingOrder";
    }

    @Override
    protected Codec<RoutingOrder> codec() {
        return RoutingOrder.CODEC;
    }

    @Nullable
    @Override
    protected RoutingOrder readOrder(ItemStack stack) {
        return RoutingOrder.getRoutingOrder(stack);
    }

    @Override
    protected RoutingOrder newOrder() {
        return new RoutingOrder();
    }

    @Override
    protected void writeOrder(RoutingOrder order, ItemStack stack) {
        order.write(stack);
    }

    @Override
    protected boolean isOrderItem(ItemStack stack) {
        return stack.getItem() instanceof ItemRoutingOrder;
    }

    @Override
    protected boolean isRawValid(CompoundTag raw) {
        ListTag entries = raw.getList("entryList", Tag.TAG_COMPOUND);
        if (entries.size() > MAX_ENTRIES) {
            return false;
        }
        for (int i = 0; i < entries.size(); i++) {
            ListTag filters = entries.getCompound(i).getList("filterList", Tag.TAG_COMPOUND);
            if (filters.size() > RoutingOrder.FILTER_SLOTS) {
                return false;
            }
            for (int j = 0; j < filters.size(); j++) {
                int slot = filters.getCompound(j).getInt("slot");
                if (slot < 0 || slot >= RoutingOrder.FILTER_SLOTS) {
                    return false;
                }
            }
        }
        return true;
    }

    @Nullable
    @Override
    protected RoutingOrder sanitize(RoutingOrder current, RoutingOrder sent) {
        List<BlockPos> sentPoints = new ArrayList<>();
        for (RoutingOrder.RoutePoint point : sent.getEntries()) {
            sentPoints.add(point.getTarget());
        }
        List<BlockPos> currentPoints = new ArrayList<>();
        for (RoutingOrder.RoutePoint point : current.getEntries()) {
            currentPoints.add(point.getTarget());
        }
        if (!isSubset(sentPoints, currentPoints)) {
            return null;
        }
        for (RoutingOrder.RoutePoint point : sent.getEntries()) {
            for (int i = 0; i < point.getFilterSize(); i++) {
                ItemStack filter = point.getFilterInSlot(i);
                if (filter.getCount() > MAX_FILTER_COUNT) {
                    ItemStack limited = filter.copy();
                    limited.setCount(MAX_FILTER_COUNT);
                    point.setFilter(i, limited);
                }
            }
        }
        return sent;
    }
}
