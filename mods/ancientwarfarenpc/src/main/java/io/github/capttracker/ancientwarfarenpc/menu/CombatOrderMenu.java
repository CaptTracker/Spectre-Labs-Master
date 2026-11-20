/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/container/ContainerCombatOrder.
 */
package io.github.capttracker.ancientwarfarenpc.menu;

import com.mojang.serialization.Codec;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcMenus;
import io.github.capttracker.ancientwarfarenpc.item.order.ItemCombatOrder;
import io.github.capttracker.ancientwarfarenpc.orders.CombatOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The combat order editor (1.12.2: {@code GUI_NPC_COMBAT_ORDER}, 36). No slots (the original added the player slots and hid them). The message
 * key is {@code combatOrder}.
 *
 * <p>The check ({@link OrderMenu}): the dimension is the stored one and every patrol point is one the stored order already has.
 */
public class CombatOrderMenu extends OrderMenu<CombatOrder> {
    /** Client side: the hand comes from the buffer written by {@link #open}. */
    public CombatOrderMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readEnum(InteractionHand.class));
    }

    public CombatOrderMenu(int containerId, Inventory inventory, InteractionHand hand) {
        super(AwNpcMenus.COMBAT_ORDER_MENU.get(), containerId, inventory, hand);
    }

    /** Server side: opens the editor for the combat order in the hand (installed with {@code OrderGuiBridge.setGuiOpener}). */
    public static void open(ServerPlayer player, InteractionHand hand) {
        openMenu(player, hand, (id, inventory, p) -> new CombatOrderMenu(id, inventory, hand));
    }

    @Override
    protected String dataKey() {
        return "combatOrder";
    }

    @Override
    protected Codec<CombatOrder> codec() {
        return CombatOrder.CODEC;
    }

    @Nullable
    @Override
    protected CombatOrder readOrder(ItemStack stack) {
        return CombatOrder.getCombatOrder(stack);
    }

    @Override
    protected CombatOrder newOrder() {
        return new CombatOrder();
    }

    @Override
    protected void writeOrder(CombatOrder order, ItemStack stack) {
        order.write(stack);
    }

    @Override
    protected boolean isOrderItem(ItemStack stack) {
        return stack.getItem() instanceof ItemCombatOrder;
    }

    @Nullable
    @Override
    protected CombatOrder sanitize(CombatOrder current, CombatOrder sent) {
        if (!sent.getPatrolDimension().equals(current.getPatrolDimension())) {
            return null;
        }
        List<BlockPos> sentPoints = new ArrayList<>();
        sent.forEach(sentPoints::add);
        List<BlockPos> currentPoints = new ArrayList<>();
        current.forEach(currentPoints::add);
        return isSubset(sentPoints, currentPoints) ? sent : null;
    }
}
