/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/container/ContainerNpcInventory.
 */
package io.github.capttracker.ancientwarfarenpc.menu;

import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcMenus;
import io.github.capttracker.ancientwarfarenpc.inventory.NpcEquipmentHandler;
import io.github.capttracker.ancientwarfarenpc.skin.NpcSkinSettings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

import javax.annotation.Nullable;

/**
 * The inventory of an NPC (1.12.2: {@code GUI_NPC_INVENTORY}, 4): its eight equipment slots (weapon, shield, armor, orders and upkeep orders,
 * through {@link NpcEquipmentHandler}), the player's inventory, and buttons for name, skin, repack, home and follow.
 *
 * <p>Slot order is the original's: NPC slots 0 to 7, the hotbar (8 to 16), then the main inventory (17 to 43); the shift-click routing relies on it.
 * The custom name, the skin and the do-not-pursue flag are kept in the menu and written to the NPC when the menu closes, as before.
 *
 * <p>Changes from 1.12.2: every client message is only acted on while the player may use the menu (the commander of a player-owned NPC, or
 * a creative player for a faction NPC) and the name and skin are checked ({@link NpcSkinInput}); shift-clicking the weapon slot no longer drops
 * the remainder of the stack that stayed in the slot (the original dropped a copy of it, duplicating the item).
 *
 * <p>Messages: client to server {@code customName}, {@code repack}, {@code setHome}, {@code clearHome}, {@code togglefollow},
 * {@code donotpursue}, {@code skinSettings}; server to client {@code donotpursue} (the client's NPC does not know the flag).
 */
public class NpcInventoryMenu extends NpcMenuBase<NpcBase> implements ISkinSettingsMenu {
    /** If the NPC should not pursue targets away from its position or route. */
    public boolean doNotPursue;
    public final boolean isArcher;
    public final int guiHeight;
    private String name;
    public NpcSkinSettings skinSettings;

