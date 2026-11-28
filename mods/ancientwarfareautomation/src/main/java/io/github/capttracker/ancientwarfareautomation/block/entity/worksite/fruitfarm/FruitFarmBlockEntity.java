/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/fruitfarm/WorkSiteFruitFarm.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.fruitfarm;

import io.github.capttracker.ancientwarfare.api.WorkAction;
import io.github.capttracker.ancientwarfare.api.WorkTask;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.FarmSeasons;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.FarmWorksiteBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.IWorksiteAction;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.WorksiteActions;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.WorksiteMenus;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import io.github.capttracker.ancientwarfareautomation.registry.FruitFarmRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The fruit farm ({@code WorkSiteFruitFarm}, work type farming). Scans each target column from the bottom layer up
 * seven blocks and queues ripe fruit to pick, positions where a plantable item of the plantable inventory can be
 * planted, and fruit blocks that can be fertilised. Priority: pick, plant, bone meal; each action costs one work unit.
 * Bone meal comes from the misc inventory.
 *
 * <p>Deviations from the original, both fixes:
 * <ul>
 * <li>planting a fruit now uses up the item (the original placed the pod and kept the bean, so a stack of beans never
 * ran out);</li>
 * <li>bone meal is only used on fruit blocks that are not ripe yet. 1.12.2 used it on every {@code IGrowable} block in
 * the area, which in 1.21 would mean every grass, flower and sapling that is in the area.</li>
 * </ul>
 */
public class FruitFarmBlockEntity extends FarmWorksiteBlockEntity {
    private static final int MAX_LISTED_PER_ACTION = 64;
    private final Set<BlockPos> blocksToPick = new LinkedHashSet<>();
    private final Set<BlockPos> blocksToPlant = new LinkedHashSet<>();
    private final Set<BlockPos> blocksToBoneMeal = new LinkedHashSet<>();

    private final IItemHandler inventoryForDrops;
    /** The game time at which the scan last found a place where a seed fits but the season forbids it (not saved). */
    private long seasonBlockedAt = Long.MIN_VALUE / 2;

    public FruitFarmBlockEntity(BlockPos pos, BlockState state) {
        super(AwAutomationBase.FRUIT_FARM_BE.get(), pos, state);
        inventoryForDrops = new CombinedInvWrapper(plantableInventory, mainInventory);
    }

    @Override
    public void onBoundsAdjusted() {
        super.onBoundsAdjusted();
        validateCollection(blocksToPick);
        validateCollection(blocksToPlant);
        validateCollection(blocksToBoneMeal);
    }

    @Override
    public boolean onBlockClicked(Player player, @Nullable InteractionHand hand) {
        if (!player.level().isClientSide) {
            WorksiteMenus.open(player, WorksiteMenus.Kind.FRUIT_FARM, this);
        }
        return true;
    }

    @Override
    public WorkType getWorkType() {
        return WorkType.FARMING;
    }

    @Override
    protected boolean isPlantable(ItemStack stack) {
        return FruitFarmRegistry.isPlantable(stack);
    }

    /**
     * Rework phase 3: fruit grows above the ground (cocoa on a trunk, berries in a bush), so the column is scanned over the whole vertical range
     * of the site, from its lowest block up; there is no surface to find.
     */
    @Override
    protected BlockPos columnScanPos(int x, int z) {
        return new BlockPos(x, getScanMinY(), z);
    }

    @Override
    protected void scanBlockPosition(BlockPos pos) {
        for (BlockPos currentPos = pos; currentPos.getY() <= getScanMaxY(); currentPos = currentPos.above()) {
            BlockState state = level.getBlockState(currentPos);
            IFruit fruit = FruitFarmRegistry.getPickable(state);
            if (fruit.isRipe(state)) {
                blocksToPick.add(currentPos);
            }
            BlockPos curPos = currentPos; // because streams need effectively final vars
            InventoryTools.stream(plantableInventory).filter(s -> FruitFarmRegistry.getPlantable(s).canPlant(level, curPos, state)).forEach(s -> {
                if (FarmSeasons.mayPlant(level, curPos, s)) {
                    blocksToPlant.add(curPos);
                } else {
                    seasonBlockedAt = level.getGameTime(); // a seed that fits here but not in this season (Serene Seasons)
                }
            });
            if (canBoneMeal(currentPos, state, fruit)) {
                blocksToBoneMeal.add(currentPos);
            }
        }
    }

    /** A growing fruit: a block of a known fruit that is not ripe and accepts bone meal. */
    private boolean canBoneMeal(BlockPos currentPos, BlockState state, IFruit fruit) {
        return fruit.matches(state) && !fruit.isRipe(state)
                && state.getBlock() instanceof BonemealableBlock growable && growable.isValidBonemealTarget(level, currentPos, state);
    }

