/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/item/ItemOrders (and ItemBaseNPC, whose model registration is not needed any more).
 */
package io.github.capttracker.ancientwarfarenpc.item.order;

import io.github.capttracker.ancientwarfare.input.IItemKeyInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Base of the five order slips. A right click opens the editor of the slip, the primary alt key adds the position the player looks at (each
 * slip reacts to it in {@link #onKeyAction}). The order itself is a data component of the stack, read and written by the order classes in
 * {@code orders} ({@code WorkOrder.getWorkOrder(stack)} ... {@code write(stack)}).
 */
public abstract class ItemOrders extends Item implements IItemKeyInterface {
    private final OrderGuiBridge.Gui gui;

    protected ItemOrders(Properties properties, OrderGuiBridge.Gui gui) {
        super(properties);
        this.gui = gui;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("guistrings.npc.orders.open_gui"));
        tooltip.add(Component.translatable("guistrings.npc.orders.add_position", ItemAltFunction.ALT_FUNCTION_1.keyDisplay()));
    }

    @Override
    public boolean onKeyActionClient(Player player, ItemStack stack, ItemAltFunction altFunction) {
        return altFunction == ItemAltFunction.ALT_FUNCTION_1;
    }

    /** The positions the client highlights while the slip is held. */
    public abstract List<BlockPos> getPositionsForRender(ItemStack stack);

    public void addMessage(Player player) {
        player.sendSystemMessage(Component.translatable("guistrings.npc.orders.position_added"));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) {
            OrderGuiBridge.open(player, hand, gui);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    /** Opens the editor for the slip the key action was used with (it was found in one of the player's hands). */
    protected void openGui(Player player, ItemStack stack) {
        OrderGuiBridge.open(player, handOf(player, stack), gui);
    }

    protected static InteractionHand handOf(Player player, ItemStack stack) {
        return player.getMainHandItem() == stack ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }
}
