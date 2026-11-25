/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 3): the smelter worksite. No 1.12.2 original.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import io.github.capttracker.ancientwarfare.api.WorkAction;
import io.github.capttracker.ancientwarfare.api.WorkTask;
import io.github.capttracker.ancientwarfare.block.BlockRotationHandler.RelativeSide;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The smelter (work type crafting). It works the furnaces, smokers and blast furnaces inside its work area (the bounds and the vertical range of the site)
 * and has a fire of its own.
 *
 * <p>Tasks: {@code SMELT} loads a linked furnace with ore from the input inventory (the recipe type of the furnace decides what it takes: smelting, smoking
 * or blasting) and with fuel from the fuel inventory when its fuel slot is empty; {@code COLLECT} takes the output of a linked furnace. The internal fire
 * takes the same two tasks at the smelter block itself: a worker loads up to {@value #FIRE_LOAD} items of smeltable input, the fire burns fuel from the fuel
 * inventory by itself ({@code SMELTING} recipes, one item per cooking time, fuel as in a furnace), and the worker collects the output.
 * Furnace experience is not collected.
 *
 * <p>Inventories: main (output), input ({@code inputInventory}) and fuel ({@code fuelInventory}). The site is an {@link io.github.capttracker.ancientwarfare.api.IWorksiteLogistics}
 * through its base: couriers take the main inventory and keep input and fuel stocked.
 */
public class SmelterBlockEntity extends InventoryWorksiteBlockEntity {
    private static final int INPUT_SIZE = 9;
    private static final int FUEL_SIZE = 3;
    private static final int FIRE_LOAD = 16;
    private static final int RESCAN_TICKS = 100;
    private static final int TASK_REFRESH_TICKS = 40;
    private static final int MAX_FURNACES = 64;
    private static final int LOAD_TICKS = 20;
    private static final int LOAD_MAX = 16;

    public final ItemStackHandler inputInventory;
    public final ItemStackHandler fuelInventory;

    private List<BlockPos> furnaces = List.of();
    private List<WorkTask> tasks = List.of();
    private int furnaceRescan = 0;
    private int taskRefresh = 0;
    private boolean tasksDirty = true;

    // the internal fire
    private ItemStack fireInput = ItemStack.EMPTY;
    private ItemStack fireOutput = ItemStack.EMPTY;
    private int fireBurn = 0;
    private int fireProgress = 0;
    private int fireCookTime = 0;

    private record Cooking(int cookTime, ItemStack result) {}

    public SmelterBlockEntity(BlockPos pos, BlockState state) {
        super(AwAutomationBase.SMELTER_BE.get(), pos, state);
        inputInventory = new ItemStackHandler(INPUT_SIZE) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                tasksDirty = true;
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return isSmeltable(stack) ? super.insertItem(slot, stack, simulate) : stack;
            }
        };
        fuelInventory = new ItemStackHandler(FUEL_SIZE) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                tasksDirty = true;
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return isFuel(stack) ? super.insertItem(slot, stack, simulate) : stack;
            }
        };
        setSideInventory(RelativeSide.FRONT, inputInventory, RelativeSide.FRONT);
        setSideInventory(RelativeSide.BOTTOM, fuelInventory, RelativeSide.TOP);
    }

    // ---------------------------------------------------------------- recipes

    private <T extends AbstractCookingRecipe> Cooking lookup(RecipeType<T> type, ItemStack stack) {
        if (level == null || stack.isEmpty()) {
            return null;
        }
        SingleRecipeInput input = new SingleRecipeInput(stack);
        Optional<RecipeHolder<T>> recipe = level.getRecipeManager().getRecipeFor(type, input, level);
        return recipe.map(holder -> new Cooking(holder.value().getCookingTime(), holder.value().assemble(input, level.registryAccess()))).orElse(null);
    }

    private static RecipeType<? extends AbstractCookingRecipe> typeOf(AbstractFurnaceBlockEntity furnace) {
        if (furnace instanceof BlastFurnaceBlockEntity) {
            return RecipeType.BLASTING;
        }
        return furnace instanceof SmokerBlockEntity ? RecipeType.SMOKING : RecipeType.SMELTING;
    }

    @Nullable
    private Cooking cookingFor(AbstractFurnaceBlockEntity furnace, ItemStack stack) {
        return lookup(typeOf(furnace), stack);
    }

    private boolean isSmeltable(ItemStack stack) {
        if (level == null) {
            return !stack.isEmpty(); // before the level is set (loading): accept, the stacks were checked when they went in
        }
        return lookup(RecipeType.SMELTING, stack) != null || lookup(RecipeType.BLASTING, stack) != null || lookup(RecipeType.SMOKING, stack) != null;
    }

    private static boolean isFuel(ItemStack stack) {
        return stack.getBurnTime(RecipeType.SMELTING) > 0;
    }

    @Override
    public List<Need> getLogisticsNeeds() {
        List<Need> needs = new ArrayList<>();
        needs.add(new Need(fuelInventory, stack -> stack.is(net.minecraft.world.item.Items.COAL) || stack.is(net.minecraft.world.item.Items.CHARCOAL), 16));
        needs.add(new Need(inputInventory, stack -> stack.is(Tags.Items.RAW_MATERIALS) || stack.is(Tags.Items.ORES), 64));
        return needs;
    }

    @Override
    public boolean userAdjustableBlocks() {
        return false;
    }

    @Override
    public boolean supportsVerticalRange() {
        return true;
    }

    @Override
    public WorkType getWorkType() {
        return WorkType.CRAFTING;
    }

    @Override
    public boolean onBlockClicked(Player player, @Nullable InteractionHand hand) {
        if (!player.level().isClientSide) {
            WorksiteMenus.open(player, WorksiteMenus.Kind.SMELTER, this);
        }
        return true;
    }

    @Override
    public void onBlockBroken(BlockState state) {
        super.onBlockBroken(state);
        if (level != null) {
            InventoryTools.dropItemsInWorld(level, inputInventory, worldPosition);
            InventoryTools.dropItemsInWorld(level, fuelInventory, worldPosition);
            InventoryTools.dropItemInWorld(level, fireInput, worldPosition);
            InventoryTools.dropItemInWorld(level, fireOutput, worldPosition);
        }
    }

    @Override
    public void onBoundsAdjusted() {
        super.onBoundsAdjusted();
        furnaceRescan = 0;
        tasksDirty = true;
    }

    // ---------------------------------------------------------------- tasks

    private void scanFurnaces() {
        List<BlockPos> found = new ArrayList<>();
        if (level != null && hasWorkBounds()) {
            BlockPos min = getWorkBoundsMin();
            BlockPos max = getWorkBoundsMax();
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int x = min.getX(); x <= max.getX() && found.size() < MAX_FURNACES; x++) {
                for (int z = min.getZ(); z <= max.getZ() && found.size() < MAX_FURNACES; z++) {
                    if (!level.isLoaded(cursor.set(x, getScanMinY(), z))) {
                        continue;
                    }
                    for (int y = getScanMinY(); y <= getScanMaxY(); y++) {
                        cursor.set(x, y, z);
                        if (level.getBlockState(cursor).getBlock() instanceof AbstractFurnaceBlock) {
                            found.add(cursor.immutable());
                        }
                    }
                }
            }
        }
        furnaces = found;
    }

    @Nullable
    private AbstractFurnaceBlockEntity furnaceAt(BlockPos pos) {
        return level != null && level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace ? furnace : null;
    }

    /** The input slot whose stack the furnace could take now, or -1. */
    private int loadableSlot(AbstractFurnaceBlockEntity furnace) {
        ItemStack in = furnace.getItem(0);
        if (!in.isEmpty() && in.getCount() >= in.getMaxStackSize()) {
            return -1;
        }
        for (int slot = 0; slot < inputInventory.getSlots(); slot++) {
            ItemStack stack = inputInventory.getStackInSlot(slot);
            if (stack.isEmpty() || cookingFor(furnace, stack) == null) {
                continue;
            }
            if (in.isEmpty() || ItemStack.isSameItemSameComponents(in, stack)) {
                return slot;
            }
        }
        return -1;
    }

    private int fuelSlot() {
        for (int slot = 0; slot < fuelInventory.getSlots(); slot++) {
            ItemStack stack = fuelInventory.getStackInSlot(slot);
            if (!stack.isEmpty() && isFuel(stack)) {
                return slot;
            }
        }
        return -1;
    }

    private boolean needsFuel(AbstractFurnaceBlockEntity furnace, boolean willHaveInput) {
        return willHaveInput && furnace.getItem(1).isEmpty() && fuelSlot() >= 0;
    }

    private void rebuildTasks() {
        tasksDirty = false;
        List<WorkTask> collect = new ArrayList<>();
        List<WorkTask> smelt = new ArrayList<>();
        for (BlockPos pos : furnaces) {
            AbstractFurnaceBlockEntity furnace = furnaceAt(pos);
            if (furnace == null) {
                continue;
            }
            if (!furnace.getItem(2).isEmpty()) {
                collect.add(new WorkTask(pos, WorkAction.COLLECT, LOAD_TICKS));
            }
            boolean canLoad = loadableSlot(furnace) >= 0;
            if (canLoad || needsFuel(furnace, !furnace.getItem(0).isEmpty())) {
                smelt.add(new WorkTask(pos, WorkAction.SMELT, LOAD_TICKS));
            }
        }
        if (!fireOutput.isEmpty()) {
            collect.add(new WorkTask(worldPosition, WorkAction.COLLECT, LOAD_TICKS));
        }
        if (canLoadFire() >= 0) {
            smelt.add(new WorkTask(worldPosition, WorkAction.SMELT, LOAD_TICKS));
        }
        List<WorkTask> list = new ArrayList<>(collect.size() + smelt.size());
        list.addAll(collect);
        list.addAll(smelt);
        tasks = list;
    }

    @Override
    protected boolean supportsStaffedWork() {
        return true;
    }

    @Override
    protected List<WorkTask> listTasks() {
        return tasks;
    }

    @Override
    protected boolean performTask(WorkTask task, IItemHandler drops) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        boolean done;
        if (task.pos().equals(worldPosition)) {
            done = task.action() == WorkAction.COLLECT ? collectFire(drops) : loadFire();
        } else {
            AbstractFurnaceBlockEntity furnace = furnaceAt(task.pos());
            if (furnace == null) {
                done = false;
            } else {
                done = task.action() == WorkAction.COLLECT ? collectFurnace(furnace, drops) : loadFurnace(furnace);
            }
        }
        if (done) {
            serverLevel.playSound(null, task.pos(), task.action() == WorkAction.COLLECT ? SoundEvents.ITEM_PICKUP : SoundEvents.FURNACE_FIRE_CRACKLE,
                    SoundSource.BLOCKS, 0.5F, 1.0F);
        }
        tasksDirty = true;
        return done;
    }

    private void give(IItemHandler drops, ItemStack stack) {
        ItemStack left = ItemHandlerHelper.insertItemStacked(drops, stack, false);
        if (!left.isEmpty()) {
            InventoryTools.insertOrDropItem(mainInventory, left, level, worldPosition);
        }
    }

    private boolean collectFurnace(AbstractFurnaceBlockEntity furnace, IItemHandler drops) {
        ItemStack out = furnace.getItem(2);
        if (out.isEmpty()) {
            return false;
        }
        furnace.setItem(2, ItemStack.EMPTY);
        furnace.setChanged();
        give(drops, out);
        return true;
    }

    private boolean loadFurnace(AbstractFurnaceBlockEntity furnace) {
        boolean moved = false;
        int slot = loadableSlot(furnace);
        if (slot >= 0) {
            ItemStack in = furnace.getItem(0);
            ItemStack source = inputInventory.getStackInSlot(slot);
            int room = (in.isEmpty() ? source.getMaxStackSize() : in.getMaxStackSize() - in.getCount());
            int count = Math.min(Math.min(room, LOAD_MAX), source.getCount());
            if (count > 0) {
                ItemStack taken = inputInventory.extractItem(slot, count, false);
                if (in.isEmpty()) {
                    furnace.setItem(0, taken);
                } else {
                    ItemStack grown = in.copy();
                    grown.grow(taken.getCount());
                    furnace.setItem(0, grown);
                }
                moved = true;
            }
        }
        if (needsFuel(furnace, !furnace.getItem(0).isEmpty())) {
            int fSlot = fuelSlot();
            ItemStack fuel = fuelInventory.getStackInSlot(fSlot);
            Cooking cooking = cookingFor(furnace, furnace.getItem(0));
            int burn = Math.max(1, fuel.getBurnTime(typeOf(furnace)));
            int cook = cooking == null ? 200 : cooking.cookTime();
            int wanted = Math.max(1, Math.min(8, (furnace.getItem(0).getCount() * cook + burn - 1) / burn));
            ItemStack taken = fuelInventory.extractItem(fSlot, wanted, false);
            if (!taken.isEmpty()) {
                furnace.setItem(1, taken);
                moved = true;
            }
        }
        if (moved) {
            furnace.setChanged();
        }
        return moved;
    }

    // ---------------------------------------------------------------- the internal fire

    /** The input slot the fire could be loaded from, or -1. */
    private int canLoadFire() {
        if (fireInput.getCount() >= FIRE_LOAD) {
            return -1;
        }
        for (int slot = 0; slot < inputInventory.getSlots(); slot++) {
            ItemStack stack = inputInventory.getStackInSlot(slot);
            if (stack.isEmpty() || lookup(RecipeType.SMELTING, stack) == null) {
                continue;
            }
            if (fireInput.isEmpty() || ItemStack.isSameItemSameComponents(fireInput, stack)) {
                return slot;
            }
        }
        return -1;
    }

    private boolean loadFire() {
        int slot = canLoadFire();
        if (slot < 0) {
            return false;
        }
        ItemStack source = inputInventory.getStackInSlot(slot);
        int count = Math.min(FIRE_LOAD - fireInput.getCount(), source.getCount());
        ItemStack taken = inputInventory.extractItem(slot, count, false);
        if (fireInput.isEmpty()) {
            fireInput = taken;
            fireCookTime = 0;
            fireProgress = 0;
        } else {
            fireInput.grow(taken.getCount());
        }
        setChanged();
        return true;
    }

    private boolean collectFire(IItemHandler drops) {
        if (fireOutput.isEmpty()) {
            return false;
        }
        ItemStack out = fireOutput;
        fireOutput = ItemStack.EMPTY;
        setChanged();
        give(drops, out);
        return true;
    }

    private void tickFire() {
        if (fireInput.isEmpty()) {
            fireProgress = 0;
            if (fireBurn > 0) {
                fireBurn--;
            }
            return;
        }
        Cooking cooking = lookup(RecipeType.SMELTING, fireInput);
        if (cooking == null) {
            // the recipe is gone (data pack reload): give the input back
            InventoryTools.insertOrDropItem(mainInventory, fireInput, level, worldPosition);
            fireInput = ItemStack.EMPTY;
            return;
        }
        fireCookTime = cooking.cookTime();
        boolean outputFree = fireOutput.isEmpty()
                || (ItemStack.isSameItemSameComponents(fireOutput, cooking.result()) && fireOutput.getCount() + cooking.result().getCount() <= fireOutput.getMaxStackSize());
        if (fireBurn <= 0 && outputFree) {
            int slot = fuelSlot();
            if (slot >= 0) {
                ItemStack fuel = fuelInventory.getStackInSlot(slot);
                fireBurn = fuel.getBurnTime(RecipeType.SMELTING);
                ItemStack remainder = fuel.getCraftingRemainingItem();
                fuelInventory.extractItem(slot, 1, false);
                if (!remainder.isEmpty()) {
                    InventoryTools.insertOrDropItem(mainInventory, remainder, level, worldPosition);
                }
                setChanged();
            }
        }
        if (fireBurn > 0) {
            fireBurn--;
            if (outputFree) {
                fireProgress++;
                if (level.getGameTime() % 10 == 0 && level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.SMOKE, worldPosition.getX() + 0.5D, worldPosition.getY() + 1.0D, worldPosition.getZ() + 0.5D, 1, 0.2D, 0.0D, 0.2D, 0.01D);
                }
                if (fireProgress >= fireCookTime) {
                    fireProgress = 0;
                    fireInput.shrink(1);
                    if (fireOutput.isEmpty()) {
                        fireOutput = cooking.result().copy();
                    } else {
                        fireOutput.grow(cooking.result().getCount());
                    }
                    if (fireInput.isEmpty()) {
                        fireInput = ItemStack.EMPTY;
                    }
                    tasksDirty = true;
                    setChanged();
                }
            }
        } else if (fireProgress > 0) {
            fireProgress = Math.max(0, fireProgress - 2);
        }
    }

    // ---------------------------------------------------------------- machine mode

    private static final IWorksiteAction SMELT_ACTION = WorksiteActions.full();

    @Override
    protected Optional<IWorksiteAction> getNextAction() {
        return tasks.isEmpty() ? Optional.empty() : Optional.of(SMELT_ACTION);
    }

    @Override
    protected boolean processAction(IWorksiteAction action) {
        return !tasks.isEmpty() && performTask(tasks.get(0), mainInventory);
    }

    @Override
    protected void updateWorksite() {
        if (furnaceRescan-- <= 0) {
            furnaceRescan = RESCAN_TICKS;
            scanFurnaces();
            tasksDirty = true;
        }
        if (tasksDirty || taskRefresh-- <= 0) {
            taskRefresh = TASK_REFRESH_TICKS;
            rebuildTasks();
        }
        tickFire();
    }

    // ---------------------------------------------------------------- saving

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inputInventory.deserializeNBT(registries, tag.getCompound("inputInventory"));
        fuelInventory.deserializeNBT(registries, tag.getCompound("fuelInventory"));
        fireInput = tag.contains("fireInput") ? ItemStack.parseOptional(registries, tag.getCompound("fireInput")) : ItemStack.EMPTY;
        fireOutput = tag.contains("fireOutput") ? ItemStack.parseOptional(registries, tag.getCompound("fireOutput")) : ItemStack.EMPTY;
        fireBurn = tag.getInt("fireBurn");
        fireProgress = tag.getInt("fireProgress");
        tasksDirty = true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inputInventory", inputInventory.serializeNBT(registries));
        tag.put("fuelInventory", fuelInventory.serializeNBT(registries));
        if (!fireInput.isEmpty()) {
            tag.put("fireInput", fireInput.save(registries));
        }
        if (!fireOutput.isEmpty()) {
            tag.put("fireOutput", fireOutput.save(registries));
        }
        tag.putInt("fireBurn", fireBurn);
        tag.putInt("fireProgress", fireProgress);
    }
}