    private boolean boneMeal() {
        if (blocksToBoneMeal.isEmpty()) {
            return false;
        }
        return boneMealAt(blocksToBoneMeal.iterator().next());
    }

    private boolean boneMealAt(BlockPos pos) {
        blocksToBoneMeal.remove(pos);
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof BonemealableBlock && fertilize(pos);
    }

    private boolean plantFruits() {
        if (blocksToPlant.isEmpty()) {
            return false;
        }
        return plantFruitAt(blocksToPlant.iterator().next());
    }

    private boolean plantFruitAt(BlockPos plantPos) {
        blocksToPlant.remove(plantPos);
        BlockState stateAtPos = level.getBlockState(plantPos);
        for (int slot = 0; slot < plantableInventory.getSlots(); slot++) {
            ItemStack stack = plantableInventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            IFruit fruit = FruitFarmRegistry.getPlantable(stack);
            if (fruit.canPlant(level, plantPos, stateAtPos) && FarmSeasons.mayPlant(level, plantPos, stack) && fruit.plant(level, plantPos)) {
                plantableInventory.extractItem(slot, 1, false);
                return true;
            }
        }
        return false;
    }

    private boolean pickFruits() {
        if (blocksToPick.isEmpty()) {
            return false;
        }
        return pickFruitAt(blocksToPick.iterator().next(), inventoryForDrops);
    }

    private boolean pickFruitAt(BlockPos pickPos, IItemHandler drops) {
        blocksToPick.remove(pickPos);
        BlockState state = level.getBlockState(pickPos);
        IFruit pickable = FruitFarmRegistry.getPickable(state);
        return pickable.pick(level, state, pickPos, getFortune(), drops);
    }

    // ---- staffed work ----

    @Override
    protected boolean supportsStaffedWork() {
        return true;
    }

    /** Pick, plant, bone meal: the order of {@link #getNextAction}, one task per target. Picking is a harvest (the fixed short time of picking a crop). */
    @Override
    protected List<WorkTask> listTasks() {
        List<WorkTask> tasks = new ArrayList<>();
        blocksToPick.stream().limit(MAX_LISTED_PER_ACTION)
                .forEach(pos -> tasks.add(new WorkTask(pos, WorkAction.HARVEST, WorkTask.MIN_TICKS + 5)));
        if (hasPlantable()) {
            blocksToPlant.stream().limit(MAX_LISTED_PER_ACTION)
                    .forEach(pos -> tasks.add(new WorkTask(pos, WorkAction.PLANT, WorkTask.SOFT_TICKS)));
        }
        if (bonemealCount > 0) {
            blocksToBoneMeal.stream().limit(Math.min(MAX_LISTED_PER_ACTION, bonemealCount))
                    .forEach(pos -> tasks.add(new WorkTask(pos, WorkAction.FERTILIZE, WorkTask.SOFT_TICKS)));
        }
        return tasks;
    }

    private boolean hasPlantable() {
        return plantableCount > 0;
    }

    /** A seed fitted a place in the last scans, but the season (Serene Seasons) forbids sowing it: the farm picks and fertilises, and says so. */
    @Override
    protected boolean isOutOfSeason() {
        return FarmSeasons.active() && blocksToPlant.isEmpty() && level != null && level.getGameTime() - seasonBlockedAt < 600L;
    }

    @Override
    protected boolean performTask(WorkTask task, IItemHandler drops) {
        BlockPos pos = task.pos();
        return switch (task.action()) {
            case HARVEST -> pickFruitAt(pos, drops);
            case PLANT -> plantFruitAt(pos);
            case FERTILIZE -> boneMealAt(pos);
            default -> false;
        };
    }

    private static final IWorksiteAction PICK_ACTION = WorksiteActions.full();
    private static final IWorksiteAction PLANT_ACTION = WorksiteActions.full();
    private static final IWorksiteAction BONEMEAL_ACTION = WorksiteActions.full();

    @Override
    protected Optional<IWorksiteAction> getNextAction() {
        if (!blocksToPick.isEmpty()) {
            return Optional.of(PICK_ACTION);
        } else if (!blocksToPlant.isEmpty()) {
            return Optional.of(PLANT_ACTION);
        } else if (!blocksToBoneMeal.isEmpty()) {
            return Optional.of(BONEMEAL_ACTION);
        }
        return Optional.empty();
    }

    @Override
    protected boolean processAction(IWorksiteAction action) {
        if (action == PICK_ACTION) {
            return pickFruits();
        } else if (action == PLANT_ACTION) {
            return plantFruits();
        } else if (action == BONEMEAL_ACTION) {
            return boneMeal();
        }
        return false;
    }
}
