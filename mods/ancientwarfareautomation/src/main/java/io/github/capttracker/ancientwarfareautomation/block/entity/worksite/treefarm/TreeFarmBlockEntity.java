/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/treefarm/WorkSiteTreeFarm.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.treefarm;

import io.github.capttracker.ancientwarfare.api.WorkAction;
import io.github.capttracker.ancientwarfare.api.WorkTask;
import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfare.util.FakePlayers;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.FarmWorksiteBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.IWorksiteAction;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.WorksiteActions;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.WorksiteMenus;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.WorksiteTools;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import io.github.capttracker.ancientwarfareautomation.registry.TreeFarmRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.IShearable;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The tree farm ({@code WorkSiteTreeFarm}, work type forestry). The scan of a position either queues planting (a
 * replaceable position on a registered soil, also the hanging variant: a replaceable position below a soil block),
 * fertilising (a growing sapling) or, when nothing is being cut, scans the whole tree standing there
 * ({@code addTreeBlocks}: only one tree is queued at a time, within 15 blocks of the starting block). Priority: shear
 * leaves (needs shears in the misc inventory), chop leaves (a tenth of a work unit), chop trunk (half), plant (needs
 * saplings), bone meal. Without shears the leaves are chopped for their drops.
 *
 * <p>The two chop queues are saved ({@code targetList}, {@code targetLeafList}); everything else is found again by
 * scanning. Drops of leaves and logs go to the plantable inventory first (saplings), then the main inventory.
 *
 * <p>1.21 changes: trunks and leaves come from the scanners of the {@code TreeFarmRegistry} (tags and data files,
 * no materials); shearing leaves takes the drops of the block's loot table with the shears as the tool ({@code IShearable}
 * on blocks only says "can be sheared" in NeoForge 1.21); bone meal is only used on blocks that are plantables of the
 * farm (saplings, mushrooms, ...), not on any {@code BonemealableBlock} (1.12.2 {@code IGrowable} made no difference
 * between a sapling and the grass block of a hillside).
 */
public class TreeFarmBlockEntity extends FarmWorksiteBlockEntity {
    private static final int MAX_DISTANCE_TO_INITIAL_CUT_POSITION = 15;
    private static final String TARGET_LIST_TAG = "targetList";
    private static final String TARGET_LEAF_LIST_TAG = "targetLeafList";
    private static final String REPLANT_LIST_TAG = "replantSpots";
    private boolean hasShears;
    private final Set<BlockPos> blocksToShear = new LinkedHashSet<>();
    private final Set<BlockPos> leafBlocksToChop = new LinkedHashSet<>();
    private final Set<BlockPos> trunkBlocksToChop = new LinkedHashSet<>();
    private final Set<BlockPos> blocksToPlant = new LinkedHashSet<>();
    private final Set<BlockPos> blocksToFertilize = new LinkedHashSet<>();
    /** Where the foot of a trunk stood: saplings go back here (saved). */
    private final Set<BlockPos> replantSpots = new LinkedHashSet<>();

    private final IItemHandler inventoryForDrops;

    public TreeFarmBlockEntity(BlockPos pos, BlockState state) {
        super(AwAutomationBase.TREE_FARM_BE.get(), pos, state);
        inventoryForDrops = new CombinedInvWrapper(plantableInventory, mainInventory);
    }

    @Override
    protected boolean isPlantable(ItemStack stack) {
        return TreeFarmRegistry.isPlantable(stack);
    }

    @Override
    protected boolean isMiscItem(ItemStack stack) {
        return stack.getItem() instanceof ShearsItem || super.isMiscItem(stack);
    }

    @Override
    public void onBoundsAdjusted() {
        validateCollection(blocksToFertilize);
        validateCollection(trunkBlocksToChop, false); // a tree reaches above the vertical range
        validateCollection(leafBlocksToChop, false);
        validateCollection(blocksToPlant);
        validateCollection(replantSpots);
        if (!hasShears) {
            blocksToShear.clear();
        }
        setChanged();
    }

