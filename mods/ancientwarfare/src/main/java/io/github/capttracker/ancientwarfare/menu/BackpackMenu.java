/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/container/ContainerBackpack and core/inventory/ItemHandlerBackpack.
 */
package io.github.capttracker.ancientwarfare.menu;

import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.ancientwarfare.item.BackpackItem;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * The backpack's inventory. Contents live in the vanilla {@code minecraft:container} data component
 * of the backpack stack (was the NBT tag "backpackItems"). They are copied into a working handler
 * while the menu is open and written back when it closes, as in the original.
 */
public class BackpackMenu extends AwMenu {
    public final int rows;
    private final InteractionHand hand;
    private final ItemStack backpack;
    /** Hotbar index of the held backpack, or -1 when it is in the off hand. */
    private final int lockedHotbarSlot;
    private final ItemStackHandler handler;

    /** Client side: the hand comes from the buffer written by {@link BackpackItem}. */
    public BackpackMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readEnum(InteractionHand.class));
    }

    public BackpackMenu(int containerId, Inventory inventory, InteractionHand hand) {
        super(AncientWarfareCore.BACKPACK_MENU.get(), containerId, inventory);
        this.hand = hand;
        this.backpack = player.getItemInHand(hand);
        // Must be known before addPlayerSlots runs, which asks canAddHotbarSlot.
        this.lockedHotbarSlot = hand == InteractionHand.MAIN_HAND ? inventory.selected : -1;
        this.rows = backpack.getItem() instanceof BackpackItem item ? item.rows : 1;

        this.handler = new ItemStackHandler(rows * 9);
        NonNullList<ItemStack> stored = NonNullList.withSize(rows * 9, ItemStack.EMPTY);
        backpack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(stored);
        for (int i = 0; i < stored.size(); i++) {
            handler.setStackInSlot(i, stored.get(i));
        }

        // Same layout as a vanilla chest screen with this many rows.
        for (int i = 0; i < rows * 9; i++) {
            addSlot(new SlotItemHandler(handler, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return !(stack.getItem() instanceof BackpackItem) && super.mayPlace(stack);
                }
            });
        }
        addPlayerSlots(inventory, 8, 31 + rows * 18, 4);
    }

    @Override
    protected boolean canAddHotbarSlot(int hotbarIndex) {
        return hotbarIndex != lockedHotbarSlot;
    }

    @Override
    public boolean stillValid(Player player) {
        // Close if the backpack leaves the hand (dropped, swapped by another mod, and so on).
        return !backpack.isEmpty() && player.getItemInHand(hand) == backpack;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Number keys and the off-hand key act on the player inventory directly, so leaving the
        // hotbar slot out would not by itself stop the open backpack from being swapped away.
        if (clickType == ClickType.SWAP) {
            boolean swapsHeldBackpack = hand == InteractionHand.MAIN_HAND
                    ? button == lockedHotbarSlot
                    : button == Inventory.SLOT_OFFHAND;
            if (swapsHeldBackpack) {
                return;
            }
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide && player.getItemInHand(hand) == backpack) {
            NonNullList<ItemStack> items = NonNullList.withSize(handler.getSlots(), ItemStack.EMPTY);
            for (int i = 0; i < handler.getSlots(); i++) {
                items.set(i, handler.getStackInSlot(i));
            }
            backpack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int size = handler.getSlots();
        if (index < size) {
            if (!moveItemStackTo(stack, size, size + playerSlots, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, size, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }
}
