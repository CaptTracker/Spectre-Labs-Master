/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/WorkSiteQuarry; mine styles added in rework phase 3.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import io.github.capttracker.ancientwarfare.api.WorkAction;
import io.github.capttracker.ancientwarfare.api.WorkTask;
import io.github.capttracker.ancientwarfare.block.BlockRotationHandler.RelativeSide;
import io.github.capttracker.ancientwarfare.upgrade.WorksiteUpgrade;
import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.mine.MineMode;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.mine.MineOp;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.mine.MinePattern;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.mine.MineSite;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.mine.OpenQuarryPattern;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.mine.ShaftPattern;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.mine.TunnelPattern;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The quarry ({@code WorkSiteQuarry}, work type mining). Since rework phase 3 it digs in one of four {@link MineMode mine styles}: the open quarry (the
 * original: the column below the machine, one chunk sized slab at a time, layer by layer), a 3x3 shaft with ladders, a branch mine and a strip mine that
 * follows ore veins. Each style is a {@link MinePattern} with its own saved cursor; the quarry asks the active pattern for the next operations and turns
 * them into staffed MINE and PLACE tasks (digging; ladders, torches and cobblestone plugs for lava and water). Tunnels stay where they are.
 *
 * <p>Everything that comes out goes into the main inventory, except what the void filter removes (cobblestone, dirt, gravel). Ladders and torches
 * have their own slots ({@link #ladderInventory}, {@link #torchInventory}, filtered; couriers stock them through the logistics needs); plugs use cobblestone
 * from the main inventory, or are free when it has none. Workers depend on the ladders to get down, so a mine that needs a ladder and has none waits
 * ({@link #isWaitingForLadder}, shown on the screen) instead of going on. Without staff (config {@code machines_work_alone}) the machine loop does the
 * first listed operation per action, into the inventory, as before.
 *
 * <p>The bounds reach from the layer below the machine down to the bottom of the world ({@code height} blocks; used by the open quarry). The cursors are reset
 * whenever an upgrade is added or the area, height or Y changes. Valid upgrades: enchanted tools 1/2, quarry medium/large, tool quality 1-3 and the quarry
 * chunk loader. Width is 16, 32 or 64.
 *
 * <p>Tool tiers: the harvest level of 1.12.2 ({@code Block.getHarvestLevel}) maps to the vanilla tier tags: blocks
 * in {@code needs_diamond_tool} are level 3, {@code needs_iron_tool} level 2, {@code needs_stone_tool} level 1, all
 * others 0. Blocks of level 2 and more are skipped unless a tool quality upgrade covers them (the machine's tool level
 * is 1; quality 1 = 2, quality 2 = 3, quality 3 = everything). Blocks that mods put in the {@code incorrect_for_*}
 * tags only are not recognised.
 */
public final class QuarryBlockEntity extends InventoryWorksiteBlockEntity implements MineSite {
    private static final int UNSET_Y = Integer.MIN_VALUE;
    private static final int MAX_TASKS = 48;
    private static final int PLACE_TICKS = 15;
    public static final int LADDER_SLOTS = 2;
    public static final int TORCH_SLOTS = 2;
    private static final int LOGISTICS_LADDER_TARGET = 32;
    private static final int LOGISTICS_TORCH_TARGET = 16;

    private boolean hasDoneInit = false;
    public int height;

    /** The chosen mine style, the Y of the shaft bottom and tunnels (UNSET_Y: a default from the quarry's height) and the void filter. */
    private MineMode mineMode = MineMode.OPEN_QUARRY;
    private int mineY = UNSET_Y;
    private boolean voidCobble;
    private boolean voidDirt;
    private boolean voidGravel;

    private final Map<MineMode, MinePattern> patterns = new EnumMap<>(MineMode.class);

    /** The ladders and torches the workers place: they are only taken from these slots (ladders and torches only). */
    public final ItemStackHandler ladderInventory = new ItemStackHandler(LADDER_SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(Items.LADDER);
        }

        @Override
        protected void onContentsChanged(int slot) {
            invalidatePlan();
            setChanged();
        }
    };
    public final ItemStackHandler torchInventory = new ItemStackHandler(TORCH_SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(Items.TORCH);
        }

        @Override
        protected void onContentsChanged(int slot) {
            invalidatePlan();
            setChanged();
        }
    };
    /** True while the pattern waits for a ladder the quarry does not have (set while planning, shown on the screen). */
    private boolean waitingForLadder;

    /** The tasks of the last plan, kept for a few ticks (planning scans the world), and the operation behind each target. */
    private List<WorkTask> cachedTasks = List.of();
    private final Map<BlockPos, MineOp> opsByPos = new HashMap<>();
    private long cacheTime = Long.MIN_VALUE;
    @Nullable
    private ChunkPos focusChunk;

    public QuarryBlockEntity(BlockPos pos, BlockState state) {
        super(AwAutomationBase.QUARRY_BE.get(), pos, state);
        patterns.put(MineMode.OPEN_QUARRY, new OpenQuarryPattern());
        patterns.put(MineMode.SHAFT, new ShaftPattern());
        patterns.put(MineMode.BRANCH, new TunnelPattern(true));
        patterns.put(MineMode.STRIP, new TunnelPattern(false));
        setSideInventory(RelativeSide.FRONT, ladderInventory, RelativeSide.FRONT);
        setSideInventory(RelativeSide.REAR, torchInventory, RelativeSide.REAR);
    }

    /** The mine needs a ladder and the ladder slots are empty: it waits for a courier or a player to stock them. */
    public boolean isWaitingForLadder() {
        return waitingForLadder;
    }

    @Override
    public void waitingForLadder() {
        waitingForLadder = true;
    }

    @Override
    public void onBlockBroken(BlockState state) {
        super.onBlockBroken(state);
        if (level != null) {
            InventoryTools.dropItemsInWorld(level, ladderInventory, worldPosition);
            InventoryTools.dropItemsInWorld(level, torchInventory, worldPosition);
        }
    }

    /** Ladders and torches are kept stocked in their slots. */
    @Override
    public List<Need> getLogisticsNeeds() {
        return List.of(new Need(ladderInventory, stack -> stack.is(Items.LADDER), LOGISTICS_LADDER_TARGET),
                new Need(torchInventory, stack -> stack.is(Items.TORCH), LOGISTICS_TORCH_TARGET));
    }

    // ************************************** MINE SETTINGS ***************************************//

    public MineMode getMineMode() {
        return mineMode;
    }

    /** The shaft bottom and tunnel Y, never above the layer 3 below the machine or below the world's bottom. */
    @Override
    public int mineY() {
        int lowest = level == null ? -63 : level.getMinBuildHeight() + 1;
        int highest = Math.max(lowest, worldPosition.getY() - 3);
        int y = mineY == UNSET_Y ? worldPosition.getY() - 20 : mineY;
        return Mth.clamp(y, lowest, highest);
    }

    public int getMineYMin() {
        return level == null ? -63 : level.getMinBuildHeight() + 1;
    }

    public int getMineYMax() {
        return Math.max(getMineYMin(), worldPosition.getY() - 3);
    }

    public boolean isVoidCobble() {
        return voidCobble;
    }

    public boolean isVoidDirt() {
        return voidDirt;
    }

    public boolean isVoidGravel() {
        return voidGravel;
    }

    /** Server side, from the menu after it validated the values. Changing the mode or the Y keeps the saved cursors of the other modes. */
    public void setMineSettings(MineMode mode, int y, boolean cobble, boolean dirt, boolean gravel) {
        boolean yChanged = y != mineY();
        mineMode = mode;
        mineY = Mth.clamp(y, getMineYMin(), getMineYMax());
        voidCobble = cobble;
        voidDirt = dirt;
        voidGravel = gravel;
        if (yChanged) {
            for (MinePattern pattern : patterns.values()) {
                if (pattern.mode().usesY()) {
                    pattern.reset(this);
                }
            }
        }
        invalidatePlan();
        setChanged();
    }

    private MinePattern pattern() {
        return patterns.get(mineMode);
    }

    private boolean isVoided(ItemStack stack) {
        if (voidCobble && (stack.is(Items.COBBLESTONE) || stack.is(Items.COBBLED_DEEPSLATE))) {
            return true;
        }
        if (voidDirt && (stack.is(Items.DIRT) || stack.is(Items.COARSE_DIRT))) {
            return true;
        }
        return voidGravel && stack.is(Items.GRAVEL);
    }

    // ************************************** BOUNDS ***************************************//

    @Override
    public boolean userAdjustableBlocks() {
        return false;
    }

    @Override
    protected void onBoundsSet() {
        super.onBoundsSet();
        offsetBounds();
    }

    /** The depth of the digging area: the saved height, or (before the first update) the distance to the bottom of the world. */
    private int effectiveHeight() {
        if (height > 0) {
            return height;
        }
        return worldPosition.getY() - (level == null ? 0 : level.getMinBuildHeight());
    }

    private void offsetBounds() {
        BlockPos boundsMax = getWorkBoundsMax();
        setWorkBoundsMax(boundsMax.above(worldPosition.getY() - 1 - boundsMax.getY()));
        BlockPos boundsMin = getWorkBoundsMin();
        setWorkBoundsMin(boundsMin.above(worldPosition.getY() - effectiveHeight() - boundsMin.getY()));
        BlockTools.notifyBlockUpdate(this);
    }

    @Override
    public void onBoundsAdjusted() {
        offsetBounds();
        resetCursor();
    }

    private void resetCursor() {
        for (MinePattern pattern : patterns.values()) {
            pattern.reset(this);
        }
        invalidatePlan();
    }

    private void invalidatePlan() {
        invalidateTasks();
        cacheTime = Long.MIN_VALUE;
        cachedTasks = List.of();
        opsByPos.clear();
    }

    @Override
    public Set<WorksiteUpgrade> getValidUpgrades() {
        return EnumSet.of(WorksiteUpgrade.ENCHANTED_TOOLS_1, WorksiteUpgrade.ENCHANTED_TOOLS_2, WorksiteUpgrade.QUARRY_MEDIUM, WorksiteUpgrade.QUARRY_LARGE,
                WorksiteUpgrade.TOOL_QUALITY_1, WorksiteUpgrade.TOOL_QUALITY_2, WorksiteUpgrade.TOOL_QUALITY_3, WorksiteUpgrade.QUARRY_CHUNK_LOADER);
    }

    @Override
    public int getBoundsMaxWidth() {
        if (getUpgrades().contains(WorksiteUpgrade.QUARRY_LARGE)) {
            return 64;
        }
        return getUpgrades().contains(WorksiteUpgrade.QUARRY_MEDIUM) ? 32 : 16;
    }

    @Override
    protected void updateWorksite() {
        initIfNeeded();
    }

    private void initIfNeeded() {
        if (!hasDoneInit && level != null) {
            initWorkSite();
            hasDoneInit = true;
        }
    }

    @Override
    public void addUpgrade(WorksiteUpgrade upgrade) {
        super.addUpgrade(upgrade);
        resetCursor();
    }

    // ************************************** MINE SITE (what the patterns ask) ***************************************//

    @Override
    public Level level() {
        return level;
    }

    @Override
    public BlockPos boundsMin() {
        return getWorkBoundsMin();
    }

    @Override
    public BlockPos boundsMax() {
        return getWorkBoundsMax();
    }

    @Override
    public int siteY() {
        return worldPosition.getY();
    }

    @Override
    public int worldMinY() {
        return level.getMinBuildHeight();
    }

    @Override
    public int openBottomY() {
        return worldPosition.getY() - height;
    }

    @Override
    public int branchLength() {
        return AWAutomationStatics.branchLength;
    }

    @Override
    public boolean canDig(BlockPos pos, BlockState state) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock || !state.getFluidState().isEmpty()) {
            return false;
        }
        int harvestLevel = getHarvestLevel(state);
        if (harvestLevel >= 2) {
            int toolLevel = 1;
            if (getUpgrades().contains(WorksiteUpgrade.TOOL_QUALITY_3)) {
                toolLevel = Integer.MAX_VALUE;
            } else if (getUpgrades().contains(WorksiteUpgrade.TOOL_QUALITY_2)) {
                toolLevel = 3;
            } else if (getUpgrades().contains(WorksiteUpgrade.TOOL_QUALITY_1)) {
                toolLevel = 2;
            }
            if (toolLevel < harvestLevel) {
                return false;
            }//else is harvestable, check the rest of the checks
        }
        return state.getDestroySpeed(level, pos) >= 0;
    }

    @Override
    public boolean isOre(BlockState state) {
        return state.is(Tags.Blocks.ORES);
    }

    @Override
    public int stock(Item item) {
        ItemStackHandler inventory = inventoryFor(item);
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Ladders and torches are only kept in their own slots; everything else (cobblestone for plugs) in the main inventory. */
    private ItemStackHandler inventoryFor(Item item) {
        if (item == Items.LADDER) {
            return ladderInventory;
        }
        return item == Items.TORCH ? torchInventory : mainInventory;
    }

    /** Takes one of the item out of the inventory it is kept in. */
    private boolean takeOne(Item item) {
        ItemStackHandler inventory = inventoryFor(item);
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (inventory.getStackInSlot(slot).is(item) && !inventory.extractItem(slot, 1, false).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** With a chunk loader upgrade the quarry keeps the chunk it works in loaded (besides its own), not the whole area. */
    @Override
    public void focus(BlockPos pos) {
        if (!hasChunkLoaderUpgrade()) {
            return;
        }
        ChunkPos chunk = new ChunkPos(pos);
        if (chunk.equals(focusChunk)) {
            return;
        }
        if (focusChunk != null && !focusChunk.equals(new ChunkPos(worldPosition))) {
            unforceChunk(focusChunk);
        }
        focusChunk = chunk;
        forceChunk(chunk);
    }

    @Override
    protected void chunkLoadWorkBounds() {
        if (hasChunkLoaderUpgrade() && focusChunk != null) {
            forceChunk(focusChunk);
        }
    }

    // ************************************** TASKS ***************************************//

    @Override
    protected boolean supportsStaffedWork() {
        return true;
    }

    @Nullable
    @Override
    public IItemHandler getDepositInventory() {
        return mainInventory;
    }

    @Override
    protected List<WorkTask> listTasks() {
        if (level == null || level.isClientSide) {
            return List.of();
        }
        initIfNeeded();
        long now = level.getGameTime();
        if (cacheTime != Long.MIN_VALUE && now >= cacheTime && now - cacheTime < 5) {
            return cachedTasks;
        }
        List<MineOp> ops = new ArrayList<>();
        waitingForLadder = false;
        pattern().plan(this, ops, MAX_TASKS);
        opsByPos.clear();
        List<WorkTask> tasks = new ArrayList<>(ops.size());
        for (MineOp op : ops) {
            if (opsByPos.putIfAbsent(op.pos(), op) != null) {
                continue;
            }
            tasks.add(op.action() == WorkAction.MINE ? WorkTask.of(level, op.pos(), WorkAction.MINE) : new WorkTask(op.pos(), WorkAction.PLACE, PLACE_TICKS));
        }
        cachedTasks = tasks;
        cacheTime = now;
        setChanged();
        return tasks;
    }

    @Override
    protected boolean performTask(WorkTask task, IItemHandler drops) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        MineOp op = opsByPos.get(task.pos());
        if (op == null) {
            invalidatePlan();
            listTasks();
            op = opsByPos.get(task.pos());
        }
        if (op == null) {
            return false;
        }
        boolean done = op.action() == WorkAction.MINE ? dig(serverLevel, op, drops) : place(serverLevel, op);
        invalidatePlan();
        if (done) {
            pattern().completed(this, op);
            setChanged();
        }
        return done;
    }

    private boolean dig(ServerLevel serverLevel, MineOp op, IItemHandler drops) {
        BlockPos pos = op.pos();
        BlockState state = level.getBlockState(pos);
        if (!canDig(pos, state) || (op.kind() == MineOp.Kind.VEIN && !isOre(state))) {
            return false;
        }
        List<ItemStack> stacks = new ArrayList<>(WorksiteTools.getDrops(serverLevel, pos, state, getFortune()));
        stacks.removeIf(this::isVoided);
        if (drops == mainInventory && !InventoryTools.insertItems(mainInventory, stacks, true).isEmpty()) {
            return false;
        }
        if (!BlockTools.breakBlockNoDrops(level, pos, state)) {
            return false;
        }
        for (ItemStack stack : stacks) {
            ItemStack left = ItemHandlerHelper.insertItemStacked(drops, stack, false);
            if (!left.isEmpty()) {
                InventoryTools.insertOrDropItem(mainInventory, left, level, worldPosition);
            }
        }
        return true;
    }

    private boolean place(ServerLevel serverLevel, MineOp op) {
        BlockPos pos = op.pos();
        BlockState place = op.placeState();
        BlockState there = level.getBlockState(pos);
        if (place == null) {
            return false;
        }
        if (op.kind() == MineOp.Kind.SEAL) {
            if (there.getFluidState().isEmpty() && !there.isAir()) {
                return false;
            }
            takeOne(Items.COBBLESTONE); // free when there is none
        } else {
            if (!there.isAir() || !place.canSurvive(level, pos)) {
                return false;
            }
            Item item = op.kind() == MineOp.Kind.LADDER ? Items.LADDER : Items.TORCH;
            if (!takeOne(item)) {
                return false;
            }
        }
        level.setBlock(pos, place, 3);
        serverLevel.playSound(null, pos, place.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.8F, 1.0F);
        return true;
    }

    // ************************************** MACHINE LOOP (machines_work_alone) ***************************************//

    private static final IWorksiteAction DIG_ACTION = WorksiteActions.full();

    @Override
    protected Optional<IWorksiteAction> getNextAction() {
        return !pattern().isFinished() ? Optional.of(DIG_ACTION) : Optional.empty();
    }

    @Override
    protected boolean processAction(IWorksiteAction action) {
        initIfNeeded();
        List<WorkTask> tasks = listTasks();
        if (tasks.isEmpty()) {
            return false;
        }
        return performTask(tasks.get(0), mainInventory);
    }

    /** The 1.12.2 harvest level of a block (0 wood, 1 stone, 2 iron, 3 diamond), from the vanilla tool tier tags. */
    private static int getHarvestLevel(BlockState state) {
        if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) {
            return 3;
        }
        if (state.is(BlockTags.NEEDS_IRON_TOOL)) {
            return 2;
        }
        if (state.is(BlockTags.NEEDS_STONE_TOOL)) {
            return 1;
        }
        return 0;
    }

    private void initWorkSite() {
        height = worldPosition.getY() - level.getMinBuildHeight();
        BlockPos boundsMin = getWorkBoundsMin();
        setWorkBoundsMin(boundsMin.above(worldPosition.getY() - height - boundsMin.getY()));
        resetCursor();
        BlockTools.notifyBlockUpdate(this);//resend work-bounds change
    }

    @Override
    public WorkType getWorkType() {
        return WorkType.MINING;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        hasDoneInit = tag.getBoolean("init");
        height = tag.getInt("height");
        mineMode = MineMode.byId(tag.getString("mineMode"));
        mineY = tag.contains("mineY") ? tag.getInt("mineY") : UNSET_Y;
        voidCobble = tag.getBoolean("voidCobble");
        voidDirt = tag.getBoolean("voidDirt");
        voidGravel = tag.getBoolean("voidGravel");
        ladderInventory.deserializeNBT(registries, tag.getCompound("ladderInventory"));
        torchInventory.deserializeNBT(registries, tag.getCompound("torchInventory"));
        if (tag.contains("patterns", 10)) {
            CompoundTag saved = tag.getCompound("patterns");
            for (MinePattern pattern : patterns.values()) {
                pattern.load(saved.getCompound(pattern.mode().id()));
            }
        } else if (tag.contains("current")) {
            // a save of the single pattern quarry: its cursor and finished flag are the open quarry's
            CompoundTag open = new CompoundTag();
            open.putLong("cursor", tag.getLong("current"));
            open.putBoolean("finished", tag.getBoolean("finished"));
            patterns.get(MineMode.OPEN_QUARRY).load(open);
        }
        invalidatePlan();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("init", hasDoneInit);
        tag.putInt("height", height);
        tag.putString("mineMode", mineMode.id());
        if (mineY != UNSET_Y) {
            tag.putInt("mineY", mineY);
        }
        tag.putBoolean("voidCobble", voidCobble);
        tag.putBoolean("voidDirt", voidDirt);
        tag.putBoolean("voidGravel", voidGravel);
        tag.put("ladderInventory", ladderInventory.serializeNBT(registries));
        tag.put("torchInventory", torchInventory.serializeNBT(registries));
        CompoundTag saved = new CompoundTag();
        for (MinePattern pattern : patterns.values()) {
            CompoundTag one = new CompoundTag();
            pattern.save(one);
            saved.put(pattern.mode().id(), one);
        }
        tag.put("patterns", saved);
    }

    @Override
    public boolean onBlockClicked(Player player, @Nullable InteractionHand hand) {
        if (!player.level().isClientSide) {
            WorksiteMenus.open(player, WorksiteMenus.Kind.QUARRY, this);
        }
        return true;
    }
}
