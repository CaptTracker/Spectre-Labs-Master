/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/container/ContainerWarehouseCraftingStation (and the slots of
 * core/container/ContainerCraftingRecipeMemory).
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfare.crafting.AWCraftingManager;
import io.github.capttracker.ancientwarfare.crafting.CraftingMemoryController;
import io.github.capttracker.ancientwarfare.crafting.CraftingRecipeMemory;
import io.github.capttracker.ancientwarfare.crafting.ICraftingContainer;
import io.github.capttracker.ancientwarfare.crafting.ICraftingRecipe;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseCraftingStationBlockEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.PlayerInvWrapper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The warehouse crafting table. Slot order (as in the original): 0 result, 1 research book, 2-10 recipe template, then the
 * player inventory. The ingredients come from the warehouse below the table (its stored items, through the virtual item
 * handler {@link WarehouseBlockEntity#getItemHandler()}); if the warehouse cannot supply the recipe the template items
 * themselves are used up like in a crafting table.
 *
 * <p>The template and recipe list work like the engineering bench's ({@link CraftingMemoryController}). The warehouse's
 * item map is kept in sync with the usual change list ({@link ItemMapSync}, key {@code changeList}); the client writes it
 * into its copy of the warehouse block entity's cache, as the original did.
 *
 * <p>Differences from the original: the result is worked out on the server only (the client slot shows the synced
 * stack), there is no client side prediction of the warehouse's contents.
 */
public class WarehouseCraftingStationMenu extends AutomationMenu<WarehouseCraftingStationBlockEntity> implements ICraftingContainer, WarehouseBlockEntity.Viewer {
    public static final int RESULT_SLOT = 0;
    public static final int BOOK_SLOT = 1;
    public static final int GRID_START = 2;
    public static final int PLAYER_START = GRID_START + 9;

    private final CraftingRecipeMemory memory;
    private final CraftingMemoryController controller;
    private final ResultContainer result = new ResultContainer();
    private final ItemMapSync sync = new ItemMapSync();
    private boolean shouldUpdate = true;
    /** Items crafted by the current shift-click, so one click yields at most one stack. */
    private int currentCraftTotalSize = 0;

    /** Client side. */
    public WarehouseCraftingStationMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, WarehouseCraftingStationBlockEntity.class));
    }

    public WarehouseCraftingStationMenu(int containerId, Inventory inventory, WarehouseCraftingStationBlockEntity station) {
        super(AwAutomationMenus.WAREHOUSE_CRAFTING_STATION.get(), containerId, inventory, station);
        this.memory = station.craftingRecipeMemory;
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
            public void onTake(Player taker, ItemStack stack) {
                onResultTaken(taker, stack);
            }
        });
        addSlot(new SlotItemHandler(memory.bookSlot, 0, 8, 18 + 8));
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                addSlot(new Slot(memory.craftMatrix, y * 3 + x, x * 18 + 8 + 3 * 18, y * 18 + 8));
            }
        }

        addPlayerSlots(inventory, 8, 8 + 3 * 18 + 8, 4);
        WarehouseBlockEntity warehouse = station.getWarehouse();
        if (warehouse != null) {
            warehouse.addCraftingViewer(this);
        }
    }

    @Nullable
    private WarehouseBlockEntity warehouse() {
        return blockEntity.getWarehouse();
    }

    @Override
    public void removed(Player who) {
        WarehouseBlockEntity warehouse = warehouse();
        if (warehouse != null) {
            warehouse.removeCraftingViewer(this);
        }
        super.removed(who);
    }

    /** Server, from the warehouse: its content changed. */
    @Override
    public void onWarehouseInventoryUpdated() {
        shouldUpdate = true;
    }

    // ---- result ----

    @Override
    protected void detectChanges() {
        if (shouldUpdate) {
            WarehouseBlockEntity warehouse = warehouse();
            sync.sendChanges(registries(), map -> {
                if (warehouse != null) {
                    warehouse.getItems(map);
                }
            }, this::sendDataToClient);
            shouldUpdate = false;
        }
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
            if (sync.handle(registries(), data)) {
                // original: the client's copy of the warehouse takes over the received map
                WarehouseBlockEntity warehouse = warehouse();
                if (warehouse != null) {
                    warehouse.clearItemCache();
                    warehouse.addItemsToCache(sync.map());
                }
            } else {
                controller.handleServerData(data);
            }
            refreshGui();
        } else if (isRequestAllowed() && controller.handleClientData(data)) {
            updateResult();
        }
    }

    // ---- taking the result ----

    private void onResultTaken(Player taker, ItemStack crafted) {
        if (!(taker.level() instanceof ServerLevel level)) {
            return;
        }
        ICraftingRecipe recipe = controller.getSelectedRecipe();
        if (recipe.isValid()) {
            crafted.onCraftedBy(level, taker, crafted.getCount());
            if (!takeIngredientsFromWarehouse(recipe, taker, level)) {
                // The warehouse could not supply the recipe: consume the grid like a crafting table would.
                consumeGrid(recipe, taker);
            }
            EventHooks.firePlayerCraftingEvent(taker, crafted, memory.craftMatrix);
        }
        controller.serverRefresh(true);
        updateResult();
    }

    /**
     * Original {@code handleOnTake}: the grid's own stacks are preferred if the warehouse holds them, otherwise any item the
     * ingredients accept is taken. Items the recipe leaves behind that it would accept again (tools) are not taken from the
     * warehouse; everything else it leaves behind goes into the warehouse, or is dropped if that is full.
     */
    private boolean takeIngredientsFromWarehouse(ICraftingRecipe recipe, Player taker, ServerLevel level) {
        WarehouseBlockEntity warehouse = warehouse();
        if (warehouse == null) {
            return false;
        }
        IItemHandlerModifiable handler = warehouse.getItemHandler();
        List<ItemStack> gridStacks = memory.getCraftingStacks();
        NonNullList<ItemStack> reusable = AWCraftingManager.getReusableStacks(recipe, memory.getCraftingInput());
        IItemHandler pool = new CombinedInvWrapper(new ItemStackHandler(reusable), handler);
        AWCraftingManager.IngredientMatch match = AWCraftingManager.matchIngredients(recipe, gridStacks, pool);
        if (!match.isSuccess()) {
            return false;
        }
        for (ItemStack portion : match.allPortions()) {
            if (reusable.stream().noneMatch(r -> InventoryTools.doItemStacksMatch(portion, r))) {
                AWCraftingManager.extractExact(handler, portion, portion.getCount());
            }
        }
        CommonHooks.setCraftingPlayer(taker);
        NonNullList<ItemStack> remaining = recipe.getRemainingItems(AWCraftingManager.fillCraftingMatrixFromInventory(match.representativeStacks()));
        CommonHooks.setCraftingPlayer(null);
        InventoryTools.insertOrDropItems(handler, InventoryTools.removeItems(remaining, reusable), level, blockEntity.getBlockPos());
        return true;
    }

    /**
     * Used up when the warehouse cannot supply the recipe: each grid cell gives the count of the ingredient it holds
     * (1.12.2: {@code SlotResearchCrafting.onTake}). Leftovers go back into the cell if it is empty now, else to the
     * warehouse or the player.
     */
    private void consumeGrid(ICraftingRecipe recipe, Player taker) {
        CraftingInput.Positioned positioned = CraftingInput.ofPositioned(3, 3, memory.getCraftingStacks());
        CraftingInput input = positioned.input();
        CommonHooks.setCraftingPlayer(taker);
        NonNullList<ItemStack> remaining = recipe.getRemainingItems(input);
        CommonHooks.setCraftingPlayer(null);

        List<SizedIngredient> ingredients = new ArrayList<>();
        recipe.getIngredients().stream().filter(i -> !ICraftingRecipe.isEmptySlot(i)).forEach(ingredients::add);
        boolean[] used = new boolean[ingredients.size()];
        WarehouseBlockEntity warehouse = warehouse();

        for (int y = 0; y < input.height(); y++) {
            for (int x = 0; x < input.width(); x++) {
                int gridSlot = x + positioned.left() + (y + positioned.top()) * 3;
                ItemStack inCell = memory.craftMatrix.getItem(gridSlot);
                if (!inCell.isEmpty()) {
                    int ingredientIndex = -1;
                    for (int j = 0; j < ingredients.size() && ingredientIndex < 0; j++) {
                        if (!used[j] && ingredients.get(j).test(inCell)) {
                            ingredientIndex = j;
                            used[j] = true;
                        }
                    }
                    memory.craftMatrix.removeItem(gridSlot, ingredientIndex >= 0 ? ingredients.get(ingredientIndex).count() : 1);
                }
                ItemStack leftover = remaining.get(x + y * input.width());
                if (leftover.isEmpty()) {
                    continue;
                }
                if (memory.craftMatrix.getItem(gridSlot).isEmpty()) {
                    memory.craftMatrix.setItem(gridSlot, leftover);
                } else {
                    ItemStack notStored = warehouse == null ? leftover : InventoryTools.insertItemStacked(warehouse.getItemHandler(), leftover, false);
                    if (!notStored.isEmpty() && !taker.getInventory().add(notStored)) {
                        taker.drop(notStored, false);
                    }
                }
            }
        }
    }

    // ---- clicks ----

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player who) {
        currentCraftTotalSize = 0;
        super.clicked(slotId, button, clickType, who);
    }

    @Override
    public ItemStack quickMoveStack(Player who, int slotClickedIndex) {
        Slot theSlot = slots.get(slotClickedIndex);
        if (!theSlot.hasItem()) {
            return ItemStack.EMPTY;
        }
        if (slotClickedIndex == RESULT_SLOT) {
            // Vanilla repeats shift-click on a result slot while it keeps producing the same item.
            ItemStack craftedStack = theSlot.getItem();
            currentCraftTotalSize += craftedStack.getCount();
            if (currentCraftTotalSize > craftedStack.getMaxStackSize()) {
                return ItemStack.EMPTY;
            }
        }
        ItemStack slotStack = theSlot.getItem();
        ItemStack slotStackCopy = slotStack.copy();
        int playerSlotEnd = PLAYER_START + playerSlots;

        if (slotClickedIndex < PLAYER_START) { // result, book and template slots: into the player inventory
            if (!moveItemStackTo(slotStack, PLAYER_START, playerSlotEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (slotClickedIndex < playerSlotEnd && !moveItemStackTo(slotStack, BOOK_SLOT, BOOK_SLOT + 1, false)) {
            return ItemStack.EMPTY; // player slots: only the book slot takes them
        }
        if (slotStack.isEmpty()) {
            theSlot.setByPlayer(ItemStack.EMPTY);
        } else {
            theSlot.setChanged();
        }
        if (slotStack.getCount() == slotStackCopy.getCount()) {
            return ItemStack.EMPTY;
        }
        // for the result slot the stack is empty by now; the crafted amount is what onTake needs
        theSlot.onTake(who, slotClickedIndex == RESULT_SLOT ? slotStackCopy.copy() : slotStack);
        return slotStackCopy;
    }

    // ---- recipe list (the screen reads these on the client) ----

    public int getRecipeCount() {
        return controller.getRecipeCount();
    }

    @Override
    public CraftingMemoryController getCraftingMemoryContainer() {
        return controller;
    }

    // ---- recipe viewer support (ICraftingContainer) ----

    @Override
    public IItemHandlerModifiable[] getInventories() {
        WarehouseBlockEntity warehouse = warehouse();
        if (warehouse == null) {
            return new IItemHandlerModifiable[]{new PlayerInvWrapper(player.getInventory())};
        }
        return new IItemHandlerModifiable[]{warehouse.getItemHandler(), new PlayerInvWrapper(player.getInventory())};
    }

    @Override
    public boolean pushCraftingMatrixToInventories() {
        NonNullList<ItemStack> craftingItems = NonNullList.create();
        for (ItemStack stack : memory.craftMatrix.getItems()) {
            if (!stack.isEmpty()) {
                craftingItems.add(stack);
            }
        }
        WarehouseBlockEntity warehouse = warehouse();
        IItemHandler inventories = warehouse == null
                ? new PlayerInvWrapper(player.getInventory())
                : new CombinedInvWrapper(warehouse.getItemHandler(), new PlayerInvWrapper(player.getInventory()));

        if (InventoryTools.insertItems(inventories, craftingItems, true).isEmpty()) {
            List<ItemStack> remainingItems = InventoryTools.insertItems(inventories, craftingItems, false);
            memory.craftMatrix.clearContent();
            if (!remainingItems.isEmpty()) {
                InventoryTools.insertItems(new InvWrapper(memory.craftMatrix), remainingItems, false);
                return false;
            }
            return true;
        }
        return false;
    }
}
