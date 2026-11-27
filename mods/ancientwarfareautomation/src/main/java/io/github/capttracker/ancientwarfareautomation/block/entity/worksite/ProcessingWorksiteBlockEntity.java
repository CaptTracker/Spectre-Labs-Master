/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 8): the shared base of the production sites (sawmill, bakery).
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import io.github.capttracker.ancientwarfare.api.IWorksiteLogistics;
import io.github.capttracker.ancientwarfare.api.IWorksiteProduction;
import io.github.capttracker.ancientwarfare.api.IWorksiteStatus;
import io.github.capttracker.ancientwarfare.api.WorkAction;
import io.github.capttracker.ancientwarfare.api.SettlementDemand;
import io.github.capttracker.ancientwarfare.api.WorkTask;
import io.github.capttracker.ancientwarfare.api.WorksiteStock;
import io.github.capttracker.ancientwarfare.block.BlockRotationHandler.RelativeSide;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The base of the production sites (work type crafting): a worker stands at the block and turns the raw input of an input store into the products of a
 * list of recipes, one batch per task, while couriers bring the input (and the fuel) and take the products from the main inventory.
 *
 * <p><b>Recipes.</b> A subclass lists its {@link ProcessingRecipe}s. Each has an on/off switch (the screen sets it; {@link ProcessingRecipe#defaultEnabled}
 * in a new site) and a target stock: "keep N in storage", 0 for no limit. The stock of a recipe is its product in the site's own main inventory plus in the
 * storages of the settlement around the site ({@link WorksiteStock}). A recipe whose stock has reached its target is not made.
 *
 * <p><b>What is made next</b> ({@link #select}): of the recipes that can be made now (switched on, stock below target, the input is there, the products
 * fit, fuel for it), the one furthest below its target (lowest stock divided by target) first; recipes without a target take turns after that.
 *
 * <p><b>Work.</b> The one task of the site is {@code craftAction()} at the block itself, as long as a batch can be made (base duration: the batch's
 * ticks). When the worker has swung that long the batch is taken out of the input store (and the fuel burnt) and the products go into the main
 * inventory ({@link #craft}). With {@code machines_work_alone} it is crafted with torque as a machine instead, one batch per work unit.
 *
 * <p><b>Inventories.</b> Main (products, where the couriers take from), input ({@link #inputInventory}, 9 slots, takes only raw inputs of a recipe), and
 * fuel ({@link #fuelInventory}, 3 slots, only for sites that burn fuel; the fuel is burnt per batch from a burn time pool, as a furnace burns it).
 * Saved: both stores, the burn pool, and per recipe id the switch and the target.
 *
 * <p><b>Settlement demand</b> (rework phase 8). The settlement tells ({@link SettlementDemand}) what it is short of: a pickaxe for a worker without one,
 * food, planks. {@link #select} makes what is needed first (the most urgent, then the most missing), then what is furthest below its target, then the
 * recipes without a target. A recipe that is needed is made even if its target stock has been reached, and, with {@code demand_enables_recipes} (true),
 * even if its switch is off (the switch then means "only when the settlement needs it"). The couriers serve the inputs of needed recipes first, the
 * recipe screen marks them, and {@link IWorksiteProduction} tells the settlement what the site makes and what it is starved of.
 */
public abstract class ProcessingWorksiteBlockEntity extends InventoryWorksiteBlockEntity implements IWorksiteStatus, IWorksiteProduction {
    public static final int INPUT_SIZE = 9;
    public static final int FUEL_SIZE = 3;
    /** The highest "keep N in storage" number the screen may set. */
    public static final int MAX_TARGET = 4096;
    /** The status keys the screen shows besides the ones of {@link IWorksiteStatus}. */
    public static final String STATUS_WORKING = "working";
    public static final String STATUS_IDLE = "idle";
    public static final String STATUS_FULL = "storage_full";
    private static final int FUEL_TARGET = 16;
    private static final int CACHE_TICKS = 20;
    private static final IWorksiteAction CRAFT_ACTION = WorksiteActions.full();

    public final ItemStackHandler inputInventory;
    @Nullable
    public final ItemStackHandler fuelInventory;

    private boolean[] enabled = new boolean[0];
    private int[] targets = new int[0];
    private int fuelBurn = 0;
    private int rotation = 0;
    @Nullable
    private Selection cached;
    private long cachedUntil = Long.MIN_VALUE;
    private SettlementDemand.Demand[] demands = new SettlementDemand.Demand[0];
    /** The product counts of the settlement's storages per recipe, kept for {@link #CACHE_TICKS} (counting them walks every storage). */
    private int[] storedStock = new int[0];
    private long storedStockUntil = Long.MIN_VALUE;
    private long demandsUntil = Long.MIN_VALUE;

    /** What {@link #select} chose: the recipe (index into {@link #recipes}) and its batch. */
    public record Selection(int index, ProcessingRecipe.Batch batch) {}

    private enum State { DISABLED, SATISFIED, NO_INPUT, OUTPUT_FULL, NO_FUEL, BLOCKED, READY }

    private record Eval(State state, @Nullable ProcessingRecipe.Batch batch) {}

    protected ProcessingWorksiteBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int fuelSlots) {
        super(type, pos, state);
        inputInventory = new ItemStackHandler(INPUT_SIZE) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                inventoryChanged();
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return isInput(stack);
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return isInput(stack) ? super.insertItem(slot, stack, simulate) : stack;
            }
        };
        setSideInventory(RelativeSide.FRONT, inputInventory, RelativeSide.FRONT);
        if (fuelSlots > 0) {
            fuelInventory = new ItemStackHandler(fuelSlots) {
                @Override
                protected void onContentsChanged(int slot) {
                    setChanged();
                    inventoryChanged();
                }

                @Override
                public boolean isItemValid(int slot, ItemStack stack) {
                    return isFuel(stack);
                }

                @Override
                public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                    return isFuel(stack) ? super.insertItem(slot, stack, simulate) : stack;
                }
            };
            setSideInventory(RelativeSide.BOTTOM, fuelInventory, RelativeSide.TOP);
        } else {
            fuelInventory = null;
        }
    }

    // ---------------------------------------------------------------- what a subclass says

    /** The recipes of the site, in the order of the screen. The same list every time. */
    protected abstract List<ProcessingRecipe> recipes();

    /** What a worker does at the block (shows in the census and trains the skill of it). */
    protected abstract WorkAction craftAction();

    /** The menu of the site. */
    public abstract WorksiteMenus.Kind menuKind();

    /** Whether the site burns fuel for its batches right now (the bakery: by config). */
    protected boolean needsFuel() {
        return fuelInventory != null;
    }

    /**
     * A condition of the world that the recipe needs and the site lacks (the tannery's water): the status key it is waiting for, or null if there is
     * none. A recipe with a missing condition is not made.
     */
    @Nullable
    protected String missingCondition(ProcessingRecipe recipe) {
        return null;
    }

    /**
     * What a batch takes out of the input store when it is made. The default is the batch's own list; the smithy lets a master smith save an ingot.
     * {@code actor} is the worker that completed the task (null for a machine).
     */
    protected List<ItemStack> consumedFor(ProcessingRecipe recipe, ProcessingRecipe.Batch batch, @Nullable LivingEntity actor) {
        return batch.consume();
    }

    /** A batch was made; a subclass plays its sound and shows its particles. */
    protected void onCrafted(ServerLevel level, ProcessingRecipe recipe) {
        level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5F, 1.0F);
    }

    // ---------------------------------------------------------------- settings

    private void ensureSettings() {
        int count = recipes().size();
        if (enabled.length != count) {
            enabled = new boolean[count];
            targets = new int[count];
            for (int i = 0; i < count; i++) {
                enabled[i] = recipes().get(i).defaultEnabled();
            }
        }
    }

    public final int recipeCount() {
        return recipes().size();
    }

    public final ProcessingRecipe recipe(int index) {
        return recipes().get(index);
    }

    public final boolean isRecipeEnabled(int index) {
        ensureSettings();
        return index >= 0 && index < enabled.length && enabled[index];
    }

    public final int getRecipeTarget(int index) {
        ensureSettings();
        return index >= 0 && index < targets.length ? targets[index] : 0;
    }

    public final void setRecipeEnabled(int index, boolean value) {
        ensureSettings();
        if (index >= 0 && index < enabled.length && enabled[index] != value) {
            enabled[index] = value;
            setChanged();
            inventoryChanged();
        }
    }

    public final void setRecipeTarget(int index, int value) {
        ensureSettings();
        int clamped = Mth.clamp(value, 0, MAX_TARGET);
        if (index >= 0 && index < targets.length && targets[index] != clamped) {
            targets[index] = clamped;
            setChanged();
            inventoryChanged();
        }
    }

    /** The product of the recipe in the main inventory and in the storages of the settlement (server only; the main inventory alone on the client). */
    public final int getRecipeStock(int index) {
        Predicate<ItemStack> product = recipes().get(index).product();
        int total = count(mainInventory, product);
        if (level instanceof ServerLevel serverLevel) {
            int recipes = recipes().size();
            long now = serverLevel.getGameTime();
            if (storedStock.length != recipes || now >= storedStockUntil || now < storedStockUntil - CACHE_TICKS) {
                storedStock = new int[recipes];
                java.util.Arrays.fill(storedStock, -1);
                storedStockUntil = now + CACHE_TICKS;
            }
            if (storedStock[index] < 0) {
                storedStock[index] = WorksiteStock.count(serverLevel, worldPosition, product);
            }
            total += storedStock[index];
        }
        return total;
    }

    private static int count(IItemHandler handler, Predicate<ItemStack> matcher) {
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && matcher.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private void inventoryChanged() {
        cached = null;
        invalidateTasks();
    }

    // ---------------------------------------------------------------- settlement demand

    /** What the settlement is short of that this recipe makes, as of the last second or so (server side; none on a client or without a settlement). */
    public final SettlementDemand.Demand demandOf(int index) {
        if (!(level instanceof ServerLevel serverLevel) || index < 0 || index >= recipes().size()) {
            return SettlementDemand.Demand.NONE;
        }
        long now = serverLevel.getGameTime();
        int count = recipes().size();
        if (demands.length != count || now >= demandsUntil) {
            SettlementDemand.Demand[] fresh = new SettlementDemand.Demand[count];
            for (int i = 0; i < count; i++) {
                fresh[i] = SettlementDemand.demandFor(serverLevel, worldPosition, recipes().get(i).icon());
            }
            demands = fresh;
            demandsUntil = now + CACHE_TICKS;
        }
        return demands[index];
    }

    /** Whether the recipe is in use: switched on, or needed by the settlement while {@code demand_enables_recipes} is true. */
    private boolean active(int index) {
        return enabled[index] || (AWAutomationStatics.demandEnablesRecipes && demandOf(index).needed());
    }

    // ---------------------------------------------------------------- inputs and fuel

    protected boolean isInput(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (ProcessingRecipe recipe : recipes()) {
            if (recipe.usesInput(stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFuel(ItemStack stack) {
        return stack.getBurnTime(RecipeType.SMELTING) > 0;
    }

    private int fuelAvailable() {
        if (fuelInventory == null) {
            return 0;
        }
        long total = fuelBurn;
        for (int slot = 0; slot < fuelInventory.getSlots(); slot++) {
            ItemStack stack = fuelInventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                total += (long) stack.getBurnTime(RecipeType.SMELTING) * stack.getCount();
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /** Burns fuel from the stock until the pool holds {@code cost}, then uses it up. Checked beforehand with {@link #fuelAvailable}. */
    private boolean burnFuel(int cost) {
        if (fuelInventory == null || fuelAvailable() < cost) {
            return false;
        }
        while (fuelBurn < cost) {
            int slot = -1;
            for (int i = 0; i < fuelInventory.getSlots(); i++) {
                ItemStack stack = fuelInventory.getStackInSlot(i);
                if (!stack.isEmpty() && isFuel(stack)) {
                    slot = i;
                    break;
                }
            }
            if (slot < 0) {
                return false;
            }
            ItemStack fuel = fuelInventory.getStackInSlot(slot);
            fuelBurn += fuel.getBurnTime(RecipeType.SMELTING);
            ItemStack remainder = fuel.getCraftingRemainingItem();
            fuelInventory.extractItem(slot, 1, false);
            if (!remainder.isEmpty() && level != null) {
                InventoryTools.insertOrDropItem(mainInventory, remainder, level, worldPosition);
            }
        }
        fuelBurn -= cost;
        return true;
    }

    // ---------------------------------------------------------------- choosing a batch

    private boolean fitsOutput(ProcessingRecipe.Batch batch) {
        for (ItemStack stack : batch.produce()) {
            if (!ItemHandlerHelper.insertItemStacked(mainInventory, stack.copy(), true).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private Eval eval(int index) {
        if (!active(index)) {
            return new Eval(State.DISABLED, null);
        }
        if (level == null) {
            return new Eval(State.NO_INPUT, null);
        }
        if (targets[index] > 0 && getRecipeStock(index) >= targets[index] && !demandOf(index).needed()) {
            return new Eval(State.SATISFIED, null);
        }
        ProcessingRecipe.Batch batch = recipes().get(index).plan(level, inputInventory);
        if (batch == null) {
            return new Eval(State.NO_INPUT, null);
        }
        if (!fitsOutput(batch)) {
            return new Eval(State.OUTPUT_FULL, batch);
        }
        if (needsFuel() && batch.fuel() > 0 && fuelAvailable() < batch.fuel()) {
            return new Eval(State.NO_FUEL, batch);
        }
        if (missingCondition(recipes().get(index)) != null) {
            return new Eval(State.BLOCKED, batch);
        }
        return new Eval(State.READY, batch);
    }

    /**
     * The batch to make next, or null if nothing can be made now: of the recipes that are ready, first one the settlement needs (the most urgent need,
     * then the most missing), else the one with a target that is furthest below it, else (recipes without a target) the next in turn after the last
     * one made.
     */
    @Nullable
    public final Selection select() {
        ensureSettings();
        int count = enabled.length;
        Selection needed = null;
        SettlementDemand.Demand neededDemand = SettlementDemand.Demand.NONE;
        Selection targeted = null;
        double targetedRatio = Double.MAX_VALUE;
        Selection free = null;
        for (int k = 0; k < count; k++) {
            int index = (rotation + k) % count;
            Eval eval = eval(index);
            if (eval.state() != State.READY || eval.batch() == null) {
                continue;
            }
            SettlementDemand.Demand demand = demandOf(index);
            if (demand.needed()) {
                if (needed == null || demand.priority() > neededDemand.priority()
                        || (demand.priority() == neededDemand.priority() && demand.count() > neededDemand.count())) {
                    needed = new Selection(index, eval.batch());
                    neededDemand = demand;
                }
                continue;
            }
            if (targets[index] > 0) {
                double ratio = (double) getRecipeStock(index) / targets[index];
                if (ratio < targetedRatio) {
                    targetedRatio = ratio;
                    targeted = new Selection(index, eval.batch());
                }
            } else if (free == null) {
                free = new Selection(index, eval.batch());
            }
        }
        return needed != null ? needed : targeted != null ? targeted : free;
    }

    @Nullable
    private Selection selectCached() {
        long now = level == null ? 0 : level.getGameTime();
        if (cached == null || now >= cachedUntil) {
            cached = select();
            cachedUntil = now + CACHE_TICKS;
        }
        return cached;
    }

    /**
     * The status key of the site: {@code working} (a batch can be made), {@code idle} (all targets are met), {@code storage_full}, {@code needs_fuel},
     * {@code needs_input}, {@code no_recipe} or what {@link #missingCondition} says. Server only.
     */
    public final String statusKey() {
        ensureSettings();
        boolean anyEnabled = false;
        boolean noInput = false;
        boolean noFuel = false;
        boolean full = false;
        String blockedKey = null;
        for (int i = 0; i < enabled.length; i++) {
            switch (eval(i).state()) {
                case READY -> {
                    return STATUS_WORKING;
                }
                case NO_INPUT -> {
                    anyEnabled = true;
                    noInput = true;
                }
                case NO_FUEL -> {
                    anyEnabled = true;
                    noFuel = true;
                }
                case OUTPUT_FULL -> {
                    anyEnabled = true;
                    full = true;
                }
                case BLOCKED -> {
                    anyEnabled = true;
                    blockedKey = missingCondition(recipes().get(i));
                }
                case SATISFIED -> anyEnabled = true;
                default -> {
                }
            }
        }
        if (!anyEnabled) {
            return NO_RECIPE;
        }
        if (full) {
            return STATUS_FULL;
        }
        if (noFuel) {
            return FUEL;
        }
        if (blockedKey != null) {
            return blockedKey;
        }
        return noInput ? INPUT : STATUS_IDLE;
    }

    /** What the look-at tooltip says the site waits for: no recipe, input, fuel or room; null when it works or has nothing to do. */
    @Nullable
    @Override
    public String getWaitingFor() {
        if (level == null || level.isClientSide) {
            return null;
        }
        String status = statusKey();
        return STATUS_WORKING.equals(status) || STATUS_IDLE.equals(status) ? null : status;
    }

    // ---------------------------------------------------------------- crafting

    /**
     * Makes the batch: takes its input out of the input store, burns its fuel and puts the products into the main inventory (what does not fit is dropped
     * at the block). Plans the recipe again first, as the stores may have changed since it was chosen.
     *
     * @return false if the batch can no longer be made
     */
    protected final boolean craft(Selection selection) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        ProcessingRecipe recipe = recipes().get(selection.index());
        ProcessingRecipe.Batch batch = recipe.plan(level, inputInventory);
        if (batch == null || !fitsOutput(batch)) {
            return false;
        }
        if (needsFuel() && batch.fuel() > 0 && !burnFuel(batch.fuel())) {
            return false;
        }
        List<ItemStack> consumed = consumedFor(recipe, batch, currentActor);
        for (ItemStack need : consumed) {
            if (countInput(need) < need.getCount()) {
                return false; // never make a product without its input
            }
        }
        for (ItemStack need : consumed) {
            takeInput(need);
        }
        for (ItemStack product : batch.produce()) {
            ItemStack left = ItemHandlerHelper.insertItemStacked(mainInventory, product.copy(), false);
            if (!left.isEmpty()) {
                InventoryTools.insertOrDropItem(mainInventory, left, level, worldPosition);
            }
            SettlementDemand.fulfilled(serverLevel, worldPosition, product);
        }
        demandsUntil = Long.MIN_VALUE;
        rotation = (selection.index() + 1) % Math.max(1, recipes().size());
        onCrafted(serverLevel, recipe);
        setChanged();
        inventoryChanged();
        return true;
    }

    /** Whether a stack of the input store is what the batch means: the same item and components, or (for an item that cannot be damaged) the same item. */
    private static boolean sameInput(ItemStack stack, ItemStack need) {
        return ItemStack.isSameItemSameComponents(stack, need) || (!need.isDamageableItem() && !stack.isDamaged() && ItemStack.isSameItem(stack, need));
    }

    private int countInput(ItemStack need) {
        int have = 0;
        for (int slot = 0; slot < inputInventory.getSlots(); slot++) {
            ItemStack stack = inputInventory.getStackInSlot(slot);
            if (!stack.isEmpty() && sameInput(stack, need)) {
                have += stack.getCount();
            }
        }
        return have;
    }

    private void takeInput(ItemStack need) {
        int remaining = need.getCount();
        for (int slot = 0; slot < inputInventory.getSlots() && remaining > 0; slot++) {
            ItemStack stack = inputInventory.getStackInSlot(slot);
            if (!stack.isEmpty() && sameInput(stack, need)) {
                remaining -= inputInventory.extractItem(slot, remaining, false).getCount();
            }
        }
    }

    // ---------------------------------------------------------------- worksite plumbing

    @Override
    public WorkType getWorkType() {
        return WorkType.CRAFTING;
    }

    @Override
    public boolean userAdjustableBlocks() {
        return false;
    }

    @Override
    public boolean onBlockClicked(Player player, @Nullable InteractionHand hand) {
        if (!player.level().isClientSide) {
            WorksiteMenus.open(player, menuKind(), this);
        }
        return true;
    }

    /** Opens the recipe screen. */
    @Override
    public void openAltGui(Player player) {
        WorksiteMenus.open(player, WorksiteMenus.Kind.PROCESSING_RECIPES, this);
    }

    @Override
    public void onBlockBroken(BlockState state) {
        super.onBlockBroken(state);
        if (level != null) {
            InventoryTools.dropItemsInWorld(level, inputInventory, worldPosition);
            if (fuelInventory != null) {
                InventoryTools.dropItemsInWorld(level, fuelInventory, worldPosition);
            }
        }
    }

    @Override
    protected boolean supportsStaffedWork() {
        return true;
    }

    /** The one task: the batch at the block itself. */
    @Override
    protected List<WorkTask> listTasks() {
        Selection selection = select();
        if (selection == null) {
            return List.of();
        }
        return List.of(new WorkTask(worldPosition, craftAction(), selection.batch().ticks()));
    }

    @Override
    protected boolean performTask(WorkTask task, IItemHandler drops) {
        Selection selection = select();
        return selection != null && craft(selection);
    }

    @Override
    protected Optional<IWorksiteAction> getNextAction() {
        return selectCached() == null ? Optional.empty() : Optional.of(CRAFT_ACTION);
    }

    @Override
    protected boolean processAction(IWorksiteAction action) {
        Selection selection = select();
        return selection != null && craft(selection);
    }

    @Override
    protected void updateWorksite() {
        // nothing to scan: the worker stands at the block
    }

    // ---------------------------------------------------------------- logistics

    /**
     * Couriers keep the raw inputs of the recipes that are in use stocked, and the fuel if the site burns any. An input of a recipe the settlement needs
     * (and the fuel, then) gets the priority of that need, so the couriers bring it first.
     */
    @Override
    public List<Need> getLogisticsNeeds() {
        ensureSettings();
        List<Need> needs = new ArrayList<>();
        Map<ProcessingRecipe.Input, Integer> priorities = new IdentityHashMap<>();
        int fuelPriority = 0;
        for (int i = 0; i < enabled.length; i++) {
            if (!active(i)) {
                continue;
            }
            SettlementDemand.Demand demand = demandOf(i);
            int priority = demand.needed() ? Math.max(1, demand.priority()) : 0;
            fuelPriority = Math.max(fuelPriority, priority);
            for (ProcessingRecipe.Input input : recipes().get(i).inputs()) {
                priorities.merge(input, priority, Math::max);
            }
        }
        for (Map.Entry<ProcessingRecipe.Input, Integer> entry : priorities.entrySet()) {
            ProcessingRecipe.Input input = entry.getKey();
            int target = input.target() > 0 ? input.target() : AWAutomationStatics.productionInputTarget;
            needs.add(new Need(inputInventory, input.matcher(), target, entry.getValue()));
        }
        if (fuelInventory != null && needsFuel()) {
            needs.add(new Need(fuelInventory, stack -> stack.is(Items.COAL) || stack.is(Items.CHARCOAL), FUEL_TARGET, fuelPriority));
        }
        return needs;
    }

    // ---------------------------------------------------------------- what the settlement may ask (IWorksiteProduction)

    @Override
    public boolean hasRecipeFor(Predicate<ItemStack> wanted) {
        for (ProcessingRecipe recipe : recipes()) {
            if (wanted.test(recipe.icon())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canMake(Predicate<ItemStack> wanted) {
        ensureSettings();
        for (int i = 0; i < enabled.length; i++) {
            if (wanted.test(recipes().get(i).icon()) && (enabled[i] || AWAutomationStatics.demandEnablesRecipes)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isMaking(Predicate<ItemStack> wanted) {
        Selection selection = selectCached();
        return selection != null && wanted.test(recipes().get(selection.index()).icon());
    }

    /** The raw inputs of the recipes in use that the input store holds none of, and the fuel when there is none and something to make. */
    @Override
    public List<StarvedInput> getStarvedInputs() {
        ensureSettings();
        List<StarvedInput> starved = new ArrayList<>();
        Set<ProcessingRecipe.Input> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean wanted = false;
        for (int i = 0; i < enabled.length; i++) {
            if (!active(i)) {
                continue;
            }
            Eval eval = eval(i);
            if (eval.state() == State.DISABLED || eval.state() == State.SATISFIED) {
                continue;
            }
            wanted = true;
            if (eval.state() != State.NO_INPUT) {
                continue;
            }
            for (ProcessingRecipe.Input input : recipes().get(i).inputs()) {
                if (!input.sample().isEmpty() && seen.add(input) && count(inputInventory, input.matcher()) == 0) {
                    int target = input.target() > 0 ? input.target() : AWAutomationStatics.productionInputTarget;
                    starved.add(new StarvedInput(input.sample(), input.matcher(), target));
                }
            }
        }
        if (wanted && fuelInventory != null && needsFuel() && fuelAvailable() == 0) {
            starved.add(new StarvedInput(new ItemStack(Items.COAL), stack -> stack.is(Items.COAL) || stack.is(Items.CHARCOAL), FUEL_TARGET));
        }
        return starved;
    }

    // ---------------------------------------------------------------- saving

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inputInventory.deserializeNBT(registries, tag.getCompound("inputInventory"));
        if (fuelInventory != null) {
            fuelInventory.deserializeNBT(registries, tag.getCompound("fuelInventory"));
        }
        fuelBurn = tag.getInt("fuelBurn");
        ensureSettings();
        ListTag list = tag.getList("recipes", Tag.TAG_COMPOUND);
        for (int n = 0; n < list.size(); n++) {
            CompoundTag entry = list.getCompound(n);
            String id = entry.getString("id");
            for (int i = 0; i < enabled.length; i++) {
                if (recipes().get(i).id().equals(id)) {
                    enabled[i] = entry.getBoolean("enabled");
                    targets[i] = Mth.clamp(entry.getInt("target"), 0, MAX_TARGET);
                }
            }
        }
        inventoryChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ensureSettings();
        tag.put("inputInventory", inputInventory.serializeNBT(registries));
        if (fuelInventory != null) {
            tag.put("fuelInventory", fuelInventory.serializeNBT(registries));
        }
        tag.putInt("fuelBurn", fuelBurn);
        ListTag list = new ListTag();
        for (int i = 0; i < enabled.length; i++) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", recipes().get(i).id());
            entry.putBoolean("enabled", enabled[i]);
            entry.putInt("target", targets[i]);
            list.add(entry);
        }
        tag.put("recipes", list);
    }
}
