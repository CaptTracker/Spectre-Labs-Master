/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/WorkSiteFishFarm.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import io.github.capttracker.ancientwarfare.api.WorkAction;
import io.github.capttracker.ancientwarfare.api.WorkTask;
import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfareautomation.entity.WorkBobberEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The fish farm ({@code WorkSiteFishFarm}, work type farming). Every 200 ticks it counts the water in its work area
 * (4 layers below the machine: each column counts from the top while there is water). An action succeeds with the
 * chance {@code waterBlockCount / 1280}, which can never reach 100 percent: the largest area, 16 x 16 x 4 = 1024
 * blocks, is smaller than the 1280 (kept as in the original). A success gives fish (the fishing loot table, with the
 * fortune of the tools as luck) or ink sacs (1 plus up to fortune more); if both are switched on in the second screen a
 * coin flip decides. Everything must fit into the main inventory. A failed roll counts as a failed action: the worksite
 * waits 20 ticks and tries again, no torque is used.
 *
 * <p><b>Shore fisher</b> (rework phase 3, staffed): the farm lists a FISH task for every water block at the edge of the water inside its area (a source
 * block with air above and dry land beside it, found again every 5 seconds). The worker walks to the shore, casts a {@link WorkBobberEntity} onto the water
 * block, waits for the vanilla bite (100 to 600 ticks less the rod's lure, a fish approaching, a bite of 20 to 40 ticks) and reels in. The loot is the vanilla
 * fishing loot table with the worker's rod (luck of the sea and the worker's luck attribute count); treasure only comes from open water, by the vanilla
 * 5 x 4 x 5 test the bobber runs. Ink sacs and the fish/ink switches of the second screen work as before. The machine loop above is kept for
 * {@code machinesWorkAlone}.
 *
 * <p>1.21 fishing loot: the {@code gameplay/fishing} table is rolled with the machine's position as origin, a fishing rod as
 * tool and the luck of the upgrades. There is no fishing hook, so the "in open water" treasure branch of the table
 * never applies: the farm gives fish and junk, no treasure. (A data pack can replace the table or
 * the farm can get its own one later.)
 */
public class FishFarmBlockEntity extends InventoryWorksiteBlockEntity {
    private static final int MAX_WATER = 1280;
    private boolean harvestFish = true;
    private boolean harvestInk = true;

    private int waterBlockCount = 0;
    private int waterRescanDelay = 0;

    public FishFarmBlockEntity(BlockPos pos, BlockState state) {
        super(AwAutomationBase.FISH_FARM_BE.get(), pos, state);
    }

    // ---- the shore fisher (staffed) ----

    private static final int MAX_LISTED = 64;
    private static final int SPOT_RESCAN_TICKS = 100;
    private static final int CAST_TICKS = 40;

    private List<BlockPos> fishSpots = List.of();
    private int spotRescanDelay = 0;
    private final FishingSupport fishing = new FishingSupport();

    @Override
    protected boolean supportsStaffedWork() {
        return true;
    }

