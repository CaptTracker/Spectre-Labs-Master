/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/TileWorksiteFarm.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import io.github.capttracker.ancientwarfare.api.IWorksiteStatus;
import io.github.capttracker.ancientwarfare.block.BlockRotationHandler.RelativeSide;
import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfare.util.EntityTools;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

/**
 * Base of the crop, fruit and tree farms: a work area of at most 16 x 16 columns and a vertical range around the site (rework phase 3:
 * 4 blocks above and below by default, set in the bounds screen; each column is scanned at its surface, see {@link #findSurface}) with
 * a target map of the columns to work on, an incremental scan queue, a plantable inventory (3 slots,
 * front side) and a misc inventory (bone meal, for the tree farm also shears; 3 slots, top side), plus picking up
 * the items lying in the area and the drop handling for harvesting.
 *
 * <p>Each tick one position of the queue is scanned ({@link #scanBlockPosition}); when the queue is empty it is
 * refilled with every target column. The target map is a 16 x 16 byte array indexed {@code (z - minZ) * 16 + (x - minX)},
 * 1 = work this column, which is why the maximum width is 16. It is all 1 when the bounds are first set, saved as
 * {@code targetMap}, and edited by the target selection screen ({@link #setTargetBlocks}).
 *
 * <p>Changes from 1.12.2: {@code IPlantable} is gone from NeoForge 1.21, the default {@link #isFarmable} test looks at
 * the block classes instead; bone meal is {@code Items.BONE_MEAL}; items are picked up every 20 game ticks (the
 * original used the day time, which stops when the daylight cycle is off); the resource counts are refreshed after
 * bone meal is used (the original only did that when a handler reported a change).
 */
public abstract class FarmWorksiteBlockEntity extends InventoryWorksiteBlockEntity implements IWorksiteStatus {
    private static final int PLANTABLE_INVENTORY_SIZE = 3;
    private static final int SIZE = 16;
    private static final int LOGISTICS_PLANTABLE_TARGET = 32;
    private static final int LOGISTICS_BONEMEAL_TARGET = 16;
    public static final int DEFAULT_MISC_INVENTORY_SIZE = 3;
    private byte[] targetMap = new byte[SIZE * SIZE];
    private final Queue<BlockPos> blocksToUpdate = new LinkedList<>();
    public final ItemStackHandler plantableInventory;
    public final ItemStackHandler miscInventory;
    private final IItemHandler depositInventory;
    protected int plantableCount;
    protected int bonemealCount;

    /*
     * flag should be set to true whenever updating inventory internally (e.g. harvesting blocks) to prevent
     * unnecessary inventory rescanning.  should be set back to false after blocks are added to inventory
     */
    private boolean shouldCountResources;

