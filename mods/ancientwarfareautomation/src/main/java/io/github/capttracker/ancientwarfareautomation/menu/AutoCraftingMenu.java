/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/container/ContainerWorksiteAutoCrafting (and the slots of
 * core/container/ContainerCraftingRecipeMemory).
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfare.crafting.CraftingMemoryController;
import io.github.capttracker.ancientwarfare.crafting.CraftingRecipeMemory;
import io.github.capttracker.ancientwarfare.crafting.ICraftingContainer;
import io.github.capttracker.ancientwarfare.crafting.ICraftingRecipe;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.AutoCraftingBlockEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import net.neoforged.neoforge.items.wrapper.PlayerInvWrapper;

/**
 * The auto crafting table. Slot order (as in the original): 0 result (display only), 1 research book, 2-10 recipe
 * template, 11-28 resources, 29-37 output (take only), then the player inventory.
 *
 * <p>The template and the recipe list work like the engineering bench's ({@link CraftingMemoryController}: the server
 * lists the recipes the template completes and the screen steps through them). The result slot only shows what the
 * recipe makes; the "Craft" button asks the server to craft once from the resource inventory ({@code craft} key, original
 * name kept), which is also what the table does by itself when it has energy.
 */
public class AutoCraftingMenu extends AutomationMenu<AutoCraftingBlockEntity> implements ICraftingContainer {
    public static final int RESULT_SLOT = 0;
    public static final int BOOK_SLOT = 1;
    public static final int GRID_START = 2;
    private static final String CRAFT_KEY = "craft";

    private final int storageSlotsStart;
    private final int outputSlotsStart;
    private final int playerSlotStart;

    private final CraftingRecipeMemory memory;
    private final CraftingMemoryController controller;
    private final ResultContainer result = new ResultContainer();

