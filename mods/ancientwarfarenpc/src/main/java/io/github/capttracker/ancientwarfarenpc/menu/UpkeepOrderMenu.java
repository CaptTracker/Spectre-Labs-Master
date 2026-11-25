/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/container/ContainerUpkeepOrder.
 */
package io.github.capttracker.ancientwarfarenpc.menu;

import com.mojang.serialization.Codec;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcMenus;
import io.github.capttracker.ancientwarfarenpc.item.order.ItemUpkeepOrder;
import io.github.capttracker.ancientwarfarenpc.orders.UpkeepOrder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * The upkeep order editor (1.12.2: {@code GUI_NPC_UPKEEP_ORDER}, 35). No slots. The message key is {@code upkeepOrder}. The screen shows the block
 * at the upkeep point as an item; the menu works it out from the player's level (the original did it in the container constructor, on both sides).
 *
 * <p>The check ({@link OrderMenu}): the upkeep point and its dimension are the stored ones (the screen can only change the side and the amount),
 * and the amount is 0 or more.
 */
public class UpkeepOrderMenu extends OrderMenu<UpkeepOrder> {
    /** The block at the upkeep point as an item, for the screen (empty if no point is set). */
    public final ItemStack upkeepBlock;

    /** Client side: the hand comes from the buffer written by {@link #open}. */
    public UpkeepOrderMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readEnum(InteractionHand.class));
    }

    public UpkeepOrderMenu(int containerId, Inventory inventory, InteractionHand hand) {
        super(AwNpcMenus.UPKEEP_ORDER_MENU.get(), containerId, inventory, hand);
        upkeepBlock = order.getUpkeepPosition()
                .map(pos -> new ItemStack(inventory.player.level().getBlockState(pos).getBlock()))
                .orElse(ItemStack.EMPTY);
    }

    /** Server side: opens the editor for the upkeep order in the hand (installed with {@code OrderGuiBridge.setGuiOpener}). */
    public static void open(ServerPlayer player, InteractionHand hand) {
        openMenu(player, hand, (id, inventory, p) -> new UpkeepOrderMenu(id, inventory, hand));
    }

    @Override
    protected String dataKey() {
        return "upkeepOrder";
    }

    @Override
    protected Codec<UpkeepOrder> codec() {
        return UpkeepOrder.CODEC;
    }

    @Nullable
    @Override
    protected UpkeepOrder readOrder(ItemStack stack) {
        return UpkeepOrder.getUpkeepOrder(stack).orElse(null);
    }

    @Override
    protected UpkeepOrder newOrder() {
        return new UpkeepOrder();
    }

    @Override
    protected void writeOrder(UpkeepOrder order, ItemStack stack) {
        order.write(stack);
    }

    @Override
    protected boolean isOrderItem(ItemStack stack) {
        return stack.getItem() instanceof ItemUpkeepOrder;
    }

    @Nullable
    @Override
    protected UpkeepOrder sanitize(UpkeepOrder current, UpkeepOrder sent) {
        if (!sent.getUpkeepPosition().equals(current.getUpkeepPosition())
                || sent.getUpkeepPosition().isPresent() && !sent.getUpkeepDimension().equals(current.getUpkeepDimension())) {
            return null;
        }
        sent.setUpkeepAmount(Math.max(0, sent.getUpkeepAmount()));
        return sent;
    }
}