    protected FarmWorksiteBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.shouldCountResources = true;
        plantableInventory = new ItemStackHandler(PLANTABLE_INVENTORY_SIZE) {
            @Override
            protected void onContentsChanged(int slot) {
                shouldCountResources = true;
                setChanged();
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return isPlantable(stack) ? super.insertItem(slot, stack, simulate) : stack;
            }
        };
        miscInventory = new ItemStackHandler(getMiscInventorySize()) {
            @Override
            protected void onContentsChanged(int slot) {
                shouldCountResources = true;
                setChanged();
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return isMiscItem(stack) ? super.insertItem(slot, stack, simulate) : stack;
            }
        };
        depositInventory = new CombinedInvWrapper(plantableInventory, mainInventory);
        setSideInventory(RelativeSide.FRONT, plantableInventory, RelativeSide.FRONT);
        setSideInventory(RelativeSide.BOTTOM, miscInventory, RelativeSide.TOP);
    }

    /** Seeds or saplings and bone meal are kept stocked; the main inventory is emptied except for such items that the inputs still want. */
    @Override
    public List<Output> getLogisticsOutputs() {
        Need plantable = plantableNeed();
        Need bonemeal = bonemealNeed();
        return List.of(new Output(mainInventory, stack -> {
            if (isPlantable(stack)) {
                return plantable.deficit() == 0;
            }
            return !isBonemeal(stack) || bonemeal.deficit() == 0;
        }));
    }

    @Override
    public List<Need> getLogisticsNeeds() {
        return List.of(plantableNeed(), bonemealNeed());
    }

    private Need plantableNeed() {
        return new Need(plantableInventory, this::isPlantable, LOGISTICS_PLANTABLE_TARGET);
    }

    private Need bonemealNeed() {
        return new Need(miscInventory, this::isBonemeal, LOGISTICS_BONEMEAL_TARGET);
    }

    /** Where drops go: the plantable inventory first (seeds, saplings), then the main inventory. A worker empties its carry pouch into this. */
    @Override
    public IItemHandler getDepositInventory() {
        return depositInventory;
    }

    /** Third round (Serene Seasons): the farm has seeds but may sow none of them in this season. Farms that plant override it. */
    protected boolean isOutOfSeason() {
        return false;
    }

    @Override
    public String getWaitingFor() {
        return isOutOfSeason() ? OUT_OF_SEASON : null;
    }

    protected boolean isMiscItem(ItemStack stack) {
        return isBonemeal(stack);
    }

    protected int getMiscInventorySize() {
        return DEFAULT_MISC_INVENTORY_SIZE;
    }

    protected abstract boolean isPlantable(ItemStack stack);

    protected abstract void scanBlockPosition(BlockPos pos);

    @Override
    protected final void updateWorksite() {
        level.getProfiler().push("Incremental Scan");
        if (blocksToUpdate.isEmpty() && hasWorkBounds()) {
            fillBlocksToProcess(blocksToUpdate);
        }
        if (!blocksToUpdate.isEmpty()) {
            BlockPos pos = blocksToUpdate.poll();
            scanBlockPosition(pos);
        }
        level.getProfiler().pop();
        updateBlockWorksite();
    }

    @Override
    public void onBlockBroken(BlockState state) {
        super.onBlockBroken(state);
        if (level != null) {
            InventoryTools.dropItemsInWorld(level, plantableInventory, worldPosition);
            InventoryTools.dropItemsInWorld(level, miscInventory, worldPosition);
        }
    }

    /**
     * Breaks the block without drops (one break event, no loot spawned) and puts what it would have dropped, with
     * this worksite's fortune, into the plantable inventory first and then the main inventory. Nothing happens if the
     * drops do not fit.
     *
     * @return true if the block was broken
     */
    protected boolean harvestBlock(BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }

        List<ItemStack> stacks = WorksiteTools.getDrops(serverLevel, pos, state, getFortune());

        if (!inventoryCanHold(stacks)) {
            return false;
        }

        if (!BlockTools.breakBlockNoDrops(level, pos, state)) {
            return false;
        }

        insertOrDropCrops(pos, stacks);
        return true;
    }

    private boolean inventoryCanHold(List<ItemStack> stacks) {
        List<ItemStack> remainingStacks = InventoryTools.insertItems(plantableInventory, stacks, true);
        remainingStacks = InventoryTools.insertItems(mainInventory, remainingStacks, true);
        return remainingStacks.isEmpty();
    }

    private void insertOrDropCrops(BlockPos pos, List<ItemStack> stacks) {
        List<ItemStack> remainingItems = InventoryTools.insertItems(plantableInventory, stacks, false);
        InventoryTools.insertOrDropItems(mainInventory, remainingItems, level, pos);
    }

    @Override
    public final boolean userAdjustableBlocks() {
        return true;
    }

    /** True if the column of the position is a target (the work area bounds are not checked). */
    protected boolean isTarget(BlockPos p) {
        return isTarget(p.getX(), p.getZ());
    }

    private boolean isTarget(int x1, int y1) {
        int z = (y1 - getWorkBoundsMin().getZ()) * SIZE + x1 - getWorkBoundsMin().getX();
        return z >= 0 && z < targetMap.length && targetMap[z] == 1;
    }

    protected boolean isBonemeal(ItemStack stack) {
        return WorksiteTools.isBonemeal(stack);
    }

    /** Whether the block is something the farm grows; subclasses refine it. */
    protected boolean isFarmable(Block block) {
        return isFarmable(block, BlockPos.ZERO);
    }

    /**
     * Default plant test (1.12.2: {@code block instanceof IPlantable}): bushes (crops, saplings, flowers, mushrooms,
     * nether wart, stems...), sugar cane and cactus.
     */
    protected boolean isFarmable(Block block, BlockPos farmablePos) {
        return block instanceof BushBlock || block instanceof SugarCaneBlock || block instanceof CactusBlock;
    }

    protected boolean canReplace(BlockPos pos) {
        return WorksiteTools.canReplace(level, pos);
    }

    protected boolean tryPlace(ItemStack stack, BlockPos pos, Direction face) {
        return BlockTools.placeItemBlock(stack, level, pos, face);
    }

    private void pickupItems() {
        BlockPos min = getWorkBoundsMin();
        BlockPos max = getWorkBoundsMax();
        List<ItemEntity> items = EntityTools.getEntitiesWithinBounds(level, ItemEntity.class,
                new BlockPos(min.getX(), getScanMinY(), min.getZ()), new BlockPos(max.getX(), getScanMaxY() + 2, max.getZ()));
        if (items.isEmpty()) {
            return;
        }
        ItemStack stack;
        for (ItemEntity item : items) {
            if (item.isAlive()) {
                stack = item.getItem();
                if (!stack.isEmpty()) {
                    stack = InventoryTools.mergeItemStack(plantableInventory, stack);
                    stack = InventoryTools.mergeItemStack(mainInventory, stack);
                    if (!stack.isEmpty()) {
                        item.setItem(stack);
                    } else {
                        item.discard();
                    }
                }
            }
        }
    }

    /** Removes the positions that are outside the bounds (horizontally and in height) or not targets. */
    @Override
    protected void validateCollection(Collection<BlockPos> blocks) {
        validateCollection(blocks, true);
    }

    /** As {@link #validateCollection(Collection)}; {@code checkHeight} false leaves the height alone (the blocks of a tree reach above the vertical range). */
    protected void validateCollection(Collection<BlockPos> blocks, boolean checkHeight) {
        if (!hasWorkBounds()) {
            blocks.clear();
            return;
        }
        Iterator<BlockPos> it = blocks.iterator();
        BlockPos pos;
        while (it.hasNext() && (pos = it.next()) != null) {
            if (!isInBounds(pos) || !isTarget(pos) || (checkHeight && !isInHeightRange(pos))) {
                it.remove();
            }
        }
    }

    /** True if the position is not too far above or below the vertical range (a margin of one below and eight above for what grows on the surface). */
    protected final boolean isInHeightRange(BlockPos pos) {
        return pos.getY() >= getScanMinY() - 1 && pos.getY() <= getScanMaxY() + 8;
    }

    // ---- the surface scan ----

    /**
     * Whether the block can be the ground of this farm: the farmable surface (soil the crop farm plants on, the tree farm's soil blocks...).
     * The default is any solid block that is not leaves or a log.
     */
    protected boolean isGround(BlockState state, BlockPos pos) {
        return state.blocksMotion() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS);
    }

    /** Whether the block above the ground leaves it usable: air, vegetation to be replaced or a plant of the farm. */
    protected boolean acceptsAboveGround(BlockState above, BlockPos abovePos) {
        return above.isAir() || above.canBeReplaced() || !above.blocksMotion() || isFarmable(above.getBlock(), abovePos);
    }

    /**
     * The scan position of a column (rework phase 3, natural terrain): the position right above the topmost block of the vertical range that
     * {@link #isGround} accepts and that has {@link #acceptsAboveGround usable space} above it. Slopes and steps give each column its own
     * height. A column whose first block from the top that is not air is a fluid has no surface (the farms do not work water); null if there is none.
     */
    @Nullable
    protected BlockPos findSurface(int x, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        if (!level.isLoaded(cursor.set(x, getScanMinY(), z))) {
            return null; // a column in a chunk that is not loaded is left for a later scan (never load a chunk for it)
        }
        for (int y = getScanMaxY(); y >= getScanMinY(); y--) {
            cursor.set(x, y, z);
            BlockState state = level.getBlockState(cursor);
            if (!state.getFluidState().isEmpty()) {
                return null;
            }
            if (isGround(state, cursor)) {
                BlockPos above = cursor.above();
                if (acceptsAboveGround(level.getBlockState(above), above)) {
                    return above;
                }
            }
        }
        return null;
    }

    /** The position the incremental scan looks at for a target column: the surface by default. The fruit farm scans the whole column instead. */
    @Nullable
    protected BlockPos columnScanPos(int x, int z) {
        return findSurface(x, z);
    }

    @Override
    public boolean supportsVerticalRange() {
        return true;
    }

    private void fillBlocksToProcess(Collection<BlockPos> targets) {
        BlockPos min = getWorkBoundsMin();
        BlockPos max = getWorkBoundsMax();
        for (int x = min.getX(); x < max.getX() + 1; x++) {
            for (int z = min.getZ(); z < max.getZ() + 1; z++) {
                if (isTarget(x, z) && level.isLoaded(new BlockPos(x, getScanMinY(), z))) {
                    BlockPos scanPos = columnScanPos(x, z);
                    if (scanPos != null) {
                        targets.add(scanPos);
                    }
                }
            }
        }
    }

    //TODO implement to check target blocks, clear invalid ones
    public void onTargetsAdjusted() {
        onBoundsAdjusted();
    }

    @Override
    protected void onBoundsSet() {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                targetMap[z * SIZE + x] = (byte) 1;
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putByteArray("targetMap", targetMap);
        tag.put("plantableInventory", plantableInventory.serializeNBT(registries));
        tag.put("miscInventory", miscInventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("targetMap")) {
            byte[] loaded = tag.getByteArray("targetMap");
            if (loaded.length == SIZE * SIZE) {
                targetMap = loaded;
            }
        }
        plantableInventory.deserializeNBT(registries, tag.getCompound("plantableInventory"));
        miscInventory.deserializeNBT(registries, tag.getCompound("miscInventory"));
    }

    /** The 16 x 16 target map (live array). */
    public byte[] getTargetMap() {
        return targetMap;
    }

    /** Replaces the target map (from the target selection screen) and revalidates the queues if it changed. */
    public void setTargetBlocks(byte[] targets) {
        boolean change = !Arrays.equals(targetMap, targets);
        targetMap = targets;
        if (change) {
            onTargetsAdjusted();
            setChanged();
        }
    }

    private void updateBlockWorksite() {
        level.getProfiler().push("Items Pickup");
        if (level.getGameTime() % 20 == 0) {
            pickupItems();
        }
        level.getProfiler().popPush("Count Resources");
        if (shouldCountResources) {
            countResources();
            shouldCountResources = false;
        }
        level.getProfiler().pop();
    }

    protected void countResources() {
        plantableCount = InventoryTools.getCountOf(plantableInventory, this::isPlantable);
        bonemealCount = InventoryTools.getCountOf(miscInventory, this::isBonemeal);
    }

    /** Uses one bone meal from the misc inventory on the block (as the fake player, with the growth particles). */
    protected boolean fertilize(BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        Optional<ItemStack> stack = InventoryTools.stream(miscInventory).filter(this::isBonemeal).findFirst();
        if (stack.isPresent() && WorksiteTools.applyBonemeal(serverLevel, pos, stack.get())) {
            shouldCountResources = true;
            setChanged();
            return true;
        }
        return false;
    }
}