    @Override
    protected void countResources() {
        super.countResources();
        hasShears = InventoryTools.getCountOf(miscInventory, s -> s.getItem() instanceof ShearsItem) > 0;
    }

    private boolean bonemealBlock() {
        if (bonemealCount <= 0 || blocksToFertilize.isEmpty()) {
            return false;
        }

        Iterator<BlockPos> it = blocksToFertilize.iterator();
        BlockPos position = it.next();
        it.remove();

        return bonemealAt(position);
    }

    private boolean bonemealAt(BlockPos position) {
        blocksToFertilize.remove(position);
        BlockState state = level.getBlockState(position);
        return canFertilize(position, state) && fertilize(position);
    }

    private boolean plant() {
        if (plantableCount <= 0 || blocksToPlant.isEmpty()) {
            return false;
        }
        Iterator<BlockPos> it = blocksToPlant.iterator();
        while (it.hasNext()) {
            BlockPos position = it.next();
            it.remove();
            if (plantAt(position)) {
                return true;
            }
        }
        return false;
    }

    /** Plants the first sapling of the plantable inventory at the position (if it is free). */
    private boolean plantAt(BlockPos position) {
        blocksToPlant.remove(position);
        if (!canReplace(position) || !level.getFluidState(position).isEmpty()) {
            return false;
        }
        // the first sapling of the plantable inventory, with its slot
        int plantableSlot = -1;
        ISapling sapling = null;
        for (int slot = 0; slot < plantableInventory.getSlots() && sapling == null; slot++) {
            ItemStack candidate = plantableInventory.getStackInSlot(slot);
            if (!candidate.isEmpty()) {
                Optional<ISapling> found = TreeFarmRegistry.getSapling(candidate);
                if (found.isPresent()) {
                    plantableSlot = slot;
                    sapling = found.get();
                }
            }
        }
        if (sapling == null) {
            return false;
        }

        ItemStack stack = plantableInventory.getStackInSlot(plantableSlot).copy();
        if (tryPlantingSapling(position, stack, sapling)) {
            InventoryTools.removeItems(plantableInventory, stack, 1);
            replantSpots.remove(position);
            return true;
        }
        return false;
    }

    private boolean tryPlantingSapling(BlockPos position, ItemStack stack, ISapling sapling) {
        return sapling.isRightClick() ? BlockTools.placeItemBlockRightClick(stack.copy(), level, position) :
                tryPlace(stack.copy(), position, Direction.UP) || tryPlace(stack.copy(), position, Direction.DOWN);
    }

    private boolean chopBlock(boolean wood) {
        if ((wood && trunkBlocksToChop.isEmpty()) || (!wood && leafBlocksToChop.isEmpty())) {
            return false;
        }

        Iterator<BlockPos> it = wood ? trunkBlocksToChop.iterator() : leafBlocksToChop.iterator();
        BlockPos position = it.next();
        BlockState state = level.getBlockState(position);
        if (state.isAir()) {
            it.remove();
            return false;
        }

        IBlockExtraDrop extraDrop = TreeFarmRegistry.getBlockExtraDrop(state);
        NonNullList<ItemStack> extraDrops = extraDrop.getDrops(level, position, state, getFortune());
        if (!harvestBlock(position)) {
            return false;
        }
        it.remove();
        InventoryTools.insertOrDropItems(inventoryForDrops, extraDrops, level, position);
        return true;
    }