    private void scanFishSpots() {
        List<BlockPos> spots = new ArrayList<>();
        if (!hasWorkBounds()) {
            fishSpots = spots;
            return;
        }
        BlockPos min = getWorkBoundsMin();
        BlockPos max = getWorkBoundsMax();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = min.getX(); x <= max.getX() && spots.size() < MAX_LISTED; x++) {
            for (int z = min.getZ(); z <= max.getZ() && spots.size() < MAX_LISTED; z++) {
                if (!level.isLoaded(cursor.set(x, min.getY(), z))) {
                    continue; // never load a chunk for a scan
                }
                // the surface of the water in this column: the first block from the top that is not air
                for (int y = max.getY() + 1; y >= min.getY(); y--) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) {
                        continue;
                    }
                    if (state.getFluidState().is(FluidTags.WATER) && state.getFluidState().isSource() && isShore(cursor.immutable())) {
                        spots.add(cursor.immutable());
                    }
                    break;
                }
            }
        }
        fishSpots = spots;
    }

    /** A worker can stand beside the water block: a horizontal neighbour at the same level that is open (air) with solid ground below it. */
    private boolean isShore(BlockPos water) {
        if (!level.getBlockState(water.above()).isAir()) {
            return false;
        }
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos side = water.relative(dir);
            if (!level.isLoaded(side)) {
                continue;
            }
            BlockState sideState = level.getBlockState(side);
            BlockState below = level.getBlockState(side.below());
            if (sideState.isAir() && below.getFluidState().isEmpty() && below.blocksMotion()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected List<WorkTask> listTasks() {
        if ((!harvestFish && !harvestInk) || fishSpots.isEmpty() || !hasFreeSlot()) {
            return List.of();
        }
        List<WorkTask> tasks = new ArrayList<>(fishSpots.size());
        for (BlockPos pos : fishSpots) {
            tasks.add(new WorkTask(pos, WorkAction.FISH, CAST_TICKS));
        }
        return tasks;
    }

    private boolean hasFreeSlot() {
        for (int slot = 0; slot < mainInventory.getSlots(); slot++) {
            ItemStack stack = mainInventory.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    /** The worker is at the shore: it casts the bobber onto the water block. */
    @Override
    public void taskStarted(UUID worker, WorkTask task, LivingEntity actor) {
        if (level instanceof ServerLevel serverLevel && task.action() == WorkAction.FISH) {
            fishing.cast(serverLevel, worker, task.pos(), actor);
        }
    }

    /** The worker reels in only while a fish bites. */
    @Override
    public boolean isTaskReady(UUID worker, WorkTask task) {
        if (task.action() != WorkAction.FISH) {
            return true;
        }
        return fishing.isReelReady(level, worker);
    }

    @Override
    public boolean keepTask(UUID worker, WorkTask task) {
        if (task.action() == WorkAction.FISH && fishing.hasBobber(worker) && fishing.bobberOf(level, worker) == null) {
            // the bobber is gone (the water changed, it was unloaded): the claim ends and the worker casts anew with its next claim
            releaseTask(worker, task, false);
            return false;
        }
        boolean kept = super.keepTask(worker, task);
        if (kept) {
            fishing.keepAlive(level, worker);
        }
        return kept;
    }

    @Override
    protected void onTaskEnded(UUID worker, WorkTask task) {
        fishing.end(level, worker);
    }

    @Override
    protected boolean performTask(WorkTask task, IItemHandler drops) {
        if (!(level instanceof ServerLevel serverLevel) || task.action() != WorkAction.FISH) {
            return false;
        }
        Map.Entry<UUID, WorkBobberEntity> found = fishing.findAt(serverLevel, task.pos());
        WorkBobberEntity bobber = found == null ? null : found.getValue();
        if (bobber == null || !bobber.isReelReady()) {
            return false;
        }
        boolean openWater = bobber.isOpenWater();
        LivingEntity actor = currentActor;
        boolean fish = harvestFish;
        boolean ink = harvestInk;
        if (fish && ink) {
            fish = level.random.nextBoolean();
            ink = !fish;
        }
        List<ItemStack> loot;
        if (fish) {
            loot = FishingSupport.rollLoot(serverLevel, actor, bobber, openWater);
        } else if (ink) {
            ItemStack inkItem = new ItemStack(Items.INK_SAC);
            int fortune = getFortune();
            if (fortune > 0) {
                inkItem.grow(level.random.nextInt(fortune + 1));
            }
            loot = List.of(inkItem);
        } else {
            return false;
        }
        for (ItemStack stack : loot) {
            ItemStack left = ItemHandlerHelper.insertItemStacked(drops, stack, false);
            if (!left.isEmpty()) {
                InventoryTools.insertOrDropItem(mainInventory, left, level, worldPosition);
            }
        }
        FishingSupport.playReel(serverLevel, task.pos());
        return true;
    }

    private static final IWorksiteAction FISH_ACTION = WorksiteActions.full();

    @Override
    protected Optional<IWorksiteAction> getNextAction() {
        return waterBlockCount > 0 ? Optional.of(FISH_ACTION) : Optional.empty();
    }

    @Override
    protected boolean processAction(IWorksiteAction action) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        float percentOfMax = ((float) waterBlockCount) / MAX_WATER;
        float check = level.random.nextFloat();
        if (check <= percentOfMax) {
            boolean fish = harvestFish;
            boolean ink = harvestInk;
            if (fish && ink) {
                fish = level.random.nextBoolean();
                ink = !fish;
            }
            if (fish) {
                List<ItemStack> fishStacks = rollFishingLoot(serverLevel);

                if (!InventoryTools.canInventoryHold(mainInventory, fishStacks)) {
                    return false;
                }

                for (ItemStack fishStack : fishStacks) {
                    InventoryTools.insertOrDropItem(mainInventory, fishStack, level, worldPosition);
                }
                return true;
            }
            if (ink) {
                ItemStack inkItem = new ItemStack(Items.INK_SAC);
                int fortune = getFortune();
                if (fortune > 0) {
                    inkItem.grow(level.random.nextInt(fortune + 1));
                }
                if (!InventoryTools.canInventoryHold(mainInventory, inkItem)) {
                    return false;
                }

                InventoryTools.insertOrDropItem(mainInventory, inkItem, level, worldPosition);
                return true;
            }
        }
        return false;
    }

    /** 1.12.2: {@code LootTableList.GAMEPLAY_FISHING.generateLootForPools(rand, context with luck)}. */
    private List<ItemStack> rollFishingLoot(ServerLevel serverLevel) {
        LootParams params = new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                .withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD))
                .withLuck((float) getFortune())
                .create(LootContextParamSets.FISHING);
        LootTable table = serverLevel.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
        return table.getRandomItems(params);
    }

    @Override
    public boolean userAdjustableBlocks() {
        return false;
    }

    /** The work area is the four layers right below the machine. */
    private void offsetBounds() {
        BlockPos boundsMax = getWorkBoundsMax();
        setWorkBoundsMax(boundsMax.above(worldPosition.getY() - 1 - boundsMax.getY()));
        BlockPos boundsMin = getWorkBoundsMin();
        setWorkBoundsMin(boundsMin.above(worldPosition.getY() - 5 - boundsMin.getY()));
        BlockTools.notifyBlockUpdate(this);
    }

    @Override
    protected void onBoundsSet() {
        super.onBoundsSet();
        offsetBounds();
    }

    @Override
    public void onBoundsAdjusted() {
        super.onBoundsAdjusted();
        offsetBounds();
    }

    @Override
    public int getBoundsMaxHeight() {
        return 4;
    }

    public boolean harvestFish() {
        return harvestFish;
    }

    public boolean harvestInk() {
        return harvestInk;
    }

    /** Sets what the farm harvests (from the second screen). */
    public void setHarvest(boolean fish, boolean ink) {
        if (harvestFish != fish || harvestInk != ink) {
            harvestFish = fish;
            harvestInk = ink;
            setChanged();
        }
    }

    private void countWater() {
        waterBlockCount = 0;
        BlockPos min = getWorkBoundsMin();
        BlockPos max = getWorkBoundsMax();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                if (!level.isLoaded(new BlockPos(x, min.getY(), z))) {
                    continue;
                }
                for (int y = max.getY(); y >= min.getY(); y--) {
                    BlockState state = level.getBlockState(new BlockPos(x, y, z));
                    if (state.getFluidState().is(FluidTags.WATER)) {
                        waterBlockCount++;
                    } else {
                        break;
                    }
                }
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // a missing tag (a block entity saved before the options existed) keeps both switched on, the default
        harvestFish = !tag.contains("fish") || tag.getBoolean("fish");
        harvestInk = !tag.contains("ink") || tag.getBoolean("ink");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("fish", harvestFish);
        tag.putBoolean("ink", harvestInk);
    }

    @Override
    public WorkType getWorkType() {
        return WorkType.FARMING;
    }

    @Override
    public boolean onBlockClicked(Player player, @Nullable InteractionHand hand) {
        if (!player.level().isClientSide) {
            WorksiteMenus.open(player, WorksiteMenus.Kind.FISH_FARM, this);
        }
        return true;
    }

    @Override
    public void openAltGui(Player player) {
        WorksiteMenus.open(player, WorksiteMenus.Kind.FISH_CONTROL, this);
    }

    @Override
    protected void updateWorksite() {
        level.getProfiler().push("WaterCount");
        if (waterRescanDelay-- <= 0) {
            countWater();
            waterRescanDelay = 200;
        }
        if (isStaffed() && spotRescanDelay-- <= 0) {
            scanFishSpots();
            spotRescanDelay = SPOT_RESCAN_TICKS;
        }
        level.getProfiler().pop();
    }
}
