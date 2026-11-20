/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/cropfarm/WorkSiteCropFarm.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.cropfarm;

import io.github.capttracker.ancientwarfare.api.WorkAction;
import io.github.capttracker.ancientwarfare.api.WorkTask;
import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.FarmWorksiteBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.IWorksiteAction;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.WorksiteActions;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.WorksiteMenus;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import io.github.capttracker.ancientwarfareautomation.registry.CropFarmRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The crop farm ({@code WorkSiteCropFarm}, work type farming). Scans the one-layer work area position by position
 * and queues what it finds: tilling (a replaceable position above a tillable block), planting (a replaceable
 * position above soil), harvesting (what the crop handler of the {@code CropFarmRegistry} reports as ready) and
 * fertilising. Priority: harvest, fertilise (needs bone meal), plant (needs a plantable item), till. Each action costs
 * a fifth of a work unit.
 *
 * <p>Drops of harvested crops go into the plantable inventory first, then the main inventory.
 *
 * <p><b>Staffed</b> (rework phase 3; the first converted worksite): the same four kinds of work are listed as {@link WorkTask}s per target position
 * (priority and conditions as above), and an NPC worker does one at a time: {@link #performTask} runs the per-target methods that the machine
 * loop also uses. The drops of a harvest go into the worker's pouch, which it empties into the plantable and main inventories. The crop
 * farm works natural terrain: each column is scanned at its surface within the vertical range (the topmost tillable or plantable block), and soil under
 * grass is tilled after the grass is cleared (never under anything else that is not air).
 */
public class CropFarmBlockEntity extends FarmWorksiteBlockEntity {
    private final Set<BlockPos> blocksToTill = new LinkedHashSet<>();
    private final Set<BlockPos> blocksToHarvest = new LinkedHashSet<>();
    private final Set<BlockPos> blocksToPlant = new LinkedHashSet<>();
    private final Set<BlockPos> blocksToFertilize = new LinkedHashSet<>();

    public CropFarmBlockEntity(BlockPos pos, BlockState state) {
        super(AwAutomationBase.CROP_FARM_BE.get(), pos, state);
    }

    @Override
    protected boolean isPlantable(ItemStack stack) {
        return CropFarmRegistry.getCrop(stack).isPlantable(stack);
    }

    /** 1.12.2: plants of the crop plant type, crops and stems. */
    @Override
    protected boolean isFarmable(Block block, BlockPos farmablePos) {
        return block instanceof CropBlock || block instanceof StemBlock;
    }

    @Override
    public void onBoundsAdjusted() {
        validateCollection(blocksToFertilize);
        validateCollection(blocksToHarvest);
        validateCollection(blocksToPlant);
        validateCollection(blocksToTill);
    }

    /** Rework phase 3: the surface of a column is the topmost block that can be tilled or planted on. */
    @Override
    protected boolean isGround(BlockState state, BlockPos pos) {
        return CropFarmRegistry.isTillable(state) || CropFarmRegistry.isSoil(state);
    }

    /** A free position for a plant: replaceable (air, grass, snow layers) and not under water. */
    private boolean canPlantInto(BlockPos pos) {
        return canReplace(pos) && level.getFluidState(pos).isEmpty();
    }

    /**
     * Whether the soil at the position can be tilled: the block above is air or vegetation that gets cleared (replaceable, not solid, no fluid).
     * Nothing else above the soil is allowed: the farm never tills under a block that is not air in that sense (a roof, a leaf block, a slab).
     */
    private boolean isOpenAbove(BlockPos soilPos) {
        BlockPos above = soilPos.above();
        BlockState state = level.getBlockState(above);
        return state.isAir() || (state.canBeReplaced() && !state.blocksMotion() && state.getFluidState().isEmpty());
    }

    @Override
    protected void scanBlockPosition(BlockPos position) {
        BlockState state = level.getBlockState(position);
        if (state.canBeReplaced() && state.getFluidState().isEmpty()) {
            BlockState stateDown = level.getBlockState(position.below());
            if (CropFarmRegistry.isTillable(stateDown)) {
                blocksToTill.add(position.below());
            } else if (CropFarmRegistry.isSoil(stateDown)) {
                blocksToPlant.add(position);
            }
        }

        if (state.isAir()) {
            return;
        }

        ICrop crop = CropFarmRegistry.getCrop(state);
        blocksToHarvest.addAll(crop.getPositionsToHarvest(level, position, state));

        if (crop.canBeFertilized(state, level, position)) {
            blocksToFertilize.add(position);
        }
    }

    @Override
    public WorkType getWorkType() {
        return WorkType.FARMING;
    }

    @Override
    public boolean onBlockClicked(Player player, @Nullable InteractionHand hand) {
        if (!player.level().isClientSide) {
            WorksiteMenus.open(player, WorksiteMenus.Kind.CROP_FARM, this);
        }
        return true;
    }

    private boolean hasToPlant() {
        return plantableCount > 0 && !blocksToPlant.isEmpty();
    }

    private boolean hasToFertilize() {
        return bonemealCount > 0 && !blocksToFertilize.isEmpty();
    }

    private static final IWorksiteAction PLANT_ACTION = WorksiteActions.fraction(5D);
    private static final IWorksiteAction FERTILIZE_ACTION = WorksiteActions.fraction(5D);
    private static final IWorksiteAction TILL_ACTION = WorksiteActions.fraction(5D);
    private static final IWorksiteAction HARVEST_ACTION = WorksiteActions.fraction(5D);

    @Override
    protected Optional<IWorksiteAction> getNextAction() {
        if (!blocksToHarvest.isEmpty()) {
            return Optional.of(HARVEST_ACTION);
        } else if (hasToFertilize()) {
            return Optional.of(FERTILIZE_ACTION);
        } else if (hasToPlant()) {
            return Optional.of(PLANT_ACTION);
        } else if (!blocksToTill.isEmpty()) {
            return Optional.of(TILL_ACTION);
        }

        return Optional.empty();
    }

    @Override
    protected boolean processAction(IWorksiteAction action) {
        if (action == TILL_ACTION) {
            return tryTill();
        } else if (action == HARVEST_ACTION) {
            return tryHarvest();
        } else if (action == PLANT_ACTION) {
            return tryPlant();
        } else if (action == FERTILIZE_ACTION) {
            return tryFertilize();
        }
        return false;
    }

    // ---- staffed work ----

    private static final int MAX_LISTED_PER_ACTION = 64;

    @Override
    protected boolean supportsStaffedWork() {
        return true;
    }

    /** Harvest, fertilise, plant, till: the priority of {@link #getNextAction}, one task per target position. */
    @Override
    protected List<WorkTask> listTasks() {
        List<WorkTask> tasks = new ArrayList<>();
        addTasks(tasks, blocksToHarvest, WorkAction.HARVEST, MAX_LISTED_PER_ACTION);
        if (hasToFertilize()) {
            addTasks(tasks, blocksToFertilize, WorkAction.FERTILIZE, Math.min(MAX_LISTED_PER_ACTION, bonemealCount));
        }
        if (hasToPlant()) {
            addTasks(tasks, blocksToPlant, WorkAction.PLANT, Math.min(MAX_LISTED_PER_ACTION, plantableCount));
        }
        addTasks(tasks, blocksToTill, WorkAction.TILL, MAX_LISTED_PER_ACTION);
        return tasks;
    }

    private void addTasks(List<WorkTask> tasks, Set<BlockPos> targets, WorkAction action, int limit) {
        int count = 0;
        for (BlockPos pos : targets) {
            if (count++ >= limit) {
                break;
            }
            // a crop is picked or cut quickly whatever its hardness: the fixed soft duration keeps wheat from being instant and stems from being slow
            tasks.add(new WorkTask(pos, action, action == WorkAction.HARVEST ? WorkTask.MIN_TICKS + 5 : WorkTask.SOFT_TICKS));
        }
    }

    @Override
    protected boolean performTask(WorkTask task, IItemHandler drops) {
        BlockPos pos = task.pos();
        return switch (task.action()) {
            case HARVEST -> harvestAt(pos, drops);
            case FERTILIZE -> fertilizeAt(pos);
            case PLANT -> plantAt(pos);
            case TILL -> tillAt(pos);
            default -> false;
        };
    }

    // ---- the per-target actions (shared by the machine loop and the workers) ----

    private boolean fertilizeAt(BlockPos position) {
        blocksToFertilize.remove(position);
        BlockState state = level.getBlockState(position);
        if (state.getBlock() instanceof BonemealableBlock) {
            boolean used = fertilize(position);
            // look at the block as it is now (the original judged the state from before the bone meal)
            BlockState after = level.getBlockState(position);
            if (after.getBlock() instanceof BonemealableBlock growable) {
                if (growable.isValidBonemealTarget(level, position, after)) {
                    blocksToFertilize.add(position);
                } else if (isFarmable(after.getBlock(), position)) {
                    blocksToHarvest.add(position);
                }
            }
            return used;
        }
        return false;
    }

    private boolean plantAt(BlockPos position) {
        blocksToPlant.remove(position);
        if (canPlantInto(position)) {
            for (int slot = 0; slot < plantableInventory.getSlots(); slot++) {
                ItemStack stack = plantableInventory.getStackInSlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                if (isPlantable(stack)) {
                    ItemStack clone = stack.copy();
                    if (tryPlace(clone, position, Direction.UP)) {
                        plantableInventory.extractItem(slot, 1, false);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean harvestAt(BlockPos position, IItemHandler drops) {
        blocksToHarvest.remove(position);
        BlockState state = level.getBlockState(position);
        ICrop crop = CropFarmRegistry.getCrop(state);
        return crop.harvest(level, state, position, getFortune(), drops);
    }

    private boolean tillAt(BlockPos position) {
        blocksToTill.remove(position);
        BlockState state = level.getBlockState(position);
        if (CropFarmRegistry.isTillable(state) && isOpenAbove(position)) {
            BlockPos above = position.above();
            BlockState vegetation = level.getBlockState(above);
            if (!vegetation.isAir()) {
                BlockTools.breakBlockNoDrops(level, above, vegetation); // grass and the like are cleared before the soil is turned
            }
            level.setBlock(position, CropFarmRegistry.getTilledState(state), Block.UPDATE_ALL);
            level.playSound(null, worldPosition, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            return true;
        }
        return false;
    }

    // ---- the machine loop: the first target of each queue, as before ----

    private boolean tryFertilize() {
        for (BlockPos position : new ArrayList<>(blocksToFertilize)) {
            if (level.getBlockState(position).getBlock() instanceof BonemealableBlock) {
                return fertilizeAt(position);
            }
            blocksToFertilize.remove(position);
        }
        return false;
    }

    private boolean tryPlant() {
        // the original skipped targets that could not take a plant until one could; the first one that is free is tried
        for (BlockPos position : new ArrayList<>(blocksToPlant)) {
            if (canPlantInto(position)) {
                return plantAt(position);
            }
            blocksToPlant.remove(position);
        }
        return false;
    }

    private boolean tryHarvest() {
        Iterator<BlockPos> it = blocksToHarvest.iterator();
        return it.hasNext() && harvestAt(it.next(), getDepositInventory());
    }

    private boolean tryTill() {
        for (BlockPos position : new ArrayList<>(blocksToTill)) {
            if (tillAt(position)) {
                return true;
            }
        }
        return false;
    }
}
