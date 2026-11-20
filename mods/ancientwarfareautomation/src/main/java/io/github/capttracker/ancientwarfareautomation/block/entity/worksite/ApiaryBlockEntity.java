/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 3): the apiary worksite. No 1.12.2 original.
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
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The apiary (work type farming). It manages the beehives and bee nests inside its work area (the bounds and the vertical range of the site).
 *
 * <p>Tasks, in priority order: {@code COLLECT} a honey bottle from a full hive (a glass bottle from the tool inventory), {@code SHEAR} three honeycombs from a
 * full hive (shears from the tool inventory, which lose durability), and {@code PLANT} a flower from the flower inventory where there are fewer than
 * {@value #MIN_FLOWERS} small flowers inside the area. A hive with bees inside is only harvested while the bees are calm: a lit campfire (or soul campfire)
 * under the hive within 5 blocks, as vanilla counts it, or a vanilla Smoker block in the tool inventory (a hand-held smoker that is not used up). A hive
 * without bees needs no smoke. The honey level of a harvested hive is reset as in vanilla and the bees stay in.
 *
 * <p>Inventories: main (products), the tools ({@code toolInventory}: glass bottles, shears, a smoker) and the flowers ({@code flowerInventory}).
 */
public class ApiaryBlockEntity extends InventoryWorksiteBlockEntity {
    /** The apiary plants flowers while there are fewer than this many small flowers in its area. */
    public static final int MIN_FLOWERS = 4;
    private static final int TOOL_INVENTORY_SIZE = 3;
    private static final int FLOWER_INVENTORY_SIZE = 6;
    private static final int RESCAN_TICKS = 100;
    private static final int HARVEST_TICKS = 30;
    private static final int MAX_LISTED = 64;
    private static final int PLANT_SEARCH = 96;

    public final ItemStackHandler toolInventory;
    public final ItemStackHandler flowerInventory;

    private List<WorkTask> tasks = List.of();
    private final List<BlockPos> plantSpots = new ArrayList<>();
    private int rescanDelay = 0;
    private boolean tasksDirty = true;
    /** Alternates between bottle and shears when a hive could take both (not saved). */
    private boolean preferBottle = true;

    public ApiaryBlockEntity(BlockPos pos, BlockState state) {
        super(AwAutomationBase.APIARY_BE.get(), pos, state);
        toolInventory = new ItemStackHandler(TOOL_INVENTORY_SIZE) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                tasksDirty = true;
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return isTool(stack) ? super.insertItem(slot, stack, simulate) : stack;
            }
        };
        flowerInventory = new ItemStackHandler(FLOWER_INVENTORY_SIZE) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                tasksDirty = true;
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return isFlower(stack) ? super.insertItem(slot, stack, simulate) : stack;
            }
        };
        setSideInventory(RelativeSide.FRONT, flowerInventory, RelativeSide.FRONT);
        setSideInventory(RelativeSide.BOTTOM, toolInventory, RelativeSide.TOP);
    }

    // ---------------------------------------------------------------- items

    private static boolean isBottle(ItemStack stack) {
        return stack.is(Items.GLASS_BOTTLE);
    }

    private static boolean isShears(ItemStack stack) {
        return stack.canPerformAction(ItemAbilities.SHEARS_HARVEST);
    }

    private static boolean isSmoker(ItemStack stack) {
        return stack.is(Items.SMOKER);
    }

    private static boolean isTool(ItemStack stack) {
        return isBottle(stack) || isShears(stack) || isSmoker(stack);
    }

    private static boolean isFlower(ItemStack stack) {
        return stack.is(ItemTags.SMALL_FLOWERS) && stack.getItem() instanceof BlockItem;
    }

    private int bottleSlot() {
        return InventoryTools.findItemSlot(toolInventory, ApiaryBlockEntity::isBottle);
    }

    private int shearsSlot() {
        return InventoryTools.findItemSlot(toolInventory, ApiaryBlockEntity::isShears);
    }

    private boolean hasSmoker() {
        return InventoryTools.findItemSlot(toolInventory, ApiaryBlockEntity::isSmoker) >= 0;
    }

    @Override
    public List<Need> getLogisticsNeeds() {
        List<Need> needs = new ArrayList<>();
        needs.add(new Need(toolInventory, ApiaryBlockEntity::isBottle, 8));
        needs.add(new Need(toolInventory, ApiaryBlockEntity::isShears, 1));
        needs.add(new Need(flowerInventory, ApiaryBlockEntity::isFlower, 8));
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
        return WorkType.FARMING;
    }

    @Override
    public boolean onBlockClicked(Player player, @Nullable InteractionHand hand) {
        if (!player.level().isClientSide) {
            WorksiteMenus.open(player, WorksiteMenus.Kind.APIARY, this);
        }
        return true;
    }

    @Override
    public void onBlockBroken(BlockState state) {
        super.onBlockBroken(state);
        if (level != null) {
            InventoryTools.dropItemsInWorld(level, toolInventory, worldPosition);
            InventoryTools.dropItemsInWorld(level, flowerInventory, worldPosition);
        }
    }

    @Override
    public void onBoundsAdjusted() {
        super.onBoundsAdjusted();
        tasksDirty = true;
        plantSpots.clear();
    }

    // ---------------------------------------------------------------- scanning

    private boolean isCalm(BlockPos hive) {
        return level != null && (CampfireBlock.isSmokeyPos(level, hive) || hasSmoker());
    }

    private boolean hasBees(BlockPos hive) {
        return level.getBlockEntity(hive) instanceof BeehiveBlockEntity be && !be.isEmpty();
    }

    /** Honey level 5 (full) and a calm hive (or one without bees). */
    private boolean isReady(BlockPos pos, BlockState state) {
        if (!state.is(BlockTags.BEEHIVES) || !state.hasProperty(BeehiveBlock.HONEY_LEVEL)
                || state.getValue(BeehiveBlock.HONEY_LEVEL) < BeehiveBlock.MAX_HONEY_LEVELS) {
            return false;
        }
        return !hasBees(pos) || isCalm(pos);
    }

    private void rebuildTasks() {
        tasksDirty = false;
        if (level == null || !hasWorkBounds()) {
            tasks = List.of();
            return;
        }
        BlockPos min = getWorkBoundsMin();
        BlockPos max = getWorkBoundsMax();
        int minY = getScanMinY();
        int maxY = getScanMaxY();
        boolean bottles = bottleSlot() >= 0;
        boolean shears = shearsSlot() >= 0;
        List<WorkTask> collect = new ArrayList<>();
        List<WorkTask> shear = new ArrayList<>();
        int flowers = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                if (!level.isLoaded(cursor.set(x, minY, z))) {
                    continue; // never load a chunk for a scan
                }
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) {
                        continue;
                    }
                    if (state.is(BlockTags.SMALL_FLOWERS)) {
                        flowers++;
                    } else if ((bottles || shears) && collect.size() + shear.size() < MAX_LISTED && isReady(cursor, state)) {
                        BlockPos pos = cursor.immutable();
                        boolean useBottle = bottles && (!shears || preferBottle);
                        if (useBottle) {
                            collect.add(new WorkTask(pos, WorkAction.COLLECT, HARVEST_TICKS));
                        } else {
                            shear.add(new WorkTask(pos, WorkAction.SHEAR, HARVEST_TICKS));
                        }
                    }
                }
            }
        }
        List<WorkTask> list = new ArrayList<>(collect.size() + shear.size() + MIN_FLOWERS);
        list.addAll(collect);
        list.addAll(shear);
        // flowers: keep the spots that are still good (a worker may hold one), add new ones until there are enough
        plantSpots.removeIf(spot -> findFlowerSlot(spot) < 0);
        int wanted = MIN_FLOWERS - flowers;
        if (wanted > 0 && hasAnyFlower()) {
            if (plantSpots.size() < wanted) {
                findPlantSpots(wanted - plantSpots.size(), min, max, minY, maxY);
            }
            for (int i = 0; i < plantSpots.size() && i < wanted; i++) {
                list.add(new WorkTask(plantSpots.get(i), WorkAction.PLANT, WorkTask.SOFT_TICKS));
            }
        } else {
            plantSpots.clear();
        }
        tasks = list;
    }

    private boolean hasAnyFlower() {
        return InventoryTools.findItemSlot(flowerInventory, ApiaryBlockEntity::isFlower) >= 0;
    }

    /** The slot of a flower that can stand at the spot (air, a dirt-like block below, the flower survives there), or -1. */
    private int findFlowerSlot(BlockPos spot) {
        if (!level.isEmptyBlock(spot) || !level.getBlockState(spot.below()).is(BlockTags.DIRT)) {
            return -1;
        }
        for (int slot = 0; slot < flowerInventory.getSlots(); slot++) {
            ItemStack stack = flowerInventory.getStackInSlot(slot);
            if (!stack.isEmpty() && isFlower(stack)) {
                Block block = ((BlockItem) stack.getItem()).getBlock();
                if (block.defaultBlockState().canSurvive(level, spot)) {
                    return slot;
                }
            }
        }
        return -1;
    }

    private void findPlantSpots(int count, BlockPos min, BlockPos max, int minY, int maxY) {
        int width = max.getX() - min.getX() + 1;
        int depth = max.getZ() - min.getZ() + 1;
        int height = maxY - minY + 1;
        long cells = (long) width * depth * height;
        if (cells <= 0) {
            return;
        }
        // a random walk over the area, so the flowers do not always grow in the same corner
        int offset = level.random.nextInt((int) Math.min(cells, Integer.MAX_VALUE));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int found = 0;
        for (int i = 0; i < Math.min(cells, PLANT_SEARCH) && found < count; i++) {
            int index = (int) ((offset + (long) i * 7) % cells);
            int y = minY + index % height;
            int z = min.getZ() + (index / height) % depth;
            int x = min.getX() + index / (height * depth);
            cursor.set(x, y, z);
            if (findFlowerSlot(cursor) >= 0 && !plantSpots.contains(cursor)) {
                plantSpots.add(cursor.immutable());
                found++;
            }
        }
    }

    // ---------------------------------------------------------------- staffed work

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
        boolean done = switch (task.action()) {
            case COLLECT -> harvest(serverLevel, task.pos(), drops, false);
            case SHEAR -> harvest(serverLevel, task.pos(), drops, true);
            case PLANT -> plant(serverLevel, task.pos());
            default -> false;
        };
        tasksDirty = true;
        return done;
    }

    private boolean harvest(ServerLevel serverLevel, BlockPos pos, IItemHandler drops, boolean withShears) {
        BlockState state = serverLevel.getBlockState(pos);
        if (!isReady(pos, state) || !(state.getBlock() instanceof BeehiveBlock hive)) {
            return false;
        }
        ItemStack result;
        if (withShears) {
            int slot = shearsSlot();
            if (slot < 0) {
                return false;
            }
            ItemStack shears = toolInventory.getStackInSlot(slot).copy();
            shears.hurtAndBreak(1, serverLevel, null, item -> {
            });
            toolInventory.setStackInSlot(slot, shears.isEmpty() || shears.getCount() <= 0 ? ItemStack.EMPTY : shears);
            result = new ItemStack(Items.HONEYCOMB, 3);
            serverLevel.playSound(null, pos, SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
        } else {
            int slot = bottleSlot();
            if (slot < 0) {
                return false;
            }
            toolInventory.extractItem(slot, 1, false);
            result = new ItemStack(Items.HONEY_BOTTLE);
            serverLevel.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        if (hasBees(pos) || hasSmoker()) {
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5D, pos.getY() + 0.9D, pos.getZ() + 0.5D, 4, 0.2D, 0.1D, 0.2D, 0.01D);
        }
        hive.resetHoneyLevel(serverLevel, state, pos);
        preferBottle = !preferBottle;
        ItemStack left = ItemHandlerHelper.insertItemStacked(drops, result, false);
        if (!left.isEmpty()) {
            InventoryTools.insertOrDropItem(mainInventory, left, level, worldPosition);
        }
        return true;
    }

    private boolean plant(ServerLevel serverLevel, BlockPos pos) {
        int slot = findFlowerSlot(pos);
        if (slot < 0) {
            return false;
        }
        ItemStack stack = flowerInventory.getStackInSlot(slot);
        Block block = ((BlockItem) stack.getItem()).getBlock();
        serverLevel.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
        flowerInventory.extractItem(slot, 1, false);
        plantSpots.remove(pos);
        serverLevel.playSound(null, pos, block.defaultBlockState().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.6F, 1.0F);
        return true;
    }

    // ---------------------------------------------------------------- machine mode (machinesWorkAlone or no worker)

    private static final IWorksiteAction TEND_ACTION = WorksiteActions.full();

    @Override
    protected Optional<IWorksiteAction> getNextAction() {
        return tasks.isEmpty() ? Optional.empty() : Optional.of(TEND_ACTION);
    }

    @Override
    protected boolean processAction(IWorksiteAction action) {
        if (tasks.isEmpty()) {
            return false;
        }
        return performTask(tasks.get(0), mainInventory);
    }

    @Override
    protected void updateWorksite() {
        if (rescanDelay-- <= 0 || tasksDirty) {
            if (rescanDelay <= 0) {
                rescanDelay = RESCAN_TICKS;
            }
            rebuildTasks();
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        toolInventory.deserializeNBT(registries, tag.getCompound("toolInventory"));
        flowerInventory.deserializeNBT(registries, tag.getCompound("flowerInventory"));
        tasksDirty = true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("toolInventory", toolInventory.serializeNBT(registries));
        tag.put("flowerInventory", flowerInventory.serializeNBT(registries));
    }
}
