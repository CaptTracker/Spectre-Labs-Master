/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/container/ContainerWorksiteBase.
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.InventoryWorksiteBlockEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Base of the worksite inventory menus: the 27 slot main inventory at the top, then whatever inventories the
 * subclass adds, then the player's inventory. The label fields are the y positions of the captions the screen draws
 * above each group (0 = no caption); {@link #guiHeight} is the height of the slot area, the screen adds 12 for its
 * button row.
 *
 * <p>The three buttons of every worksite screen ask the server to open another menu. The request is the NBT key
 * {@value #OPEN_KEY} with one of {@link #OPEN_SIDES}, {@link #OPEN_BOUNDS} and {@link #OPEN_ALT} (original: client
 * side {@code NetworkHandler.openGui}, now done by the server).
 */
public abstract class WorksiteMenuBase<T extends InventoryWorksiteBlockEntity> extends AutomationMenu<T> {
    protected static final int LABEL_GAP = 12;
    public static final String OPEN_KEY = "open";
    public static final String OPEN_SIDES = "sides";
    public static final String OPEN_BOUNDS = "bounds";
    public static final String OPEN_ALT = "alt";

    public int guiHeight;
    public int topLabel;
    public int frontLabel;
    public int bottomLabel;
    public int rearLabel;
    public int leftLabel;
    public int rightLabel;
    public int playerLabel;
    private int tileEntitySlots = 0;

    protected WorksiteMenuBase(MenuType<?> type, int containerId, Inventory playerInventory, T worksite) {
        super(type, containerId, playerInventory, worksite);

        int layerY = 8;

        topLabel = layerY;
        layerY += LABEL_GAP;
        addSlots(worksite.mainInventory, 8, layerY);
    }

    /** Adds the slots of a handler in rows of nine. @return the y coordinate below the last row */
    protected int addSlots(IItemHandler inventory, int xPosStart, int yPosStart) {
        int maxY = 0;
        for (int i = 0; i < inventory.getSlots(); i++) {
            int xPos = xPosStart + (i % 9) * 18;
            int yPos = yPosStart + (i / 9) * 18;
            if (yPos + 18 > maxY) {
                maxY = yPos + 18;
            }
            addSlot(new SlotItemHandler(inventory, i, xPos, yPos));
            tileEntitySlots++;
        }
        return maxY;
    }

    /** Adds the player slots under the worksite slots and sets {@link #guiHeight}. */
    protected void addPlayerArea(Inventory inventory, int layerY) {
        guiHeight = addPlayerSlots(inventory, 8, layerY, 4) + 8;
    }

    /**
     * Original: always returns empty, "not sure wtf the rest of the code is about". The stack is merged (worksite slots
     * to the player inventory, player slots into the worksite slots), the result is not reported to the caller.
     */
    @Override
    public ItemStack quickMoveStack(Player who, int slotClickedIndex) {
        Slot slot = slots.get(slotClickedIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stackFromSlot = slot.getItem();
        if (slotClickedIndex < tileEntitySlots) {
            moveItemStackTo(stackFromSlot, tileEntitySlots, tileEntitySlots + playerSlots, false);
        } else {
            moveItemStackTo(stackFromSlot, 0, tileEntitySlots, true);
        }
        if (stackFromSlot.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return ItemStack.EMPTY;
    }

    // ---- client requests ----

    public final void requestOpenSides() {
        sendDataToServer(OPEN_KEY, StringTag.valueOf(OPEN_SIDES));
    }

    public final void requestOpenBounds() {
        sendDataToServer(OPEN_KEY, StringTag.valueOf(OPEN_BOUNDS));
    }

    public final void requestOpenAlt() {
        sendDataToServer(OPEN_KEY, StringTag.valueOf(OPEN_ALT));
    }

    /** Server: the other screens a worksite menu may open. Subclasses add their own and call super for the rest. */
    @Override
    protected void handlePacketData(CompoundTag data) {
        if (!isRequestAllowed() || !(player instanceof ServerPlayer serverPlayer) || !data.contains(OPEN_KEY, Tag.TAG_STRING)) {
            return;
        }
        handleOpenRequest(serverPlayer, data.getString(OPEN_KEY));
    }

    /** Server: opens the requested menu; unknown requests are ignored. */
    protected void handleOpenRequest(ServerPlayer serverPlayer, String what) {
        switch (what) {
            case OPEN_SIDES -> AwAutomationMenus.openInventorySides(serverPlayer, blockEntity);
            case OPEN_BOUNDS -> AwAutomationMenus.openBounds(serverPlayer, blockEntity);
            case OPEN_ALT -> blockEntity.openAltGui(serverPlayer);
            default -> {
            }
        }
    }
}
