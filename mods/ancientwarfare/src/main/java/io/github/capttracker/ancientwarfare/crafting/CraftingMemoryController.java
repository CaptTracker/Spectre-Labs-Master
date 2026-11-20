/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/container/ContainerCraftingRecipeMemory.
 */
package io.github.capttracker.ancientwarfare.crafting;

import io.github.capttracker.ancientwarfare.crafting.wrappers.NoRecipeWrapper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The menu side of a {@link CraftingRecipeMemory}: finds the recipes the grid completes, keeps the chosen one, and
 * keeps the client's copy of the list in sync over the menu's NBT channel. A menu that has a recipe memory (the
 * engineering bench; the automation warehouse crafting station later) owns one and forwards two things to it:
 * its {@code broadcastChanges} to {@link #serverRefresh} and its {@code handlePacketData} to
 * {@link #handleClientData} / {@link #handleServerData}.
 *
 * <p>In 1.12.2 both sides worked the list out themselves. Now only the server does (it alone knows the crafter's
 * research) and sends the recipe names ({@code recipes}) and the chosen index ({@code selected}); the client only
 * displays them and asks for another recipe with {@code recipe}.
 */
public class CraftingMemoryController {
    /** Key of a recipe name: sent by the client to choose a recipe. */
    public static final String RECIPE_TAG = "recipe";
    private static final String RECIPES_TAG = "recipes";
    private static final String SELECTED_TAG = "selected";
    /** The list is recomputed this often even if the grid did not change, so newly learned research shows up. */
    private static final int PERIODIC_REFRESH_TICKS = 40;

    /** How the controller talks to the other copy of the menu (the menu's protected {@code sendDataTo...} methods). */
    public interface Channel {
        void sendToServer(CompoundTag data);

        void sendToClient(CompoundTag data);
    }

    private final CraftingRecipeMemory memory;
    private final Player player;
    private final Channel channel;

    // server
    private List<ICraftingRecipe> recipes = new ArrayList<>();
    private final List<ItemStack> lastGrid = new ArrayList<>();
    private ItemStack lastBook = ItemStack.EMPTY;
    private RecipeResourceLocation lastRecipe = RecipeResourceLocation.NO_RECIPE_REGISTRY_NAME;
    private boolean everRefreshed = false;
    private int ticksSinceRefresh = 0;

    // client
    private List<RecipeResourceLocation> clientRecipes = List.of();

    private int selectedIndex = -1;

    public CraftingMemoryController(CraftingRecipeMemory memory, Player player, Channel channel) {
        this.memory = memory;
        this.player = player;
        this.channel = channel;
    }

    public CraftingRecipeMemory getMemory() {
        return memory;
    }

    // ---- both sides ----

    public int getRecipeCount() {
        return player.level().isClientSide ? clientRecipes.size() : recipes.size();
    }

    /** Index of the chosen recipe in the list, -1 for none. */
    public int getSelectedIndex() {
        return selectedIndex;
    }

    /** The names of the recipes the grid completes, sorted. */
    public List<RecipeResourceLocation> getRecipeNames() {
        if (player.level().isClientSide) {
            return clientRecipes;
        }
        List<RecipeResourceLocation> names = new ArrayList<>();
        recipes.forEach(r -> names.add(r.getRegistryName()));
        return names;
    }

    public ICraftingRecipe getSelectedRecipe() {
        List<RecipeResourceLocation> names = getRecipeNames();
        if (selectedIndex < 0 || selectedIndex >= names.size()) {
            return NoRecipeWrapper.INSTANCE;
        }
        return AWCraftingManager.getRecipe(names.get(selectedIndex), player.level());
    }

    // ---- client ----

    /** Client: choose the next recipe of the list (wraps around). */
    public void nextRecipe() {
        if (clientRecipes.size() > 1) {
            chooseOnClient(selectedIndex + 1 < clientRecipes.size() ? selectedIndex + 1 : 0);
        }
    }

    /** Client: choose the previous recipe of the list (wraps around). */
    public void previousRecipe() {
        if (clientRecipes.size() > 1) {
            chooseOnClient(selectedIndex - 1 >= 0 ? selectedIndex - 1 : clientRecipes.size() - 1);
        }
    }

    private void chooseOnClient(int index) {
        selectedIndex = index;
        CompoundTag tag = new CompoundTag();
        tag.putString(RECIPE_TAG, clientRecipes.get(index).toString());
        channel.sendToServer(tag);
    }

    /** Client: the list the server sent. The server's choice replaces whatever the client guessed. */
    public void handleServerData(CompoundTag tag) {
        if (!tag.contains(RECIPES_TAG, Tag.TAG_LIST)) {
            return;
        }
        ListTag list = tag.getList(RECIPES_TAG, Tag.TAG_STRING);
        List<RecipeResourceLocation> names = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            names.add(RecipeResourceLocation.deserialize(list.getString(i)));
        }
        clientRecipes = names;
        selectedIndex = tag.getInt(SELECTED_TAG);
        if (selectedIndex >= names.size()) {
            selectedIndex = names.isEmpty() ? -1 : 0;
        }
    }

    // ---- server ----

    /**
     * Server: call every tick (from {@code broadcastChanges}). Recomputes the recipe list when the grid, the book or
     * the chosen recipe changed (or now and then), keeps the chosen recipe if it is still possible and otherwise
     * takes the first one, and tells the client when the list or choice changed.
     *
     * @param force recompute even if nothing seems to have changed
     * @return true if the grid, book, list or choice changed, so the menu should recompute its result slot
     */
    public boolean serverRefresh(boolean force) {
        Level level = player.level();
        if (level.isClientSide) {
            return false;
        }
        boolean periodic = ++ticksSinceRefresh >= PERIODIC_REFRESH_TICKS;
        boolean inputsChanged = gridOrBookChanged() || !memory.getRecipeId().equals(lastRecipe);
        if (!(force || periodic || inputsChanged || !everRefreshed)) {
            return false;
        }
        ticksSinceRefresh = 0;

        List<ICraftingRecipe> found = AWCraftingManager.findMatchingRecipes(memory.getCraftingInput(), level, memory.getResearcherId());
        found.sort(Comparator.comparing(r -> r.getRegistryName().toString()));

        int index = indexOf(found, memory.getRecipeId());
        if (index < 0) {
            index = found.isEmpty() ? -1 : 0;
        }
        memory.setRecipeId(index < 0 ? RecipeResourceLocation.NO_RECIPE_REGISTRY_NAME : found.get(index).getRegistryName());

        boolean listChanged = !everRefreshed || index != selectedIndex || !sameNames(found, recipes);
        recipes = found;
        selectedIndex = index;
        lastRecipe = memory.getRecipeId();
        snapshotInputs();
        everRefreshed = true;

        if (listChanged) {
            sendToClient();
        }
        return inputsChanged || listChanged || force;
    }

    /**
     * Server: the client asked for a recipe. Only recipes the grid currently completes are accepted, as the client
     * is not trusted.
     *
     * @return true if the choice changed
     */
    public boolean handleClientData(CompoundTag tag) {
        if (!tag.contains(RECIPE_TAG, Tag.TAG_STRING)) {
            return false;
        }
        RecipeResourceLocation wanted = RecipeResourceLocation.deserialize(tag.getString(RECIPE_TAG));
        if (indexOf(recipes, wanted) < 0) {
            return false;
        }
        memory.setRecipeId(wanted);
        return serverRefresh(true);
    }

    /**
     * Server: sets the recipe from code. With {@code forceSet} it is set even if the grid does not complete it right
     * now (a recipe viewer sets the recipe, then fills the grid); the next refresh keeps it only if the grid then does.
     */
    public void setRecipe(ICraftingRecipe recipe, boolean forceSet) {
        if (forceSet || indexOf(recipes, recipe.getRegistryName()) >= 0) {
            memory.setRecipe(recipe);
            serverRefresh(true);
        }
    }

    /** Server: send the current list, for a viewer that just opened the menu. */
    public void sendToClient() {
        ListTag list = new ListTag();
        for (ICraftingRecipe recipe : recipes) {
            list.add(StringTag.valueOf(recipe.getRegistryName().toString()));
        }
        CompoundTag tag = new CompoundTag();
        tag.put(RECIPES_TAG, list);
        tag.putInt(SELECTED_TAG, selectedIndex);
        channel.sendToClient(tag);
    }

    private boolean gridOrBookChanged() {
        List<ItemStack> now = memory.craftMatrix.getItems();
        boolean changed = lastGrid.size() != now.size() || !ItemStack.matches(lastBook, memory.bookSlot.getStackInSlot(0));
        for (int i = 0; !changed && i < now.size(); i++) {
            changed = !ItemStack.matches(lastGrid.get(i), now.get(i));
        }
        return changed;
    }

    private void snapshotInputs() {
        lastGrid.clear();
        memory.craftMatrix.getItems().forEach(stack -> lastGrid.add(stack.copy()));
        lastBook = memory.bookSlot.getStackInSlot(0).copy();
    }

    private static int indexOf(List<ICraftingRecipe> list, RecipeResourceLocation name) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getRegistryName().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean sameNames(List<ICraftingRecipe> a, List<ICraftingRecipe> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).getRegistryName().equals(b.get(i).getRegistryName())) {
                return false;
            }
        }
        return true;
    }
}
