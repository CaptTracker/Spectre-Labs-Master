/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): the build job API that the builder NPC uses.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfare.api.IBoundedSite;
import io.github.capttracker.ancientwarfare.owner.IOwnable;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementApi;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementNeeds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The build job API (rework phase 9). The builder NPC of the next part works only through this class.
 *
 * <h2>The jobs</h2>
 * A job is a {@link BuildProject} that the owner approved and that is not done: {@link #jobs} lists them in queue order (builds and demolitions).
 * {@link #state} says where one stands ({@link JobState}). {@link #next} gives the job a builder should work on: one that is in progress and has its
 * materials, else the first one that is ready (approved, all materials in the storage). A job that has to wait for materials is not given out; its
 * missing materials are {@link #missing} and the planner already posted them as needs, so production and couriers fetch them.
 *
 * <h2>The steps</h2>
 * {@link #nextStep} gives the next {@link BuildStep} of a job (it skips steps whose block is already in the world). The builder walks to
 * {@link BuildStep#pos()} and calls {@link #perform} (which takes the material from the storage, sets the block, plays the sound and marks the step done),
 * or, if it wants to do the work itself, takes the material with {@link BuildMaterials#extract}, sets the block and calls {@link #markDone}. A bed and a
 * door are two steps (one for each half, in the right order); the design lists both. When the last step is done the job finishes by itself: the beds and
 * containers of the new building join the settlement at once, and a replaced building is queued for demolition.
 *
 * <h2>Materials</h2>
 * {@link #remainingMaterials} is what the job still needs, {@link #missing} what the storage lacks for it. A block with no material in the storage makes
 * {@link #perform} answer {@link PlaceResult#NO_MATERIAL}; the builder waits (the settlement shows "waiting for materials"). Taking a building down returns
 * the block items to the storage.
 *
 * <p>Skill: the building skill ({@code Skill.BUILDING}) has no XP source yet; the builder gives the XP.
 * Server thread only.
 */
public final class BuildJobs {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Ticks a posted material need lasts without being posted again. */
    private static final long NEED_TTL = 1500L;
    /** Ticks after which a need that has not changed is posted again. */
    private static final long NEED_REPOST = 600L;
    private static final int MAX_SKIP = 8192;

    /** Where a job stands. */
    public enum JobState {
        /** Waiting for the owner's approval. */
        PLANNED,
        /** Approved, but the storage lacks materials. */
        WAITING_MATERIALS,
        /** Approved, all materials are in the storage, no step done yet. */
        READY,
        /** At least one step done and the materials for the rest are in the storage. */
        IN_PROGRESS,
        DONE
    }

    /** What {@link #perform} did. */
    public enum PlaceResult {
        /** A block was placed. */
        PLACED,
        /** A block was removed (a cleared cell or a demolition step). */
        REMOVED,
        /** The block was already as wanted; the step is marked done. */
        SKIPPED,
        /** The storage lacks the material; nothing happened. */
        NO_MATERIAL,
        /** The chunk is not loaded; nothing happened. */
        UNLOADED,
        /** Something that cannot be broken is in the way; nothing happened. */
        BLOCKED,
        /** The step does not belong to the job (any more); nothing happened. */
        INVALID
    }

    private BuildJobs() {}

    // ---------------------------------------------------------------- the jobs

    /** The design of a project, or null if it is gone (a deleted blueprint or datapack). */
    @Nullable
    public static BuildingDesign designOf(BuildProject project) {
        return BuildingDesigns.get(project.getDesignId());
    }

    /** All projects of the settlement in queue order (planned, queued, building and finished). */
    public static List<BuildProject> all(Settlement settlement) {
        return settlement.getBuilding().getProjects();
    }

    /** The approved jobs that are not done, in queue order (builds and demolitions). */
    public static List<BuildProject> jobs(Settlement settlement) {
        List<BuildProject> list = new ArrayList<>();
        for (BuildProject project : settlement.getBuilding().getProjects()) {
            if (project.getStatus() == BuildProject.Status.QUEUED || project.getStatus() == BuildProject.Status.BUILDING) {
                list.add(project);
            }
        }
        return list;
    }

    /** The state of a job. Counts the storage, so call it on the server thread and not for every tick. */
    public static JobState state(ServerLevel level, Settlement settlement, BuildProject project) {
        switch (project.getStatus()) {
            case PLANNED:
                return JobState.PLANNED;
            case DONE:
                return JobState.DONE;
            default:
                break;
        }
        if (!missing(level, settlement, project).isEmpty()) {
            return JobState.WAITING_MATERIALS;
        }
        return project.getStatus() == BuildProject.Status.BUILDING ? JobState.IN_PROGRESS : JobState.READY;
    }

    /** The job a builder should work on now, or null: the first one in progress, else the first ready one. */
    @Nullable
    public static BuildProject next(ServerLevel level, Settlement settlement) {
        BuildProject ready = null;
        for (BuildProject project : jobs(settlement)) {
            JobState state = state(level, settlement, project);
            if (state == JobState.IN_PROGRESS) {
                return project;
            }
            if (state == JobState.READY && ready == null) {
                ready = project;
            }
        }
        return ready;
    }

    // ---------------------------------------------------------------- materials

    /** The materials the job still needs (the steps that are not done). */
    public static List<BuildMaterials.Req> remainingMaterials(BuildProject project) {
        BuildingDesign design = designOf(project);
        return design == null ? List.of() : BuildMaterials.remaining(project, design);
    }

    /** The materials the storage of the settlement lacks for the rest of the job (empty when it has them all). */
    public static List<BuildMaterials.Req> missing(ServerLevel level, Settlement settlement, BuildProject project) {
        List<BuildMaterials.Req> needed = remainingMaterials(project);
        return needed.isEmpty() ? List.of() : BuildMaterials.missing(needed, BuildMaterials.stock(level, settlement));
    }

    // ---------------------------------------------------------------- the steps

    /**
     * The next step of a job, or null when there is none (the job is finished, which this call notices). Steps whose block is already in the world are marked
     * done on the way.
     */
    @Nullable
    public static BuildStep nextStep(ServerLevel level, Settlement settlement, BuildProject project) {
        BuildingDesign design = designOf(project);
        if (design == null || project.getStatus() == BuildProject.Status.PLANNED || project.getStatus() == BuildProject.Status.DONE) {
            return null;
        }
        List<BuildStep> steps = project.steps(design);
        int skipped = 0;
        boolean changed = false;
        BuildStep result = null;
        for (int i = project.firstOpenStep(steps.size()); i >= 0 && skipped < MAX_SKIP; i = project.firstOpenStep(steps.size())) {
            BuildStep step = steps.get(i);
            if (level.isLoaded(step.pos()) && satisfied(level.getBlockState(step.pos()), step)) {
                project.markStepDone(i);
                changed = true;
                skipped++;
                continue;
            }
            result = step;
            break;
        }
        if (changed) {
            touched(project, settlement);
        }
        if (result == null && project.firstOpenStep(steps.size()) < 0) {
            finish(level, settlement, project, design);
        }
        return result;
    }

    /** True if the world already shows what the step wants (a placed block, an air cell, a removed block). */
    public static boolean isSatisfied(BlockState current, BuildStep step) {
        return satisfied(current, step);
    }

    private static boolean satisfied(BlockState current, BuildStep step) {
        return switch (step.kind()) {
            case PLACE, FILL -> current.equals(step.state());
            case CLEAR -> current.isAir();
            case REMOVE -> !current.equals(step.state());
        };
    }

    /** Marks a step done (for a builder that placed the block itself). Finishes the job when it was the last step. */
    public static void markDone(ServerLevel level, Settlement settlement, BuildProject project, BuildStep step) {
        if (project.markStepDone(step.index())) {
            touched(project, settlement);
        }
        BuildingDesign design = designOf(project);
        if (design != null && project.getStatus() != BuildProject.Status.DONE && project.firstOpenStep(project.steps(design).size()) < 0) {
            finish(level, settlement, project, design);
        }
    }

    private static void touched(BuildProject project, Settlement settlement) {
        if (project.getStatus() == BuildProject.Status.QUEUED && project.getDoneCount() > 0) {
            project.setStatus(BuildProject.Status.BUILDING);
        }
        settlement.getBuilding().changed();
    }

    /** Where the materials of a step come from and where the drops of a broken block go: the storage of the settlement, or the pouch of a builder. */
    public interface Carrier {
        /** Takes {@code count} items of the material (all or nothing). */
        boolean take(BuildMaterials.Req req, int count);

        /** Takes in an item that a broken or removed block gave. @return what did not fit (empty if all of it went in) */
        ItemStack store(ItemStack stack);
    }

    /** The settlement storage as the source and the sink (the default of {@link #perform(ServerLevel, Settlement, BuildProject, BuildStep)}). */
    public static Carrier storage(ServerLevel level, Settlement settlement) {
        return new Carrier() {
            @Override
            public boolean take(BuildMaterials.Req req, int count) {
                return BuildMaterials.extract(level, settlement, req, count);
            }

            @Override
            public ItemStack store(ItemStack stack) {
                return BuildMaterials.insert(level, settlement, stack);
            }
        };
    }

    /**
     * Does a step: takes the material from the storage (or gives the block back to it when taking a building down), changes the block, plays the sound
     * and marks the step done.
     */
    public static PlaceResult perform(ServerLevel level, Settlement settlement, BuildProject project, BuildStep step) {
        return perform(level, settlement, project, step, storage(level, settlement));
    }

    /**
     * As {@link #perform(ServerLevel, Settlement, BuildProject, BuildStep)}, but the material comes from the carrier and what the broken block gave goes
     * into it (the pouch of a builder). Taking down a chest, barrel or bed also unlinks it from the settlement, and the contents of a container go into the carrier.
     */
    public static PlaceResult perform(ServerLevel level, Settlement settlement, BuildProject project, BuildStep step, Carrier carrier) {
        BuildingDesign design = designOf(project);
        if (design == null || project.getStatus() == BuildProject.Status.PLANNED || project.getStatus() == BuildProject.Status.DONE) {
            return PlaceResult.INVALID;
        }
        List<BuildStep> steps = project.steps(design);
        if (step.index() < 0 || step.index() >= steps.size() || !steps.get(step.index()).pos().equals(step.pos()) || project.isStepDone(step.index())) {
            return PlaceResult.INVALID;
        }
        BlockPos pos = step.pos();
        if (!level.isLoaded(pos)) {
            return PlaceResult.UNLOADED;
        }
        BlockState current = level.getBlockState(pos);
        PlaceResult result;
        if (satisfied(current, step)) {
            result = PlaceResult.SKIPPED;
        } else {
            switch (step.kind()) {
                case REMOVE -> {
                    // unlink first, so the contents of a container are not put back into the container that is being taken down
                    settlement.removeStorage(pos);
                    settlement.removeBed(pos);
                    drainContents(level, pos, carrier);
                    if (!wasPaid(settlement, project, pos, current)) {
                        // the builders never paid for this block (it was already there): it breaks like mined terrain, no design refund
                        breakInto(level, carrier, pos, current);
                        result = PlaceResult.REMOVED;
                        break;
                    }
                    BlockPos partner = costFreeHalfPartner(current, pos);
                    BlockState partnerBefore = partner == null ? null : level.getBlockState(partner);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    level.levelEvent(2001, pos, Block.getId(current));
                    giveBack(level, carrier, pos, step.state());
                    if (partnerBefore != null && partnerBefore.is(current.getBlock()) && level.getBlockState(partner).isAir()) {
                        // the free half of a door, bed or tall plant came first and took the other half with it: the item of the pair is given back
                        // here, because the step of the other half will find only air
                        settlement.removeBed(partner);
                        giveBack(level, carrier, pos, partnerBefore);
                    }
                    result = PlaceResult.REMOVED;
                }
                case CLEAR -> {
                    if (current.getDestroySpeed(level, pos) < 0) {
                        return PlaceResult.BLOCKED;
                    }
                    breakInto(level, carrier, pos, current);
                    result = PlaceResult.REMOVED;
                }
                default -> {
                    BuildMaterials.Req req = step.kind() == BuildStep.Kind.FILL && !SettlementConfig.terrainFillCostsMaterial ? null : BuildMaterials.reqFor(step.state());
                    if (!current.isAir() && !current.canBeReplaced() && current.getDestroySpeed(level, pos) < 0) {
                        return PlaceResult.BLOCKED;
                    }
                    if (req != null && !carrier.take(req, req.count())) {
                        return PlaceResult.NO_MATERIAL;
                    }
                    if (!current.isAir() && !current.canBeReplaced()) {
                        breakInto(level, carrier, pos, current);
                    }
                    level.setBlock(pos, step.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    if (req != null) {
                        project.markPaid(step.index());
                    }
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof IOwnable ownable) {
                        ownable.setOwner(settlement.getOwner());
                    }
                    level.playSound(null, pos, step.state().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.8F, 0.9F);
                    result = PlaceResult.PLACED;
                }
            }
        }
        markDone(level, settlement, project, step);
        return result;
    }

    /** Whether the build a demolition takes down paid for its block at this position (the free half of a door or bed counts with its other half). */
    private static boolean wasPaid(Settlement settlement, BuildProject demolition, BlockPos pos, BlockState current) {
        UUID target = demolition.getTarget();
        BuildProject old = target == null ? null : settlement.getBuilding().project(target).orElse(null);
        BuildingDesign design = old == null ? null : designOf(old);
        if (old == null || design == null) {
            return false;
        }
        if (old.isPaidAt(pos, design)) {
            return true;
        }
        BlockPos partner = costFreeHalfPartner(current, pos);
        return partner != null && old.isPaidAt(partner, design);
    }

    /** For the half of a door, tall plant or bed that costs nothing (upper half, head): the position of the other half, else null. */
    @Nullable
    private static BlockPos costFreeHalfPartner(BlockState state, BlockPos pos) {
        Block block = state.getBlock();
        if (BuildMaterials.countFor(state) != 0) {
            return null;
        }
        if (block instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
            return pos.relative(state.getValue(BedBlock.FACING).getOpposite());
        }
        return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) ? pos.below() : null;
    }

    /** Breaks a block and hands what it drops to the carrier (the rest lies on the ground). */
    private static void breakInto(ServerLevel level, Carrier carrier, BlockPos pos, BlockState state) {
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos));
        level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
        level.levelEvent(2001, pos, Block.getId(state));
        for (ItemStack drop : drops) {
            ItemStack rest = carrier.store(drop);
            if (!rest.isEmpty()) {
                Block.popResource(level, pos, rest);
            }
        }
    }

    /** The contents of a container that is taken down go to the carrier (they would be dropped on the ground otherwise). */
    private static void drainContents(ServerLevel level, BlockPos pos, Carrier carrier) {
        if (level.getBlockEntity(pos) instanceof Container container) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.removeItemNoUpdate(slot);
                if (!stack.isEmpty()) {
                    ItemStack rest = carrier.store(stack);
                    if (!rest.isEmpty()) {
                        Block.popResource(level, pos, rest);
                    }
                }
            }
        }
    }

    /** The item of a removed block goes to the carrier (what does not fit lies on the ground). */
    private static void giveBack(ServerLevel level, Carrier carrier, BlockPos pos, BlockState state) {
        BuildMaterials.Req req = BuildMaterials.reqFor(state);
        if (req != null) {
            ItemStack rest = carrier.store(new ItemStack(req.item(), req.count()));
            if (!rest.isEmpty()) {
                Block.popResource(level, pos, rest);
            }
        }
    }

    // ---------------------------------------------------------------- finishing

    /** The job is finished: the building counts, a replaced building is queued for demolition, a finished demolition removes its record. */
    private static void finish(ServerLevel level, Settlement settlement, BuildProject project, BuildingDesign design) {
        if (project.getStatus() == BuildProject.Status.DONE) {
            return;
        }
        project.setStatus(BuildProject.Status.DONE);
        SettlementBuilding building = settlement.getBuilding();
        if (project.getKind() == BuildProject.Kind.BUILD) {
            register(settlement, project, design);
            applyWorkArea(level, project, design);
            UUID target = project.getTarget();
            if (target != null) {
                BuildProject old = building.project(target).orElse(null);
                if (old != null && old.getKind() == BuildProject.Kind.BUILD && old.isDone() && !hasDemolition(building, old.getId())) {
                    BuildProject demolition = new BuildProject(UUID.randomUUID(), old.getDesignId(), old.getPlotId(), old.getOrigin(), old.getFacing(), old.getSize(),
                            BuildProject.Kind.DEMOLISH, BuildProject.Status.QUEUED, "upgrade", old.getId(), level.getGameTime(), List.of());
                    building.addProject(demolition);
                }
            }
        } else {
            UUID target = project.getTarget();
            if (target != null) {
                building.removeProject(target);
            }
            building.removeProject(project.getId());
        }
        building.changed();
        LOGGER.debug("Settlement {}: build {} of {} finished", settlement.getName(), project.getId(), project.getDesignId());
    }

    private static boolean hasDemolition(SettlementBuilding building, UUID target) {
        for (BuildProject project : building.getProjects()) {
            if (project.getKind() == BuildProject.Kind.DEMOLISH && target.equals(project.getTarget())) {
                return true;
            }
        }
        return false;
    }

    /**
     * A design whose worksite has a work area (a farmhouse with its garden, a lumber camp with its plot, a mine entrance) gets that area set on the worksite
     * block when the build is finished: the area is turned with the design and the block entity is told like the bounds screen does. A worksite block that is
     * not in the world (broken, chunk not loaded) is left alone: the owner can still set the area by hand.
     */
    private static void applyWorkArea(ServerLevel level, BuildProject project, BuildingDesign design) {
        BlockPos[] area = design.workArea(project.getOrigin(), project.getFacing());
        BlockPos site = design.worksitePos(project.getOrigin(), project.getFacing());
        if (area == null || site == null || !level.isLoaded(site)) {
            return;
        }
        if (level.getBlockEntity(site) instanceof IBoundedSite bounded) {
            bounded.setBounds(area[0], area[1]);
            bounded.onBoundsAdjusted();
            bounded.onPostBoundsAdjusted();
            LOGGER.debug("Work area {} to {} set on the worksite at {}", area[0], area[1], site);
        } else {
            LOGGER.warn("The worksite of {} at {} has no work area to set (no bounded worksite block there)", design.getId(), site);
        }
    }

    /** The beds and containers of the new building join the settlement at once (worksites are picked up by the job board). */
    private static void register(Settlement settlement, BuildProject project, BuildingDesign design) {
        if (project.isRegistered()) {
            return;
        }
        for (BuildingDesign.Placement placement : design.blocks(project.getOrigin(), project.getFacing())) {
            BlockState state = placement.state();
            if (state.isAir()) {
                continue;
            }
            if (state.is(BlockTags.BEDS) && state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.FOOT) {
                settlement.addBed(placement.pos());
            } else if (state.getBlock() instanceof ChestBlock || state.getBlock() instanceof BarrelBlock || state.getBlock() instanceof ShulkerBoxBlock) {
                settlement.addStorage(placement.pos());
            }
        }
        project.setRegistered(true);
    }

    // ---------------------------------------------------------------- upkeep

    /**
     * Every few seconds: drops open projects whose design is gone, finishes jobs whose steps are all done and posts the materials of the first
     * {@code active_builds} jobs of the queue as settlement needs (production sites and couriers then supply them).
     */
    public static void maintain(ServerLevel level, Settlement settlement) {
        SettlementBuilding building = settlement.getBuilding();
        if (building.getProjects().isEmpty()) {
            clearPosted(settlement, building, Map.of());
            return;
        }
        for (BuildProject project : new ArrayList<>(building.getProjects())) {
            if (!project.isOpen()) {
                continue;
            }
            if (project.getPlotId() != null && building.plot(project.getPlotId()).isEmpty()) {
                // rework phase 9, builders: the plot was removed; the job is cancelled and what was built stays
                LOGGER.info("Settlement {}: the plot of the build of {} is gone, the build is cancelled (placed blocks stay)", settlement.getName(), project.getDesignId());
                building.removeProject(project.getId());
                continue;
            }
            BuildingDesign design = designOf(project);
            if (design == null) {
                LOGGER.warn("Settlement {}: the design {} of a build is gone, the build is cancelled", settlement.getName(), project.getDesignId());
                building.removeProject(project.getId());
                continue;
            }
            List<BuildStep> steps = project.steps(design);
            if (project.getStatus() != BuildProject.Status.PLANNED && project.firstOpenStep(steps.size()) < 0) {
                finish(level, settlement, project, design);
            }
        }
        postNeeds(level, settlement, building);
    }

    private static void postNeeds(ServerLevel level, Settlement settlement, SettlementBuilding building) {
        Map<String, BuildMaterials.Req> wanted = new LinkedHashMap<>();
        int active = 0;
        for (BuildProject project : jobs(settlement)) {
            if (active >= SettlementConfig.activeBuilds) {
                break;
            }
            if (project.getKind() != BuildProject.Kind.BUILD) {
                continue;
            }
            BuildingDesign design = designOf(project);
            if (design == null) {
                continue;
            }
            active++;
            for (BuildMaterials.Req req : BuildMaterials.remaining(project, design)) {
                wanted.merge(req.key(), req, (a, b) -> new BuildMaterials.Req(a.key(), a.item(), a.anyPlanks(), a.count() + b.count()));
            }
        }
        long now = level.getGameTime();
        for (BuildMaterials.Req req : wanted.values()) {
            String id = "build." + req.key().replace(':', '.');
            long[] last = building.postedNeeds.get(id);
            if (last == null || req.count() > last[0] || now - last[1] >= NEED_REPOST) { // a smaller need is picked up at the next repost
                SettlementApi.postNeed(level, settlement, id, req.label(), req.matcher(), req.count(), SettlementNeeds.PRIORITY_MATERIAL + 5, NEED_TTL);
                building.postedNeeds.put(id, new long[]{req.count(), now});
            }
        }
        clearPosted(settlement, building, wanted);
    }

    private static void clearPosted(Settlement settlement, SettlementBuilding building, Map<String, BuildMaterials.Req> keep) {
        if (building.postedNeeds.isEmpty()) {
            return;
        }
        Map<String, Boolean> keepIds = new HashMap<>();
        for (String key : keep.keySet()) {
            keepIds.put("build." + key.replace(':', '.'), true);
        }
        building.postedNeeds.keySet().removeIf(id -> {
            if (keepIds.containsKey(id)) {
                return false;
            }
            SettlementApi.clearNeed(settlement, id);
            return true;
        });
    }
}
