/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/container/ContainerEngineeringStation (and the slots of ContainerCraftingRecipeMemory).
 */
package io.github.capttracker.ancientwarfare.menu;

import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.ancientwarfare.block.entity.EngineeringStationBlockEntity;
import io.github.capttracker.ancientwarfare.crafting.AWCraftingManager;
import io.github.capttracker.ancientwarfare.crafting.CraftingMemoryController;
import io.github.capttracker.ancientwarfare.crafting.CraftingRecipeMemory;
import io.github.capttracker.ancientwarfare.crafting.ICraftingContainer;
import io.github.capttracker.ancientwarfare.crafting.ICraftingRecipe;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
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
import net.neoforged.neoforge.items.wrapper.PlayerInvWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * The engineering bench. Slot order (kept from the original): 0 result, 1 research book, 2-10 recipe grid, 11-28
 * storage, then the player inventory.
 *
 * <p>The grid is a template. The recipes it completes are listed by {@link CraftingMemoryController} (research
 * recipes only if the crafter named by the book in the book slot knows their research goal, plus ordinary crafting
 * recipes). Taking the result takes the ingredients, with the counts the recipe asks for, from the storage slots
 * ({@link AWCraftingManager#matchIngredients}); if the storage cannot supply them the grid's own items are used up like
 * in a crafting table.
 */
public class EngineeringStationMenu extends AwMenu implements ICraftingContainer {
    public static final int RESULT_SLOT = 0;
    public static final int BOOK_SLOT = 1;
    public static final int GRID_START = 2;
    public static final int STORAGE_START = GRID_START + EngineeringStationBlockEntity.GRID_SIZE;
    public static final int PLAYER_START = STORAGE_START + EngineeringStationBlockEntity.STORAGE_SIZE;

    private final EngineeringStationBlockEntity station;
    private final CraftingRecipeMemory memory;
    private final CraftingMemoryController controller;
    private final ResultContainer result = new ResultContainer();
    /** Items crafted by the current shift-click, so one click yields at most one stack. */
    private int shiftCraftedCount = 0;

    /** Client side. */
    public EngineeringStationMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, clientStation(inventory, buf));
    }

    private static EngineeringStationBlockEntity clientStation(Inventory inventory, RegistryFriendlyByteBuf buf) {
        var pos = buf.readBlockPos();
        if (inventory.player.level().getBlockEntity(pos) instanceof EngineeringStationBlockEntity station) {
            return station;
        }
        // No block entity on this client (it has not arrived yet): a detached one holds the slots until the server fills them.
        return new EngineeringStationBlockEntity(pos, AncientWarfareCore.ENGINEERING_STATION.get().defaultBlockState());
    }

    public EngineeringStationMenu(int containerId, Inventory inventory, EngineeringStationBlockEntity station) {
        super(AncientWarfareCore.ENGINEERING_STATION_MENU.get(), containerId, inventory);
        this.station = station;
        this.memory = station.memory;
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

        addSlot(new Slot(result, 0, 134, 26) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player taker, ItemStack stack) {
                onResultTaken(taker, stack);
            }
        });
        addSlot(new SlotItemHandler(memory.bookSlot, 0, 8, 26));
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                addSlot(new Slot(memory.craftMatrix, y * 3 + x, 62 + x * 18, 8 + y * 18));
            }
        }
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 9; x++) {
                addSlot(new SlotItemHandler(station.storage, y * 9 + x, 8 + x * 18, 66 + y * 18));
            }
        }
        addPlayerSlots(inventory, 8, 110, 4);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(station, player);
    }

    // ---- recipe list (the screen reads these on the client) ----

    public int getRecipeCount() {
        return controller.getRecipeCount();
    }

    /** Index of the chosen recipe, -1 for none. */
    public int getSelectedRecipeIndex() {
        return controller.getSelectedIndex();
    }

    public void nextRecipe() {
        controller.nextRecipe();
    }

    public void previousRecipe() {
        controller.previousRecipe();
    }

    @Override
    public CraftingMemoryController getCraftingMemoryContainer() {
        return controller;
    }

    @Override
    protected void handlePacketData(CompoundTag data) {
        if (player.level().isClientSide) {
            controller.handleServerData(data);
        } else if (controller.handleClientData(data)) {
            updateResult();
        }
    }

    @Override
    public void broadcastChanges() {
        // The grid lives in the block entity, so nothing calls slotsChanged for it; the controller compares instead.
        if (!player.level().isClientSide && controller.serverRefresh(false)) {
            updateResult();
        }
        super.broadcastChanges();
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

    // ---- taking the result ----

    private void onResultTaken(Player taker, ItemStack crafted) {
        if (!(taker.level() instanceof ServerLevel level)) {
            return;
        }
        ICraftingRecipe recipe = controller.getSelectedRecipe();
        if (recipe.isValid()) {
            crafted.onCraftedBy(level, taker, crafted.getCount());
            if (!takeIngredientsFromStorage(recipe, taker, level)) {
                // Storage could not supply the recipe: consume the grid like a crafting table would.
                consumeGrid(recipe, taker);
            }
            EventHooks.firePlayerCraftingEvent(taker, crafted, memory.craftMatrix);
        }
        controller.serverRefresh(true);
        updateResult();
    }

    /**
     * Original {@code handleOnTake}: the grid's own stacks are preferred if the storage holds them, otherwise any item
     * the ingredients accept is taken. Items the recipe leaves behind that it would accept again (tools) are not taken
     * from storage; everything else it leaves behind goes into storage, or is dropped if that is full.
     */
    private boolean takeIngredientsFromStorage(ICraftingRecipe recipe, Player taker, ServerLevel level) {
        List<ItemStack> gridStacks = memory.getCraftingStacks();
        NonNullList<ItemStack> reusable = AWCraftingManager.getReusableStacks(recipe, memory.getCraftingInput());
        IItemHandler pool = new CombinedInvWrapper(station.storage, new ItemStackHandler(reusable));
        AWCraftingManager.IngredientMatch match = AWCraftingManager.matchIngredients(recipe, gridStacks, pool);
        if (!match.isSuccess()) {
            return false;
        }
        for (ItemStack portion : match.allPortions()) {
            if (reusable.stream().noneMatch(r -> InventoryTools.doItemStacksMatch(portion, r))) {
                AWCraftingManager.extractExact(station.storage, portion, portion.getCount());
            }
        }
        CommonHooks.setCraftingPlayer(taker);
        NonNullList<ItemStack> remaining = recipe.getRemainingItems(AWCraftingManager.fillCraftingMatrixFromInventory(match.representativeStacks()));
        CommonHooks.setCraftingPlayer(null);
        InventoryTools.insertOrDropItems(station.storage, InventoryTools.removeItems(remaining, reusable), level, station.getBlockPos());
        return true;
    }

    /**
     * Used up when the storage cannot supply the recipe: each grid cell gives the count of the ingredient it holds
     * (1.12.2: {@code SlotResearchCrafting.onTake}). Leftovers go back into the cell if it is empty now, else to storage or the player.
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
                    ItemStack notStored = InventoryTools.insertItemStacked(station.storage, leftover, false);
                    if (!notStored.isEmpty() && !taker.getInventory().add(notStored)) {
                        taker.drop(notStored, false);
                    }
                }
            }
        }
    }

    // ---- clicks ----

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        shiftCraftedCount = 0;
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int playerEnd = PLAYER_START + playerSlots;

        if (index == RESULT_SLOT) {
            // Vanilla repeats shift-click on a result slot while it keeps producing the same item.
            shiftCraftedCount += stack.getCount();
            if (shiftCraftedCount > stack.getMaxStackSize()) {
                return ItemStack.EMPTY;
            }
        }
        if (index < GRID_START) {                       // result or book -> player
            if (!moveItemStackTo(stack, PLAYER_START, playerEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < STORAGE_START) {             // grid -> storage
            if (!moveItemStackTo(stack, STORAGE_START, PLAYER_START, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < PLAYER_START) {              // storage -> player
            if (!moveItemStackTo(stack, PLAYER_START, playerEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, BOOK_SLOT, BOOK_SLOT + 1, false)   // player -> book, else storage
                && !moveItemStackTo(stack, STORAGE_START, PLAYER_START, false)) {
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
        // for the result slot the stack is empty by now; the crafted amount is what onTake needs
        slot.onTake(player, index == RESULT_SLOT ? original.copy() : stack);
        return original;
    }

    // ---- recipe viewer support (ICraftingContainer) ----

    @Override
    public IItemHandlerModifiable[] getInventories() {
        return new IItemHandlerModifiable[]{station.storage, new PlayerInvWrapper(player.getInventory())};
    }

    @Override
    public boolean pushCraftingMatrixToInventories() {
        NonNullList<ItemStack> craftingItems = NonNullList.create();
        for (ItemStack stack : memory.craftMatrix.getItems()) {
            if (!stack.isEmpty()) {
                craftingItems.add(stack);
            }
        }
        IItemHandler inventories = new CombinedInvWrapper(station.storage, new PlayerInvWrapper(player.getInventory()));
        if (InventoryTools.insertItems(inventories, craftingItems, true).isEmpty()) {
            InventoryTools.insertItems(inventories, craftingItems, false);
            memory.craftMatrix.clearContent();
            return true;
        }
        return false;
    }
}