    /** Client side: the NPC's entity id comes from the buffer written by {@link NpcMenuOpener}. */
    public NpcInventoryMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, find(inventory.player, buf.readVarInt(), NpcBase.class));
    }

    public NpcInventoryMenu(int containerId, Inventory inventory, @Nullable NpcBase npc) {
        super(AwNpcMenus.INVENTORY_MENU.get(), containerId, inventory, npc);
        if (npc != null) {
            NpcEquipmentHandler equipment = new NpcEquipmentHandler(npc);
            addSlot(new SlotItemHandler(equipment, 0, 8, 8)); //weapon slot
            addSlot(new SlotItemHandler(equipment, 1, 8, 8 + 18));//shield slot
            addSlot(new SlotItemHandler(equipment, 2, 8, 8 + 18 * 5));//boots
            addSlot(new SlotItemHandler(equipment, 3, 8, 8 + 18 * 4));//legs
            addSlot(new SlotItemHandler(equipment, 4, 8, 8 + 18 * 3));//chest
            addSlot(new SlotItemHandler(equipment, 5, 8, 8 + 18 * 2));//helm
            addSlot(new SlotItemHandler(equipment, 6, 8 + 18 * 2, 8 + 18 * 3));//work/combat/route orders slot
            addSlot(new SlotItemHandler(equipment, 7, 8 + 18 * 2, 8 + 18 * 2));//upkeep orders slot
        }
        guiHeight = addPlayerSlots(inventory, 8, 8 + 5 * 18 + 8 + 18 + 18, 4) + 8;
        if (npc != null) {
            isArcher = npc.getNpcSubType().equals("archer");
            name = npc.hasCustomName() ? npc.getCustomName().getString() : "";
            doNotPursue = npc.getDoNotPursue();
            skinSettings = npc.getSkinSettings();
        } else {
            isArcher = false;
            name = "";
            skinSettings = new NpcSkinSettings();
        }
    }

    /** The custom name as edited so far (the text box shows it). */
    public String getName() {
        return name;
    }

    // ---- client requests ----

    public void sendChangesToServer() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("donotpursue", doNotPursue);
        sendDataToServer(tag);
    }

    public void handleNpcNameUpdate(String newName) {
        name = newName;
    }

    /** Sends the custom name; the server keeps it until the menu closes. */
    public void setName() {
        CompoundTag tag = new CompoundTag();
        tag.putString("customName", name);
        sendDataToServer(tag);
    }

    @Override
    public void handleNpcSkinUpdate() {
        sendDataToServer("skinSettings", skinSettings.serializeNBT());
    }

    @Override
    public NpcSkinSettings getSkinSettings() {
        return skinSettings;
    }

    @Override
    public void setSkinSettings(NpcSkinSettings skinSettings) {
        this.skinSettings = skinSettings;
    }

    // ---- sync ----

    @Override
    protected void sendInitialState() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("donotpursue", doNotPursue);
        sendDataToClient(tag);
    }

    @Override
    protected void handlePacketData(CompoundTag tag) {
        if (isClient()) {
            // from the server
            if (tag.contains("donotpursue", Tag.TAG_BYTE)) {
                doNotPursue = tag.getBoolean("donotpursue");
            }
            refreshGui();
            return;
        }
        if (!isRequestAllowed() || npc == null) {
            return;
        }
        if (tag.contains("customName", Tag.TAG_STRING)) {
            name = sanitizeName(tag.getString("customName"));
        }
        if (tag.contains("repack")) {
            npc.repackEntity(player);
        } else if (tag.contains("setHome")) {
            npc.setHomeAreaAtCurrentPosition();
        } else if (tag.contains("clearHome")) {
            npc.clearRestriction();
        } else if (tag.contains("togglefollow")) {
            LivingEntity following = npc.getFollowingEntity();
            if (following instanceof Player followed && followed.getGameProfile().getName().equals(player.getGameProfile().getName())) {
                npc.clearFollowingEntity();
            } else {
                npc.setFollowingEntity(player);
            }
        }
        if (tag.contains("donotpursue", Tag.TAG_BYTE)) {
            doNotPursue = tag.getBoolean("donotpursue");
        }
        if (tag.contains("skinSettings", Tag.TAG_COMPOUND)) {
            NpcSkinSettings sent = NpcSkinInput.read(tag.getCompound("skinSettings"));
            if (sent != null) {
                skinSettings = sent;
                npc.setSkinSettings(sent);
            }
        }
    }

    /** Server side: writes name, skin and the flag to the NPC (1.12.2: {@code onContainerClosed}). */
    @Override
    public void removed(Player who) {
        super.removed(who);
        if (!isClient() && npc != null && npc.isAlive() && mayUse(who)) {
            npc.setCustomName(name.isEmpty() ? null : Component.literal(name));
            npc.setSkinSettings(skinSettings);
            npc.setDoNotPursue(doNotPursue);
        }
    }

    // ---- shift-click ----

    @Override
    public ItemStack quickMoveStack(Player who, int slotClickedIndex) {
        if (npc == null || slotClickedIndex < 0 || slotClickedIndex >= slots.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = slots.get(slotClickedIndex);
        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemstack = slotStack.copy();
            EquipmentSlot equipmentSlot = npc.getEquipmentSlotForItem(itemstack);
            int equipmentIndex = NpcBase.getEquipmentSlotIndex(equipmentSlot);
            if (slotClickedIndex < 8) {
                if (!moveItemStackTo(slotStack, 8, 44, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (equipmentSlot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && equipmentIndex >= 2 && !slots.get(equipmentIndex).hasItem()) {
                if (!moveItemStackTo(slotStack, equipmentIndex, equipmentIndex + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (equipmentSlot == EquipmentSlot.MAINHAND && !slots.get(0).hasItem() && !slots.get(1).hasItem()) {
                if (!moveItemStackTo(slotStack, 0, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (equipmentSlot == EquipmentSlot.OFFHAND && !slots.get(1).hasItem()) {
                if (!moveItemStackTo(slotStack, 1, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!slots.get(6).hasItem() && slots.get(6).mayPlace(slotStack)) {
                if (!moveItemStackTo(slotStack, 6, 7, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!slots.get(7).hasItem() && slots.get(7).mayPlace(slotStack)) {
                if (!moveItemStackTo(slotStack, 7, 8, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotClickedIndex < 17) {
                if (!moveItemStackTo(slotStack, 17, 44, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotClickedIndex < 44) {
                if (!moveItemStackTo(slotStack, 8, 17, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(slotStack, 8, 44, false)) {
                return ItemStack.EMPTY;
            }

            if (slotStack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (slotStack.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(who, slotStack);
        }
        return itemstack;
    }
}