    /** Client side. */
    public AutoCraftingMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, AutoCraftingBlockEntity.class));
    }

    public AutoCraftingMenu(int containerId, Inventory inventory, AutoCraftingBlockEntity table) {
        super(AwAutomationMenus.AUTO_CRAFTING.get(), containerId, inventory, table);
        this.memory = table.craftingRecipeMemory;
        this.controller = new CraftingMemoryController(memory, player, new CraftingMemoryController.Channel() {
            @Override
            public void sendToServer(CompoundTag data) {
                sendDataToServer(data);
            }

            @Override
            public void sendToClient(CompoundTag data) {
                sendDataToClient(data);
            }
        });

        addSlot(new Slot(result, 0, 3 * 18 + 3 * 18 + 8 + 18, 18 + 8) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player who) {
                return false;
            }
        });
        addSlot(new SlotItemHandler(memory.bookSlot, 0, 8, 18 + 8));
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                addSlot(new Slot(memory.craftMatrix, y * 3 + x, x * 18 + 8 + 3 * 18, y * 18 + 8));
            }
        }
        storageSlotsStart = slots.size();

        for (int y1 = 0; y1 < 2; y1++) {
            int y2 = y1 * 18 + 8 + 4 * 18 + 4 + 4;
            for (int x1 = 0; x1 < 9; x1++) {
                addSlot(new SlotItemHandler(table.resourceInventory, y1 * 9 + x1, x1 * 18 + 8, y2));
            }
        }
        outputSlotsStart = slots.size();

        int y2 = 8 + 3 * 18 + 4;
        for (int x1 = 0; x1 < 9; x1++) {
            addSlot(new SlotItemHandler(table.outputInventory, x1, x1 * 18 + 8, y2) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        playerSlotStart = slots.size();

        addPlayerSlots(inventory, 8, 8 + 8 + 3 * 18 + 2 * 18 + 4 + 4 + 18, 4);
    }

    @Override
    protected void detectChanges() {
        // The template lives in the block entity, so nothing calls slotsChanged for it; the controller compares instead.
        if (controller.serverRefresh(false)) {
            updateResult();
        }
    }

    private void updateResult() {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        ICraftingRecipe recipe = controller.getSelectedRecipe();
        ItemStack crafted = ItemStack.EMPTY;
        if (recipe.isValid()) {
            ItemStack assembled = recipe.getCraftingResult(memory.getCraftingInput(), level.registryAccess());
            if (assembled.isItemEnabled(level.enabledFeatures())) {
                crafted = assembled;
            }
        }
        if (!ItemStack.matches(result.getItem(0), crafted)) {
            result.setItem(0, crafted);
        }
    }

    @Override
    protected void handlePacketData(CompoundTag data) {
        if (isClient()) {
            controller.handleServerData(data);
            return;
        }
        if (!isRequestAllowed()) {
            return;
        }
        if (controller.handleClientData(data)) {
            updateResult();
        }
        if (data.getBoolean(CRAFT_KEY)) {
            blockEntity.tryCraftItem();
        }
    }

    /** Client: asks the server to craft once. */
    public void craft() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(CRAFT_KEY, true);
        sendDataToServer(tag);
    }

    // ---- recipe list (the screen reads these on the client) ----

    public int getRecipeCount() {
        return controller.getRecipeCount();
    }

    @Override
    public CraftingMemoryController getCraftingMemoryContainer() {
        return controller;
    }

    // ---- shift click ----

    @Override
    public ItemStack quickMoveStack(Player who, int slotClickedIndex) {
        Slot theSlot = slots.get(slotClickedIndex);
        if (!theSlot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack slotStack = theSlot.getItem();
        ItemStack slotStackCopy = slotStack.copy();
        int playerSlotEnd = playerSlotStart + playerSlots;

        if (slotClickedIndex < GRID_START) { // result or book slot: into the player inventory
            if (!moveItemStackTo(slotStack, playerSlotStart, playerSlotEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (slotClickedIndex < storageSlotsStart) { // template: into the resources
            if (!moveItemStackTo(slotStack, storageSlotsStart, outputSlotsStart, false)) {
                return ItemStack.EMPTY;
            }
        } else if (slotClickedIndex < playerSlotStart) { // resource and output slots: into the player inventory
            if (!moveItemStackTo(slotStack, playerSlotStart, playerSlotEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (slotClickedIndex < playerSlotEnd) { // player slots: into the book slot, then the resources
            if (!moveItemStackTo(slotStack, BOOK_SLOT, BOOK_SLOT + 1, false)
                    && !moveItemStackTo(slotStack, storageSlotsStart, outputSlotsStart, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (slotStack.isEmpty()) {
            theSlot.setByPlayer(ItemStack.EMPTY);
        } else {
            theSlot.setChanged();
        }
        if (slotStack.getCount() == slotStackCopy.getCount()) {
            return ItemStack.EMPTY;
        }
        theSlot.onTake(who, slotStack);
        return slotStackCopy;
    }

    // ---- recipe viewer support (ICraftingContainer) ----

    @Override
    public IItemHandlerModifiable[] getInventories() {
        return new IItemHandlerModifiable[]{blockEntity.resourceInventory, blockEntity.outputInventory, new PlayerInvWrapper(player.getInventory())};
    }

    @Override
    public boolean pushCraftingMatrixToInventories() {
        NonNullList<ItemStack> craftingItems = NonNullList.create();
        for (ItemStack stack : memory.craftMatrix.getItems()) {
            if (!stack.isEmpty()) {
                craftingItems.add(stack);
            }
        }
        IItemHandler inventories = new CombinedInvWrapper(blockEntity.resourceInventory, new PlayerInvWrapper(player.getInventory()));
        if (InventoryTools.insertItems(inventories, craftingItems, true).isEmpty()) {
            InventoryTools.insertItems(inventories, craftingItems, false);
            memory.craftMatrix.clearContent();
            return true;
        }
        return false;
    }
}
