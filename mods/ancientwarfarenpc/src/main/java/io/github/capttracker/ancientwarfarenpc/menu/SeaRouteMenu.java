/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 11): the editor of the sea route chart.
 */
package io.github.capttracker.ancientwarfarenpc.menu;

import com.mojang.serialization.Codec;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcMenus;
import io.github.capttracker.ancientwarfarenpc.item.order.ItemSeaRoute;
import io.github.capttracker.ancientwarfarenpc.orders.SeaRoute;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
 * The sea route chart editor: the stops of the route (up or down, delete, the wait at the dock) and for each stop the unload list and the load list, and the
 * player's inventory below so that items can be picked up to set the filters. The message key is {@code seaRoute}.
 *
 * <p>The check ({@link OrderMenu}): at most {@value SeaRoute#MAX_STOPS} stops and {@value SeaRoute#MAX_ENTRIES} entries in each list; every stop is a dock position the
 * stored chart already has (docks are added with the item, not in the screen), with a wait of 0 or more ticks.
 */
public class SeaRouteMenu extends OrderMenu<SeaRoute> {

    /** Client side: the hand comes from the buffer written by {@link #open}. */
    public SeaRouteMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readEnum(InteractionHand.class));
    }

    public SeaRouteMenu(int containerId, Inventory inventory, InteractionHand hand) {
        super(AwNpcMenus.SEA_ROUTE_MENU.get(), containerId, inventory, hand);
        addPlayerSlots(inventory, (256 - (9 * 18)) / 2, 240 - 4 - 8 - 4 * 18, 4);
    }

    /** Server side: opens the editor for the chart in the hand (installed with {@code OrderGuiBridge.setGuiOpener}). */
    public static void open(ServerPlayer player, InteractionHand hand) {
        openMenu(player, hand, (id, inventory, p) -> new SeaRouteMenu(id, inventory, hand));
    }

    @Override
    protected String dataKey() {
        return "seaRoute";
    }

    @Override
    protected Codec<SeaRoute> codec() {
        return SeaRoute.CODEC;
    }

    @Nullable
    @Override
    protected SeaRoute readOrder(ItemStack stack) {
        return SeaRoute.get(stack);
    }

    @Override
    protected SeaRoute newOrder() {
        return new SeaRoute();
    }

    @Override
    protected void writeOrder(SeaRoute order, ItemStack stack) {
        order.write(stack);
    }

    @Override
    protected boolean isOrderItem(ItemStack stack) {
        return stack.getItem() instanceof ItemSeaRoute;
    }

    @Override
    protected boolean isRawValid(CompoundTag raw) {
        net.minecraft.nbt.ListTag stops = raw.getList("stops", Tag.TAG_COMPOUND);
        if (stops.size() > SeaRoute.MAX_STOPS) {
            return false;
        }
        for (int i = 0; i < stops.size(); i++) {
            CompoundTag stop = stops.getCompound(i);
            if (stop.getList("unload", Tag.TAG_COMPOUND).size() > SeaRoute.MAX_ENTRIES || stop.getList("load", Tag.TAG_COMPOUND).size() > SeaRoute.MAX_ENTRIES) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    @Override
    protected SeaRoute sanitize(SeaRoute current, SeaRoute sent) {
        // the docks: only stops the chart has (the screen reorders and removes, the item adds)
        List<BlockPos> sentDocks = new ArrayList<>();
        for (SeaRoute.Stop stop : sent.getStops()) {
            sentDocks.add(stop.getDock());
        }
        List<BlockPos> currentDocks = new ArrayList<>();
        for (SeaRoute.Stop stop : current.getStops()) {
            currentDocks.add(stop.getDock());
        }
        if (!isSubset(sentDocks, currentDocks)) {
            return null;
        }
        for (SeaRoute.Stop stop : sent.getStops()) {
            stop.setWaitTicks(stop.getWaitTicks()); // clamps
        }
        return sent;
    }
}
