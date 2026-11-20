/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/inventory/NpcEquipmentHandler.
 */
package io.github.capttracker.ancientwarfarenpc.inventory;

import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.NpcItemIds;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * The inventory of an NPC as an item handler of 8 slots: 0 main hand, 1 off hand, 2 to 5 feet, legs, chest and head, then the
 * orders slot ({@link NpcBase#ORDER_SLOT}) and the upkeep slot ({@link NpcBase#UPKEEP_SLOT}). The slot numbers are the 1.12.2 ones,
 * see {@link NpcBase#getItemBySlot(int)}.
 */
public class NpcEquipmentHandler implements IItemHandlerModifiable {

    private static final int SIZE_INVENTORY = 8;
    private final NpcBase npc;

    public NpcEquipmentHandler(NpcBase npc) {
        this.npc = npc;
    }

    @Override
    public int getSlots() {
        return SIZE_INVENTORY;
    }

    private int validateSlotIndex(final int slot) {
        if (slot < 0 || slot >= SIZE_INVENTORY) {
            throw new IllegalArgumentException("Slot " + slot + " not in valid range - [0," + SIZE_INVENTORY + ")");
        }
        return slot;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return npc.getItemBySlot(validateSlotIndex(slot));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        validateSlotIndex(slot);

        ItemStack existing = npc.getItemBySlot(slot);

        int limit = getStackLimit(slot, stack);

        if (!existing.isEmpty()) {
            if (!ItemStack.isSameItemSameComponents(stack, existing)) {
                return stack;
            }

            limit -= existing.getCount();
        }

        if (limit <= 0) {
            return stack;
        }

        boolean reachedLimit = stack.getCount() > limit;

        if (!simulate) {
            if (existing.isEmpty()) {
                npc.setItemSlot(slot, reachedLimit ? stack.copyWithCount(limit) : stack);
            } else {
                existing.grow(reachedLimit ? limit : stack.getCount());
            }
        }

        return reachedLimit ? stack.copyWithCount(stack.getCount() - limit) : ItemStack.EMPTY;

    }

    private int getStackLimit(final int slot, ItemStack stack) {
        return Math.min(getSlotLimit(slot), stack.getMaxStackSize());
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount == 0) {
            return ItemStack.EMPTY;
        }

        validateSlotIndex(slot);

        ItemStack existing = npc.getItemBySlot(slot);

        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }

        final int toExtract = Math.min(amount, existing.getMaxStackSize());

        if (existing.getCount() <= toExtract) {
            if (!simulate) {
                npc.setItemSlot(slot, ItemStack.EMPTY);
            }

            return existing;
        } else {
            if (!simulate) {
                npc.setItemSlot(slot, existing.copyWithCount(existing.getCount() - toExtract));
            }

            return existing.copyWithCount(toExtract);
        }
    }

    @Override
    public int getSlotLimit(int slot) {
        return slot == NpcBase.UPKEEP_SLOT || slot == NpcBase.ORDER_SLOT || slot > 1 && slot < NpcBase.ORDER_SLOT ? 1 : 64;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (slot == NpcBase.UPKEEP_SLOT) {
            return NpcItemIds.is(stack, NpcItemIds.UPKEEP_ORDER);
        } else if (slot == NpcBase.ORDER_SLOT) {
            return npc.isValidOrdersStack(stack);
        } else if (slot > 1 && slot < NpcBase.ORDER_SLOT) {
            return stack.getItem().canEquip(stack, NpcBase.getEquipmentSlot(slot), npc);
        }
        return true;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        validateSlotIndex(slot);
        if (ItemStack.matches(npc.getItemBySlot(slot), stack)) {
            return;
        }
        npc.setItemSlot(slot, stack);
    }
}