    /** The staffed chop: breaks the block (a trunk, or a leaf block of the leaf queue) and hands what it drops to {@code drops}; the foot of a trunk is remembered for replanting. */
    private boolean chopAt(BlockPos position, IItemHandler drops) {
        trunkBlocksToChop.remove(position);
        leafBlocksToChop.remove(position);
        BlockState state = level.getBlockState(position);
        if (state.isAir() || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        NonNullList<ItemStack> extraDrops = TreeFarmRegistry.getBlockExtraDrop(state).getDrops(level, position, state, getFortune());
        List<ItemStack> stacks = WorksiteTools.getDrops(serverLevel, position, state, getFortune());
        if (!BlockTools.breakBlockNoDrops(level, position, state)) {
            return false;
        }
        InventoryTools.insertOrDropItems(drops, stacks, level, position);
        InventoryTools.insertOrDropItems(drops, extraDrops, level, position);
        if (TreeFarmRegistry.isSoil(level.getBlockState(position.below()))) {
            replantSpots.add(position.immutable());
        }
        setChanged();
        return true;
    }

    private boolean shearAt(BlockPos position, IItemHandler drops) {
        blocksToShear.remove(position);
        BlockState state = level.getBlockState(position);
        if (!hasShears || !(state.getBlock() instanceof IShearable shearable)) {
            return false;
        }
        Optional<ItemStack> shears = InventoryTools.stream(miscInventory).filter(s -> s.getItem() instanceof ShearsItem).findFirst();
        return shears.isPresent() && shear(position, state, shearable, shears.get(), drops);
    }

    private boolean shearBlock() {
        if (!hasShears || blocksToShear.isEmpty()) {
            return false;
        }

        Iterator<BlockPos> it = blocksToShear.iterator();
        BlockPos position = it.next();
        it.remove();
        BlockState state = level.getBlockState(position);
        if (state.getBlock() instanceof IShearable shearable) {
            Optional<ItemStack> shears = InventoryTools.stream(miscInventory).filter(s -> s.getItem() instanceof ShearsItem).findFirst();

            if (shears.isPresent() && shear(position, state, shearable, shears.get(), inventoryForDrops)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Shears a block. In 1.21 a block's {@code IShearable} only answers whether it can be sheared; what falls out is
     * the block's loot table evaluated with the shears as the tool (leaves, vines and grass have a shears branch).
     */
    private boolean shear(BlockPos position, BlockState state, IShearable block, ItemStack shears, IItemHandler dropTarget) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (block.isShearable(FakePlayers.get(serverLevel), shears, level, position)) {
            List<ItemStack> drops = Block.getDrops(state, serverLevel, position, level.getBlockEntity(position), null, shears);
            if (!BlockTools.breakBlockNoDrops(level, position, state)) {
                return false;
            }
            InventoryTools.insertOrDropItems(dropTarget, drops, level, worldPosition);
            return true;
        }
        return false;
    }

    private void addTreeBlocks(BlockState state, BlockPos basePos) {
        level.getProfiler().push("TreeFinder");

        ITree tree = TreeFarmRegistry.getTreeScanner(state).scanTree(level, basePos,
                basePos.offset(-MAX_DISTANCE_TO_INITIAL_CUT_POSITION, 0, -MAX_DISTANCE_TO_INITIAL_CUT_POSITION),
                basePos.offset(MAX_DISTANCE_TO_INITIAL_CUT_POSITION, 0, MAX_DISTANCE_TO_INITIAL_CUT_POSITION));
        List<BlockPos> leafBlocks = tree.getLeafPositions();
        if (hasShears) {
            blocksToShear.addAll(leafBlocks);
        } else if (!isStaffed()) {
            leafBlocksToChop.addAll(leafBlocks); // the workers leave the leaves to decay
        }
        List<BlockPos> trunkBlocks = tree.getTrunkPositions();
        trunkBlocksToChop.addAll(trunkBlocks);

        if (!leafBlocks.isEmpty() || !trunkBlocks.isEmpty()) {
            setChanged();
        }
        level.getProfiler().pop();
    }

    @Override
    public WorkType getWorkType() {
        return WorkType.FORESTRY;
    }

    @Override
    public boolean onBlockClicked(Player player, @Nullable InteractionHand hand) {
        if (!player.level().isClientSide) {
            WorksiteMenus.open(player, WorksiteMenus.Kind.TREE_FARM, this);
        }
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!trunkBlocksToChop.isEmpty()) {
            tag.put(TARGET_LIST_TAG, writePositions(trunkBlocksToChop));
        }
        if (!leafBlocksToChop.isEmpty()) {
            tag.put(TARGET_LEAF_LIST_TAG, writePositions(leafBlocksToChop));
        }
        if (!replantSpots.isEmpty()) {
            tag.put(REPLANT_LIST_TAG, writePositions(replantSpots));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        trunkBlocksToChop.clear();
        leafBlocksToChop.clear();
        readPositions(tag, TARGET_LIST_TAG, trunkBlocksToChop);
        readPositions(tag, TARGET_LEAF_LIST_TAG, leafBlocksToChop);
        replantSpots.clear();
        readPositions(tag, REPLANT_LIST_TAG, replantSpots);
    }

    private static ListTag writePositions(Set<BlockPos> positions) {
        ListTag list = new ListTag();
        for (BlockPos position : positions) {
            list.add(LongTag.valueOf(position.asLong()));
        }
        return list;
    }

    private static void readPositions(CompoundTag tag, String key, Set<BlockPos> into) {
        if (tag.contains(key, Tag.TAG_LIST)) {
            ListTag list = tag.getList(key, Tag.TAG_LONG);
            for (Tag element : list) {
                if (element instanceof LongTag longTag) {
                    into.add(BlockPos.of(longTag.getAsLong()));
                }
            }
        }
    }

    @Override
    protected void scanBlockPosition(BlockPos scanPos) {
        if (canReplace(scanPos)) {
            BlockState state = level.getBlockState(scanPos.below());
            if (level.getFluidState(scanPos).isEmpty()) {
                if (TreeFarmRegistry.isSoil(state)) {
                    if (replantSpots.contains(scanPos) || hasRoomForSapling(scanPos)) {
                        blocksToPlant.add(scanPos);
                    }
                } else if (state.canBeReplaced() && TreeFarmRegistry.isSoil(level.getBlockState(scanPos.above()))) {
                    blocksToPlant.add(scanPos);
                }
            }
        } else {
            BlockState state = level.getBlockState(scanPos);
            if (canFertilize(scanPos, state)) {
                blocksToFertilize.add(scanPos);
            } else if (!state.isAir() && trunkBlocksToChop.isEmpty() && leafBlocksToChop.isEmpty()) {
                addTreeBlocks(state, scanPos);
            }
            if (isPlantedByFarm(state) || state.is(BlockTags.LOGS)) {
                replantSpots.remove(scanPos); // somebody (or something) planted there already
            }
        }
    }

    /** Natural terrain: no sapling next to a trunk or another sapling (two blocks around, two layers up), so that planted trees do not crowd. */
    private boolean hasRoomForSapling(BlockPos pos) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    BlockState state = level.getBlockState(cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz));
                    if (state.is(BlockTags.LOGS) || state.getBlock() instanceof SaplingBlock) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean isPlantedByFarm(BlockState state) {
        return state.getBlock() instanceof SaplingBlock || TreeFarmRegistry.isPlantable(new ItemStack(state.getBlock().asItem()));
    }

    /** Rework phase 3: the surface of a column is the topmost soil block of the tree farm. */
    @Override
    protected boolean isGround(BlockState state, BlockPos pos) {
        return TreeFarmRegistry.isSoil(state);
    }

    /** A trunk or a leaf block above the soil is part of a tree standing there. */
    @Override
    protected boolean acceptsAboveGround(BlockState above, BlockPos abovePos) {
        return super.acceptsAboveGround(above, abovePos) || above.is(BlockTags.LOGS) || above.is(BlockTags.LEAVES)
                || TreeFarmRegistry.getRegisteredTreeScanner(above).isPresent();
    }

    // ---- staffed work ----

    private static final int MAX_LISTED_PLANT = 16;
    private static final int MAX_LISTED_SHEAR = 24;

    @Override
    protected boolean supportsStaffedWork() {
        return true;
    }

    /**
     * Chop (the lowest layer of the tree that is queued, so the tree comes down bottom up), then plant (where the trunk stood first), bone meal, and
     * shear (only with shears in the misc inventory; the leaves decay by themselves otherwise).
     */
    @Override
    protected List<WorkTask> listTasks() {
        List<WorkTask> tasks = new ArrayList<>();
        int lowest = Integer.MAX_VALUE;
        for (Iterator<BlockPos> it = trunkBlocksToChop.iterator(); it.hasNext(); ) {
            BlockPos pos = it.next();
            if (level.getBlockState(pos).isAir()) {
                it.remove(); // already gone
            } else {
                lowest = Math.min(lowest, pos.getY());
            }
        }
        for (BlockPos pos : trunkBlocksToChop) {
            if (pos.getY() == lowest) {
                tasks.add(WorkTask.of(level, pos, WorkAction.CHOP));
            }
        }
        if (plantableCount > 0) {
            int limit = Math.min(MAX_LISTED_PLANT, plantableCount);
            blocksToPlant.stream().sorted(Comparator.comparing((BlockPos pos) -> !replantSpots.contains(pos))).limit(limit)
                    .forEach(pos -> tasks.add(new WorkTask(pos, WorkAction.PLANT, WorkTask.SOFT_TICKS)));
        }
        if (bonemealCount > 0) {
            blocksToFertilize.stream().limit(Math.min(MAX_LISTED_PLANT, bonemealCount))
                    .forEach(pos -> tasks.add(new WorkTask(pos, WorkAction.FERTILIZE, WorkTask.SOFT_TICKS)));
        }
        blocksToShear.removeIf(pos -> !(level.getBlockState(pos).getBlock() instanceof IShearable)); // decayed leaves
        if (hasShears && !blocksToShear.isEmpty()) {
            blocksToShear.stream().sorted(Comparator.comparingInt(BlockPos::getY)).limit(MAX_LISTED_SHEAR)
                    .forEach(pos -> tasks.add(new WorkTask(pos, WorkAction.SHEAR, WorkTask.MIN_TICKS)));
        }
        return tasks;
    }

    @Override
    protected boolean performTask(WorkTask task, IItemHandler drops) {
        BlockPos pos = task.pos();
        return switch (task.action()) {
            case CHOP -> chopAt(pos, drops);
            case PLANT -> plantAt(pos);
            case FERTILIZE -> bonemealAt(pos);
            case SHEAR -> shearAt(pos, drops);
            default -> false;
        };
    }

    /** A growing plantable of this farm (sapling, mushroom, ...) that accepts bone meal. */
    private boolean canFertilize(BlockPos pos, BlockState state) {
        return state.getBlock() instanceof BonemealableBlock growable && growable.isValidBonemealTarget(level, pos, state)
                && TreeFarmRegistry.isPlantable(new ItemStack(state.getBlock().asItem()));
    }

    private static final IWorksiteAction SHEAR_ACTION = WorksiteActions.full();
    private static final IWorksiteAction CHOP_TRUNK_ACTION = WorksiteActions.fraction(2D);
    private static final IWorksiteAction CHOP_LEAF_ACTION = WorksiteActions.fraction(10D);
    private static final IWorksiteAction PLANT_ACTION = WorksiteActions.full();
    private static final IWorksiteAction BONEMEAL_ACTION = WorksiteActions.full();

    @Override
    protected Optional<IWorksiteAction> getNextAction() {
        if (hasShears && !blocksToShear.isEmpty()) {
            return Optional.of(SHEAR_ACTION);
        } else if (!leafBlocksToChop.isEmpty()) {
            return Optional.of(CHOP_LEAF_ACTION);
        } else if (!trunkBlocksToChop.isEmpty()) {
            return Optional.of(CHOP_TRUNK_ACTION);
        } else if (plantableCount > 0 && !blocksToPlant.isEmpty()) {
            return Optional.of(PLANT_ACTION);
        } else if (bonemealCount > 0 && !blocksToFertilize.isEmpty()) {
            return Optional.of(BONEMEAL_ACTION);
        }
        return Optional.empty();
    }

    @Override
    protected boolean processAction(IWorksiteAction action) {
        if (action == SHEAR_ACTION) {
            return shearBlock();
        } else if (action == CHOP_TRUNK_ACTION) {
            return chopBlock(true);
        } else if (action == CHOP_LEAF_ACTION) {
            return chopBlock(false);
        } else if (action == BONEMEAL_ACTION) {
            return bonemealBlock();
        } else if (action == PLANT_ACTION) {
            return plant();
        }
        return false;
    }
}
