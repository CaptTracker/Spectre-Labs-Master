/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): a builder doing the steps of a build of its settlement.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfare.api.IWorkSite.WorkType;
import io.github.capttracker.ancientwarfare.api.WorkAction;
import io.github.capttracker.ancientwarfare.item.HammerItem;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.ai.path.NpcRouteAssist;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcWorker;
import io.github.capttracker.ancientwarfarenpc.settlement.JobBoard;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementApi;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.settlement.building.BuildJobs;
import io.github.capttracker.ancientwarfarenpc.settlement.building.BuildMaterials;
import io.github.capttracker.ancientwarfarenpc.settlement.building.BuildProject;
import io.github.capttracker.ancientwarfarenpc.settlement.building.BuildSafety;
import io.github.capttracker.ancientwarfarenpc.settlement.building.BuildStep;
import io.github.capttracker.ancientwarfarenpc.settlement.building.BuildingDesign;
import io.github.capttracker.ancientwarfarenpc.skill.Skill;
import io.github.capttracker.ancientwarfarenpc.skill.SkillEffects;
import io.github.capttracker.ancientwarfarenpc.trait.TraitEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The work of one builder on a build of the queue ({@link BuildProject}), driven tick by tick by {@link NpcAIBuilderWork}. It works through {@link BuildJobs}.
 *
 * <p><b>Order.</b> First the site is cleared: every cell the design wants as air that still holds a block (earth that is cut away, bushes, trees) is
 * broken, with the tool that fits the block (the tool switching of the workers), and what drops goes into the pouch. Then the ground is levelled (the fill
 * steps) and the design is placed bottom layer up. A block whose material is missing, or that has nothing to hang on yet (a torch before its wall), is left
 * for later and the builder goes on with another block it can place. Several builders on one build claim the steps one by one at the job board
 * ({@link JobBoard#claimStep}), so no block is placed twice. What a builder may break is decided by {@link BuildSafety}.
 *
 * <p><b>Materials.</b> Only from the storage of the settlement (owner decision: no free building). The builder walks to a linked storage or the hall that
 * holds what the next blocks need, takes it into its pouch ({@code Carrier}: the pouch is the source of {@link BuildJobs#perform}) and walks back to place
 * it. What a broken block drops goes into the pouch too, and when it is nearly full of things the build does not need (and when the builder leaves the job)
 * the builder takes them to a storage. If the storage lacks a material the block waits (the builder says so in its thoughts and the job shows it in the
 * Building tab); after {@code builder_wait_release_ticks} of having nothing at all to do, the builder lets go of the job for a while and looks for another.
 *
 * <p><b>Reaching.</b> A builder places a block within 4.5 blocks of its eyes. It walks to it like the other workers (vanilla navigation, then the routes over
 * climbable blocks of {@link NpcRouteAssist}); there is no flying. Where that finds no way (a roof, a high wall) and {@code builder_scaffolding} is on it
 * builds a column of vanilla scaffolding next to the building up to the layer it works on, climbs it, and takes the scaffolding down again from the top
 * when it has nothing more to do with it. The scaffolding blocks are kept in the list {@link NpcWorker#scaffolds} (saved), so a builder that is unloaded
 * still takes them down later.
 */
public final class BuilderWork {
    private static final double REACH = NpcRouteAssist.WORK_REACH;
    /** Free pouch slots a builder keeps for drops. */
    private static final int RESERVE = 2;
    private static final int SCAN_LIMIT = 4000;
    private static final long STEP_RETRY = 200L;
    private static final int PICK_INTERVAL = 40;
    private static final int KEEP_INTERVAL = 40;
    private static final int STUCK_TICKS = 100;
    private static final int NO_PATH_TICKS = 10;
    private static final double MAX_TOOL_SPEED = 8.0D;
    private static final int MAX_FETCH_ITEMS = 512;

    private enum Access { READY, MOVING, NO_WAY }

    /** What a look at the open steps found. */
    private static final class Pick {
        @Nullable
        BuildStep step;
        final Map<String, BuildMaterials.Req> fetch = new LinkedHashMap<>();
        final Map<String, BuildMaterials.Req> missing = new LinkedHashMap<>();
        boolean anyOpen;
        boolean finished;
    }

    /** A scaffolding column to put up: its place, the first cell above the ground and the last cell (where the builder stands). */
    private record Column(int x, int z, int baseY, int topY) {}

    private final NpcWorker npc;
    private final StaffedWork.Mover mover;
    private final Walk workWalk = new Walk();
    private final Walk siteWalk = new Walk();
    private final Walk scaffoldWalk = new Walk();

    @Nullable
    private UUID projectId;
    @Nullable
    private BuildStep step;
    private double progress;
    private double target;
    private double breakPortion;
    private int swingTimer;
    private int idlePause;
    private boolean started;
    private int crackStage = -1;
    private long nextPick;
    private long waitingSince;
    private int keepTimer;
    private final Map<Integer, Long> cooldown = new HashMap<>();
    private boolean moreClears = true;
    private long lastSkipThought;

    @Nullable
    private BlockPos fetchPos;
    @Nullable
    private Map<String, BuildMaterials.Req> fetchWanted;
    private long fetchStarted;
    private long depositRetryAt;
    private long cleanupRetryAt;

    private List<ItemStack> stock = List.of();
    private long stockAt = -1000L;
    private List<BuildMaterials.Req> remaining = List.of();
    private long remainingAt = -1000L;

    @Nullable
    private Column column;
    @Nullable
    private BlockPos columnTarget;
    private int columnTries;
    private boolean columnComplete;
    private long scaffoldNext;
    private int dismantleFails;
    private boolean dismantling;
    @Nullable
    private BlockPos asideTo;
    private long asideUntil;

    private final BuildJobs.Carrier carrier = new BuildJobs.Carrier() {
        @Override
        public boolean take(BuildMaterials.Req req, int count) {
            return takeFromPouch(req, count);
        }

        @Override
        public ItemStack store(ItemStack stack) {
            ItemStack rest = ItemHandlerHelper.insertItem(npc.getPouch(), stack, false);
            if (!rest.isEmpty() && npc.level() instanceof ServerLevel level) {
                Settlement settlement = npc.getSettlement().orElse(null);
                if (settlement != null) {
                    rest = BuildMaterials.insert(level, settlement, rest); // the pouch is full: into the storage
                }
            }
            return rest;
        }
    };

    public BuilderWork(NpcWorker npc, StaffedWork.Mover mover) {
        this.npc = npc;
        this.mover = mover;
    }

    // ---------------------------------------------------------------- the tick of a job

    /** One tick of work on a build that the builder has claimed. */
    public void tick(ServerLevel level, Settlement settlement, BuildProject project) {
        UUID id = project.getId();
        if (!id.equals(projectId)) {
            reset();
            projectId = id;
            moreClears = true;
            cooldown.clear();
            waitingSince = 0L;
            nextPick = 0L;
        }
        BuildingDesign design = BuildJobs.designOf(project);
        if (design == null) {
            return;
        }
        long now = level.getGameTime();
        JobBoard board = settlement.getJobBoard();
        if (dismantling && dismantle(level, settlement)) {
            return;
        }
        dismantling = false;
        if (step != null) {
            if (project.isStepDone(step.index()) || (++keepTimer >= KEEP_INTERVAL && !renewClaim(board, project, now))) {
                dropStep(board, project);
            }
        }
        if (step == null && fetchPos != null && fetchWanted != null) {
            if (now - fetchStarted > 600L) {
                fetchPos = null;
                fetchWanted = null;
                nextPick = now + PICK_INTERVAL;
            } else {
                fetch(level, settlement, fetchWanted);
                return;
            }
        }
        if (step == null && depositJunkIfNeeded(level, settlement, project)) {
            return;
        }
        if (step == null) {
            if (now < nextPick) {
                idleTick(level, settlement, project);
                return;
            }
            Pick pick = pick(level, settlement, project, design, now);
            if (project.isDone() || pick.finished) {
                BuildJobs.nextStep(level, settlement, project); // finishes the job when every step is done
                return;
            }
            if (pick.step != null) {
                BuildStep chosen = pick.step;
                if (!board.claimStep(id, chosen.index(), npc.getUUID(), now)) {
                    nextPick = now + 5;
                    return;
                }
                step = chosen;
                keepTimer = 0;
                progress = 0.0D;
                started = false;
                waitingSince = 0L;
                workWalk.reset();
            } else if (!pick.fetch.isEmpty()) {
                fetchPos = nearestStorageWith(level, settlement, pick.fetch);
                fetchStarted = now;
                if (fetchPos != null) {
                    waitingSince = 0L;
                    fetchWanted = pick.fetch;
                    fetch(level, settlement, pick.fetch);
                    return;
                }
                wait(level, settlement, project, pick, now); // the stock changed since the look: wait and look again
                return;
            } else {
                wait(level, settlement, project, pick, now);
                return;
            }
        }
        BuildStep current = step;
        if (current != null) {
            work(level, settlement, project, board, current, now);
        }
    }

    /** Between two looks at the steps: the builder stays where it is (or takes a leftover to the storage). */
    private void idleTick(ServerLevel level, Settlement settlement, BuildProject project) {
        npc.getNavigation().stop();
        npc.removeAITask(NpcAI.TASK_MOVE);
        if (waitingSince > 0L) {
            Pick none = new Pick();
            setWaitDoing(level, settlement, project, none);
        }
    }

    // ---------------------------------------------------------------- picking a step

    /**
     * Looks at the open steps in order and finds what the builder can do now: a cell to clear (these first, over the whole build), else the first block
     * that it has the material for and that can stand where it goes. If there is none, it finds what to take from the storage for the next blocks, and what
     * the storage lacks.
     */
    private Pick pick(ServerLevel level, Settlement settlement, BuildProject project, BuildingDesign design, long now) {
        Pick pick = new Pick();
        JobBoard board = settlement.getJobBoard();
        UUID me = npc.getUUID();
        List<BuildStep> steps = project.steps(design);
        int first = project.firstOpenStep(steps.size());
        if (first < 0) {
            pick.finished = true;
            return pick;
        }
        BuildStep firstClear = null;
        BuildStep firstPlace = null;
        boolean clearsLeft = false;
        boolean complete = true;
        Map<String, Integer> planned = new HashMap<>();
        Map<String, Integer> usedFromPouch = new HashMap<>();
        Map<String, Integer> storeMemo = new HashMap<>();
        int freeSlots = Math.max(0, npc.pouchFreeSlots() - RESERVE);
        int plannedItems = 0;
        int seen = 0;
        for (int i = first; i < steps.size(); i++) {
            if (project.isStepDone(i)) {
                continue;
            }
            if (seen++ >= SCAN_LIMIT) {
                complete = false;
                break;
            }
            if ((firstPlace != null && !moreClears) || firstClear != null) {
                if (firstClear != null) {
                    complete = false;
                }
                break; // a cell to clear comes first; with nothing more to clear the first block that can be placed will do
            }
            BuildStep candidate = steps.get(i);
            BlockPos pos = candidate.pos();
            pick.anyOpen = true;
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState current = level.getBlockState(pos);
            if (BuildJobs.isSatisfied(current, candidate)) {
                BuildJobs.markDone(level, settlement, project, candidate);
                if (project.isDone()) {
                    pick.finished = true;
                    return pick;
                }
                continue;
            }
            if (candidate.kind() == BuildStep.Kind.CLEAR && current.getBlock() instanceof LiquidBlock) {
                BuildJobs.markDone(level, settlement, project, candidate); // water stays (a cell cannot be cleared of it)
                continue;
            }
            boolean breaksSomething = !current.isAir() && !current.canBeReplaced();
            if (candidate.kind() != BuildStep.Kind.REMOVE && (!BuildSafety.inPlot(settlement, project, pos)
                    || (breaksSomething && !BuildSafety.mayBreak(level, settlement, project, pos, current)))) {
                skipStep(level, settlement, project, candidate, current, now);
                if (project.isDone()) {
                    pick.finished = true;
                    return pick;
                }
                continue;
            }
            if (candidate.kind() == BuildStep.Kind.CLEAR) {
                clearsLeft = true;
            }
            Long cool = cooldown.get(i);
            if ((cool != null && cool > now) || board.isStepClaimedByOther(project.getId(), i, me, now)) {
                continue;
            }
            switch (candidate.kind()) {
                case CLEAR -> {
                    if (firstClear == null) {
                        firstClear = candidate;
                    }
                }
                case REMOVE -> {
                    if (firstPlace == null) {
                        firstPlace = candidate;
                    }
                }
                default -> {
                    BuildMaterials.Req req = candidate.kind() == BuildStep.Kind.FILL && !SettlementConfig.terrainFillCostsMaterial ? null : BuildMaterials.reqFor(candidate.state());
                    if (req != null) {
                        int inPouch = pouchCount(req) - usedFromPouch.getOrDefault(req.key(), 0);
                        if (inPouch < req.count()) {
                            if (firstPlace == null) {
                                int inStore = storeMemo.computeIfAbsent(req.key(), key -> BuildMaterials.count(stock(level, settlement, now), req)) - planned.getOrDefault(req.key(), 0);
                                if (inStore >= req.count()) {
                                    int total = planned.getOrDefault(req.key(), 0) + req.count();
                                    int slots = slotsFor(planned, req, total);
                                    if (slots <= freeSlots && plannedItems + req.count() <= MAX_FETCH_ITEMS) {
                                        planned.put(req.key(), total);
                                        plannedItems += req.count();
                                        pick.fetch.merge(req.key(), req, (a, b) -> new BuildMaterials.Req(a.key(), a.item(), a.anyPlanks(), a.count() + b.count()));
                                    }
                                } else {
                                    pick.missing.putIfAbsent(req.key(), new BuildMaterials.Req(req.key(), req.item(), req.anyPlanks(), req.count()));
                                }
                            }
                            continue;
                        }
                        usedFromPouch.merge(req.key(), req.count(), Integer::sum);
                    }
                    if (candidate.state().canSurvive(level, pos) && firstPlace == null) {
                        firstPlace = candidate;
                    }
                }
            }
        }
        if (complete) {
            moreClears = clearsLeft;
        }
        pick.step = firstClear != null ? firstClear : firstPlace;
        if (pick.step != null) {
            pick.fetch.clear();
        }
        return pick;
    }

    /** The slots the pouch needs for the planned amounts if the amount of one material is {@code total}. */
    private static int slotsFor(Map<String, Integer> planned, BuildMaterials.Req req, int total) {
        int slots = 0;
        for (Map.Entry<String, Integer> entry : planned.entrySet()) {
            if (!entry.getKey().equals(req.key())) {
                slots += (entry.getValue() + 63) / 64;
            }
        }
        return slots + (total + Math.max(1, req.item().getDefaultMaxStackSize()) - 1) / Math.max(1, req.item().getDefaultMaxStackSize());
    }

    /** A step that cannot be done by the rules is left out for good (the building is a little different there). */
    private void skipStep(ServerLevel level, Settlement settlement, BuildProject project, BuildStep skipped, BlockState current, long now) {
        BuildJobs.markDone(level, settlement, project, skipped);
        if (now - lastSkipThought > 600L) {
            lastSkipThought = now;
            npc.think("build_skip", current.isAir() ? skipped.state().getBlock().getName() : current.getBlock().getName());
        }
    }

    // ---------------------------------------------------------------- waiting

    private void wait(ServerLevel level, Settlement settlement, BuildProject project, Pick pick, long now) {
        nextPick = now + PICK_INTERVAL;
        npc.getNavigation().stop();
        npc.removeAITask(NpcAI.TASK_MOVE);
        if (waitingSince == 0L) {
            waitingSince = now;
            if (!pick.missing.isEmpty()) {
                npc.think("build_wait", missingText(pick.missing.values()));
            }
        }
        setWaitDoing(level, settlement, project, pick);
        if (!npc.scaffolds.isEmpty()) {
            dismantling = true; // nothing to do at the moment: take the scaffolding down
        }
        if (now - waitingSince > SettlementConfig.builderWaitReleaseTicks) {
            // nothing to do for a long time: give the job up for a while, the board gives it to nobody until then
            JobBoard board = settlement.getJobBoard();
            board.blockBuild(project.getId(), now + 1200L);
            board.releaseBuild(npc.getUUID());
            npc.buildJobId = null;
            waitingSince = 0L;
            npc.think("build_wait_over");
        }
    }

    private void setWaitDoing(ServerLevel level, Settlement settlement, BuildProject project, Pick pick) {
        List<BuildMaterials.Req> missing = new ArrayList<>(pick.missing.values());
        if (missing.isEmpty()) {
            missing = BuildJobs.missing(level, settlement, project);
        }
        if (missing.isEmpty()) {
            npc.setDoing("work.build_idle", project.getOrigin(), designName(project));
        } else {
            npc.setDoing("work.build_wait", project.getOrigin(), missingText(missing));
        }
    }

    private static Component missingText(Iterable<BuildMaterials.Req> reqs) {
        MutableComponent text = Component.empty();
        int shown = 0;
        for (BuildMaterials.Req req : reqs) {
            if (shown == 3) {
                text.append(" ...");
                break;
            }
            if (shown > 0) {
                text.append(", ");
            }
            text.append(Component.literal(req.count() + " ")).append(Component.translatable(req.label()));
            shown++;
        }
        return text;
    }

    private static Component designName(BuildProject project) {
        BuildingDesign design = BuildJobs.designOf(project);
        return design == null ? Component.translatable("npc.thought.building.generic") : Component.literal(design.getName());
    }

    // ---------------------------------------------------------------- doing a step

    private void work(ServerLevel level, Settlement settlement, BuildProject project, JobBoard board, BuildStep current, long now) {
        BlockPos pos = current.pos();
        if (!level.isLoaded(pos)) {
            dropStep(board, project);
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (BuildJobs.isSatisfied(state, current)) {
            BuildJobs.markDone(level, settlement, project, current);
            finishStep(board, project, true);
            return;
        }
        if (current.kind() != BuildStep.Kind.REMOVE && !state.isAir() && !state.canBeReplaced()
                && !BuildSafety.mayBreak(level, settlement, project, pos, state)) {
            // the block changed since the step was picked (somebody built or put a chest there): the rules are checked again before anything is broken
            skipStep(level, settlement, project, current, state, now);
            finishStep(board, project, true);
            return;
        }
        boolean places = current.places();
        boolean breaking = current.kind() == BuildStep.Kind.CLEAR || current.kind() == BuildStep.Kind.REMOVE || (places && !state.isAir() && !state.canBeReplaced());
        npc.setDoing(doingKey(current), pos, current.kind() == BuildStep.Kind.CLEAR || current.kind() == BuildStep.Kind.REMOVE
                ? state.getBlock().getName() : current.state().getBlock().getName());
        Access access = access(level, settlement, project, pos);
        if (access == Access.MOVING) {
            return;
        }
        if (access == Access.NO_WAY) {
            cooldown.put(current.index(), now + STEP_RETRY);
            npc.think("no_reach");
            dropStep(board, project);
            return;
        }
        npc.getNavigation().stop();
        npc.removeAITask(NpcAI.TASK_MOVE);
        Vec3 center = Vec3.atCenterOf(pos);
        npc.getLookControl().setLookAt(center.x, center.y, center.z);
        if (places && !current.state().getCollisionShape(level, pos).isEmpty()) {
            AABB cell = new AABB(pos);
            if (cell.intersects(npc.getBoundingBox())) {
                if (stepAside(level, pos, now)) {
                    return;
                }
                cooldown.put(current.index(), now + STEP_RETRY);
                dropStep(board, project);
                return;
            }
            if (!level.isUnobstructed(current.state(), pos, net.minecraft.world.phys.shapes.CollisionContext.empty())) {
                cooldown.put(current.index(), now + 60L); // somebody (or something) stands in the cell
                dropStep(board, project);
                return;
            }
        }
        if (!started) {
            started = true;
            double hardness = breaking ? Math.max(0.0F, state.getDestroySpeed(level, pos)) : 0.0D;
            breakPortion = breaking ? Math.max(4.0D, hardness * SettlementConfig.builderBreakTicksPerHardness) : 0.0D;
            target = breakPortion + (places ? SettlementConfig.builderPlaceTicks : 0);
            progress = 0.0D;
            swingTimer = Integer.MAX_VALUE / 2;
            if (TraitEffects.lazyPause(npc)) {
                idlePause = 40 + npc.getRandom().nextInt(60); // a lazy builder idles a while before the block
            }
        }
        if (breaking) {
            chooseBreakTool(state);
        } else if (npc.tickCount % 10 == 0) {
            npc.equipRoleTool(); // placing: the hammer (or the tool of the role) again
        }
        if (idlePause > 0) {
            idlePause--;
            return;
        }
        boolean inBreakPhase = breaking && progress < breakPortion;
        if (npc.advanceWorkTick()) {
            progress += rate(inBreakPhase, state);
            if (npc.takeBonusWorkTick()) {
                progress += rate(inBreakPhase, state);
            }
        }
        if (swingTimer++ >= SettlementConfig.swingIntervalTicks) {
            swingTimer = 1;
            npc.swing(InteractionHand.MAIN_HAND);
            swingEffects(level, state, current, breaking && progress < breakPortion);
        }
        if (breaking && progress < breakPortion) {
            int stage = (int) Math.min(9, progress * 10.0D / breakPortion);
            if (stage != crackStage) {
                crackStage = stage;
                npc.crackPos = pos;
                level.destroyBlockProgress(npc.getId(), pos, stage);
            }
            return;
        }
        if (crackStage >= 0) {
            clearCrack(level);
        }
        if (progress < target) {
            return;
        }
        complete(level, settlement, project, board, current, now);
    }

    private void complete(ServerLevel level, Settlement settlement, BuildProject project, JobBoard board, BuildStep current, long now) {
        BuildJobs.PlaceResult result = BuildJobs.perform(level, settlement, project, current, carrier);
        switch (result) {
            case PLACED, REMOVED -> {
                npc.swing(InteractionHand.MAIN_HAND);
                npc.addExperience(AWNPCStatics.npcXpFromWork);
                npc.gainSkillXp(Skill.BUILDING, SettlementConfig.skillXpBuild);
                finishStep(board, project, true);
            }
            case SKIPPED -> finishStep(board, project, true);
            case NO_MATERIAL -> {
                dropStep(board, project);
                nextPick = 0L; // fetch it
            }
            case BLOCKED -> {
                BuildJobs.markDone(level, settlement, project, current); // cannot be broken: left as it is
                finishStep(board, project, true);
            }
            case UNLOADED, INVALID -> dropStep(board, project);
        }
    }

    private void finishStep(JobBoard board, BuildProject project, boolean done) {
        BuildStep finished = step;
        if (finished != null) {
            board.releaseStep(project.getId(), finished.index(), npc.getUUID());
            cooldown.remove(finished.index());
        }
        if (npc.level() instanceof ServerLevel level) {
            clearCrack(level);
        }
        step = null;
        started = false;
        progress = 0.0D;
        nextPick = 0L;
        asideTo = null;
    }

    private void dropStep(JobBoard board, BuildProject project) {
        BuildStep dropped = step;
        if (dropped != null) {
            board.releaseStep(project.getId(), dropped.index(), npc.getUUID());
        }
        if (npc.level() instanceof ServerLevel level) {
            clearCrack(level);
        }
        step = null;
        started = false;
        progress = 0.0D;
        nextPick = 0L;
        workWalk.reset();
    }

    private boolean renewClaim(JobBoard board, BuildProject project, long now) {
        keepTimer = 0;
        BuildStep current = step;
        return current != null && board.claimStep(project.getId(), current.index(), npc.getUUID(), now);
    }

    private static String doingKey(BuildStep current) {
        return switch (current.kind()) {
            case CLEAR -> "work.build_clear";
            case FILL -> "work.build_level";
            case REMOVE -> "work.build_demolish";
            case PLACE -> "work.build_place";
        };
    }

    /** Progress per working tick: the skill and the traits of the builder, and for breaking the tool on the block. */
    private double rate(boolean breaking, BlockState state) {
        double skill = Math.max(0.1D, npc.getBaseWorkEffectiveness(WorkType.BUILDING));
        double rankFactor = SkillEffects.workSpeed(npc.getSkillRank(Skill.BUILDING));
        skill *= SkillEffects.clampSpeed(rankFactor * TraitEffects.workSpeed(npc, Skill.BUILDING)); // with lazy, hardworking, night owl
        double tool = 1.0D;
        if (breaking) {
            ItemStack held = npc.getMainHandItem();
            tool = Math.max(1.0D, Math.min(MAX_TOOL_SPEED, held.isEmpty() ? 1.0D : held.getDestroySpeed(state)));
        }
        return tool * skill;
    }

    private void swingEffects(ServerLevel level, BlockState state, BuildStep current, boolean breaking) {
        BlockState shown = breaking ? state : current.state();
        if (shown.isAir()) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(current.pos());
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, shown), center.x, center.y, center.z, 4, 0.25D, 0.25D, 0.25D, 0.05D);
        level.playSound(null, current.pos(), breaking ? shown.getSoundType().getHitSound() : shown.getSoundType().getPlaceSound(), SoundSource.BLOCKS,
                breaking ? 0.4F : 0.5F, breaking ? 0.8F : 1.0F);
    }

    private void clearCrack(ServerLevel level) {
        if (crackStage >= 0 && npc.crackPos != null) {
            level.destroyBlockProgress(npc.getId(), npc.crackPos, -1);
        }
        npc.clearCrack();
        crackStage = -1;
    }

    /** Takes the tool that fits the block into the hand (see {@link WorkTool#forTask}); asks the fetch-tool goal for one if the builder has none. */
    private void chooseBreakTool(BlockState state) {
        if (!SettlementConfig.workerToolSwitching) {
            return;
        }
        List<WorkTool> kinds = WorkTool.forTask(WorkAction.MINE, state);
        if (kinds.isEmpty()) {
            npc.equipRoleTool();
            return;
        }
        ItemStack held = npc.getMainHandItem();
        for (WorkTool kind : kinds) {
            if (kind.matches(held)) {
                return;
            }
        }
        for (WorkTool kind : kinds) {
            for (net.neoforged.neoforge.items.IItemHandlerModifiable handler : new net.neoforged.neoforge.items.IItemHandlerModifiable[]{npc.getToolBelt(), npc.getPouch()}) {
                int slot = npc.findToolSlot(handler, kind, state);
                if (slot >= 0) {
                    npc.equipFrom(handler, slot);
                    npc.setWantedTool(null);
                    return;
                }
            }
        }
        npc.setWantedTool(kinds.get(0));
    }

    // ---------------------------------------------------------------- reaching a block

    /** Gets the builder within reach of the block: on foot, over climbable blocks, or up a scaffolding column that it builds. */
    private Access access(ServerLevel level, Settlement settlement, BuildProject project, BlockPos pos) {
        if (NpcRouteAssist.inReach(npc, pos, REACH, false)) {
            workWalk.reset();
            column = null;
            columnComplete = false;
            columnTries = 0;
            return Access.READY;
        }
        double distance = npc.distanceToSqr(Vec3.atCenterOf(pos));
        double walkY = Math.min(pos.getY(), npc.getY() + 2.0D);
        if ((column == null || columnComplete) && workWalk.step(pos.getX() + 0.5D, walkY, pos.getZ() + 0.5D, pos, REACH, true, false, distance)) {
            return Access.MOVING;
        }
        workWalk.reset();
        return scaffoldAccess(level, settlement, project, pos);
    }

    /** Builds (or keeps building) the scaffolding column to the layer of the target. */
    private Access scaffoldAccess(ServerLevel level, Settlement settlement, BuildProject project, BlockPos targetPos) {
        if (!SettlementConfig.builderScaffolding) {
            return Access.NO_WAY;
        }
        long now = level.getGameTime();
        if (column == null || !targetPos.equals(columnTarget)) {
            if (!npc.scaffolds.isEmpty() && !onlyColumn(npc.scaffolds, targetPos)) {
                dismantling = true; // the old columns are far away: take them down first, then plan again
                return Access.MOVING;
            }
            column = planColumn(level, settlement, project, targetPos);
            columnComplete = false;
            columnTarget = targetPos;
            columnTries = 0;
            if (column == null) {
                return Access.NO_WAY;
            }
        }
        Column plan = column;
        npc.setDoing("work.build_scaffold", targetPos, Component.empty());
        for (int y = plan.baseY(); y <= plan.topY(); y++) {
            BlockPos cell = new BlockPos(plan.x(), y, plan.z());
            BlockState existing = level.getBlockState(cell);
            if (existing.is(Blocks.SCAFFOLDING)) {
                continue;
            }
            if (!existing.isAir() && !existing.canBeReplaced()) {
                column = null;
                return Access.NO_WAY; // something stands in the column
            }
            if (!NpcRouteAssist.inReach(npc, cell, REACH, false)) {
                double distance = npc.distanceToSqr(Vec3.atCenterOf(cell));
                if (!scaffoldWalk.step(cell.getX() + 0.5D, Math.min(cell.getY(), npc.getY() + 2.0D), cell.getZ() + 0.5D, cell, REACH, true, false, distance)) {
                    column = null;
                    return Access.NO_WAY;
                }
                return Access.MOVING;
            }
            scaffoldWalk.reset();
            if (now < scaffoldNext) {
                return Access.MOVING;
            }
            if (SettlementConfig.builderScaffoldCostsMaterial) {
                BuildMaterials.Req req = BuildMaterials.reqFor(Blocks.SCAFFOLDING.defaultBlockState());
                if (req == null || !BuildMaterials.extract(level, settlement, req, 1)) {
                    column = null;
                    return Access.NO_WAY; // the storage has no scaffolding
                }
            }
            level.setBlock(cell, Blocks.SCAFFOLDING.defaultBlockState(), Block.UPDATE_ALL);
            level.playSound(null, cell, SoundType.SCAFFOLDING.getPlaceSound(), SoundSource.BLOCKS, 0.8F, 0.9F);
            npc.swing(InteractionHand.MAIN_HAND);
            npc.scaffolds.add(cell);
            scaffoldNext = now + 8L;
            npc.getRouteAssist().forgetFailure();
            return Access.MOVING;
        }
        // the column stands: the walk can find the way up now (a few tries, then the step is left for later)
        columnComplete = true;
        npc.getRouteAssist().forgetFailure();
        if (++columnTries > 3) {
            column = null;
            columnComplete = false;
            return Access.NO_WAY;
        }
        return Access.MOVING;
    }

    /** True if every scaffolding block of the list stands in one column, close to the target (that column is reused). */
    private boolean onlyColumn(List<BlockPos> scaffolds, BlockPos targetPos) {
        BlockPos first = scaffolds.get(0);
        for (BlockPos p : scaffolds) {
            if (p.getX() != first.getX() || p.getZ() != first.getZ()) {
                return false;
            }
        }
        double dx = first.getX() - targetPos.getX();
        double dz = first.getZ() - targetPos.getZ();
        return dx * dx + dz * dz <= 36.0D;
    }

    /**
     * The column for a high target: a place around the building (one or two blocks off its footprint, inside the settlement) with solid ground under it
     * and room all the way up to the cell below the target; the column nearest to the target wins, a column that already stands counts as nearer.
     */
    @Nullable
    private Column planColumn(ServerLevel level, Settlement settlement, BuildProject project, BlockPos targetPos) {
        int minX = project.getOrigin().getX();
        int minZ = project.getOrigin().getZ();
        int maxX = project.getMaxX();
        int maxZ = project.getMaxZ();
        int topY = targetPos.getY() - 1;
        int limit = topY - SettlementConfig.builderMaxScaffoldHeight;
        Column best = null;
        double bestScore = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX - 2; x <= maxX + 2; x++) {
            for (int z = minZ - 2; z <= maxZ + 2; z++) {
                if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                    continue;
                }
                if (!settlement.contains(x, z) || !level.isLoaded(cursor.set(x, topY, z))) {
                    continue;
                }
                int y = topY;
                while (y > limit && y > level.getMinBuildHeight()) {
                    BlockState s = level.getBlockState(cursor.set(x, y, z));
                    if (s.isAir() || s.canBeReplaced() || s.is(Blocks.SCAFFOLDING)) {
                        y--;
                    } else {
                        break;
                    }
                }
                if (y >= topY || y <= limit) {
                    continue; // no scaffolding needed here, or too high
                }
                BlockState ground = level.getBlockState(cursor.set(x, y, z));
                if (!ground.isFaceSturdy(level, cursor, Direction.UP)) {
                    continue;
                }
                double dx = x - targetPos.getX();
                double dz = z - targetPos.getZ();
                double score = dx * dx + dz * dz + (topY - y) * 0.5D;
                for (BlockPos existing : npc.scaffolds) {
                    if (existing.getX() == x && existing.getZ() == z) {
                        score -= 40.0D;
                        break;
                    }
                }
                if (score < bestScore) {
                    bestScore = score;
                    best = new Column(x, z, y + 1, topY);
                }
            }
        }
        return best;
    }

    /** The builder stands in the cell of the block it wants to place: it steps to a free cell beside it. @return true while it does */
    private boolean stepAside(ServerLevel level, BlockPos pos, long now) {
        if (asideTo != null && now < asideUntil) {
            return true;
        }
        BlockPos here = npc.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos cell = here.offset(dx, 0, dz);
                if ((cell.getX() == pos.getX() && cell.getZ() == pos.getZ()) || !level.isLoaded(cell)) {
                    continue;
                }
                if (!level.getBlockState(cell).getCollisionShape(level, cell).isEmpty() || !level.getBlockState(cell.above()).getCollisionShape(level, cell.above()).isEmpty()
                        || !level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), Direction.UP)) {
                    continue;
                }
                double distance = cell.distSqr(here);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = cell;
                }
            }
        }
        if (best == null) {
            return false;
        }
        asideTo = best;
        asideUntil = now + 30L;
        npc.getNavigation().moveTo(best.getX() + 0.5D, best.getY(), best.getZ() + 0.5D, 1.0D);
        npc.addAITask(NpcAI.TASK_MOVE);
        return true;
    }

    // ---------------------------------------------------------------- scaffolding down

    /** Takes the scaffolding down from the top. @return true while there is more to do */
    private boolean dismantle(ServerLevel level, Settlement settlement) {
        if (npc.scaffolds.isEmpty()) {
            return false;
        }
        BlockPos top = null;
        for (BlockPos p : npc.scaffolds) {
            if (top == null || p.getY() > top.getY()) {
                top = p;
            }
        }
        if (!level.isLoaded(top)) {
            return false; // later, when the chunk is back
        }
        if (!level.getBlockState(top).is(Blocks.SCAFFOLDING)) {
            npc.scaffolds.remove(top);
            return !npc.scaffolds.isEmpty();
        }
        npc.setDoing("work.build_scaffold_down", top, Component.empty());
        if (!NpcRouteAssist.inReach(npc, top, REACH, false)) {
            double distance = npc.distanceToSqr(Vec3.atCenterOf(top));
            if (!scaffoldWalk.step(top.getX() + 0.5D, Math.min(top.getY(), npc.getY() + 2.0D), top.getZ() + 0.5D, top, REACH, true, false, distance)
                    && ++dismantleFails > 3) {
                npc.scaffolds.remove(top); // cannot get there: it stays (it drops as an item when somebody breaks it)
                dismantleFails = 0;
            }
            return true;
        }
        scaffoldWalk.reset();
        long now = level.getGameTime();
        if (now < scaffoldNext) {
            return true;
        }
        npc.getNavigation().stop();
        npc.swing(InteractionHand.MAIN_HAND);
        level.destroyBlock(top, false);
        if (SettlementConfig.builderScaffoldCostsMaterial) {
            ItemStack rest = BuildMaterials.insert(level, settlement, new ItemStack(Items.SCAFFOLDING));
            if (!rest.isEmpty()) {
                Block.popResource(level, top, rest);
            }
        }
        npc.scaffolds.remove(top);
        scaffoldNext = now + 6L;
        dismantleFails = 0;
        column = null;
        return !npc.scaffolds.isEmpty();
    }

    // ---------------------------------------------------------------- leaving the job

    /**
     * No build to work on (finished, cancelled, given up): takes the scaffolding down and takes what is left in the pouch to a storage.
     * @return false when there is nothing (more) to do
     */
    public boolean cleanup(ServerLevel level, Settlement settlement) {
        if (step != null || projectId != null) {
            reset();
        }
        if (!npc.scaffolds.isEmpty() && dismantle(level, settlement)) {
            return true;
        }
        if (level.getGameTime() >= depositRetryAt && hasLeftovers() && deposit(level, settlement, false)) {
            return true;
        }
        cleanupRetryAt = level.getGameTime() + 200L; // nothing (more) to do now: look again later
        return false;
    }

    /** True if the builder carries something besides tools (it has something to take to the storage). */
    public boolean hasLeftovers() {
        for (int slot = 0; slot < npc.getPouch().getSlots(); slot++) {
            ItemStack stack = npc.getPouch().getStackInSlot(slot);
            if (!stack.isEmpty() && !isTool(stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTool(ItemStack stack) {
        return stack.getItem() instanceof HammerItem || WorkTool.isAnyTool(stack);
    }

    /** Whether there is anything to clean up now (the goal keeps running for it). */
    public boolean needsCleanup(long now) {
        return now >= cleanupRetryAt && (!npc.scaffolds.isEmpty() || (now >= depositRetryAt && hasLeftovers()));
    }

    // ---------------------------------------------------------------- the storage

    private List<ItemStack> stock(ServerLevel level, Settlement settlement, long now) {
        if (now - stockAt > 40L) {
            stock = BuildMaterials.stock(level, settlement);
            stockAt = now;
        }
        return stock;
    }

    @Nullable
    private BlockPos nearestStorageWith(ServerLevel level, Settlement settlement, Map<String, BuildMaterials.Req> wanted) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (SettlementApi.StorageHandle storage : SettlementApi.allStorages(level, settlement)) {
            if (!hasAny(storage.handler(), wanted)) {
                continue;
            }
            BlockPos p = storage.pos();
            double distance = npc.distanceToSqr(p.getX() + 0.5D, p.getY(), p.getZ() + 0.5D);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = p;
            }
        }
        return best;
    }

    private static boolean hasAny(IItemHandler handler, Map<String, BuildMaterials.Req> wanted) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            for (BuildMaterials.Req req : wanted.values()) {
                if (req.matches(stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Walks to the storage and takes what the next blocks need into the pouch. */
    private void fetch(ServerLevel level, Settlement settlement, Map<String, BuildMaterials.Req> wanted) {
        BlockPos pos = fetchPos;
        if (pos == null) {
            return;
        }
        npc.setDoing("work.build_fetch", pos, level.getBlockState(pos).getBlock().getName());
        if (!goNear(siteWalk, pos)) {
            return;
        }
        IItemHandler handler = SettlementApi.handlerFor(level, settlement, pos);
        boolean took = false;
        if (handler != null) {
            for (BuildMaterials.Req req : wanted.values()) {
                int want = req.count() - pouchCount(req);
                for (int slot = 0; slot < handler.getSlots() && want > 0; slot++) {
                    ItemStack inSlot = handler.getStackInSlot(slot);
                    if (!req.matches(inSlot)) {
                        continue;
                    }
                    ItemStack got = handler.extractItem(slot, Math.min(want, inSlot.getCount()), false);
                    if (got.isEmpty()) {
                        continue;
                    }
                    ItemStack rest = ItemHandlerHelper.insertItem(npc.getPouch(), got, false);
                    if (!rest.isEmpty()) {
                        ItemHandlerHelper.insertItem(handler, rest, false); // the pouch is full: back into the storage
                        want = 0;
                    }
                    want -= got.getCount() - rest.getCount();
                    took |= got.getCount() != rest.getCount();
                }
            }
        }
        if (took) {
            level.playSound(null, npc.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.3F, 0.9F);
        }
        stockAt = -1000L; // count the storage again
        fetchPos = null;
        fetchWanted = null;
        nextPick = 0L;
        siteWalk.reset();
    }

    /** Whether the pouch holds something the build does not need (more than that a pouch nearly full of it). */
    private boolean hasJunk(BuildProject project, long now) {
        if (now - remainingAt > 100L) {
            remaining = BuildJobs.remainingMaterials(project);
            remainingAt = now;
        }
        for (int slot = 0; slot < npc.getPouch().getSlots(); slot++) {
            ItemStack stack = npc.getPouch().getStackInSlot(slot);
            if (stack.isEmpty() || isTool(stack)) {
                continue;
            }
            boolean needed = false;
            for (BuildMaterials.Req req : remaining) {
                if (req.matches(stack)) {
                    needed = true;
                    break;
                }
            }
            if (!needed) {
                return true;
            }
        }
        return false;
    }

    /**
     * Takes the things in the pouch to the nearest storage that takes them. {@code keepNeeded}: leaves the materials the build still needs.
     * @return true while it is on its way or putting things in
     */
    private boolean deposit(ServerLevel level, Settlement settlement, boolean keepNeeded) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (SettlementApi.StorageHandle storage : SettlementApi.allStorages(level, settlement)) {
            BlockPos p = storage.pos();
            double distance = npc.distanceToSqr(p.getX() + 0.5D, p.getY(), p.getZ() + 0.5D);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = p;
            }
        }
        if (best == null) {
            depositRetryAt = level.getGameTime() + 600L;
            return false;
        }
        npc.setDoing("work.build_deposit", best, level.getBlockState(best).getBlock().getName());
        if (!goNear(siteWalk, best)) {
            return true;
        }
        IItemHandler handler = SettlementApi.handlerFor(level, settlement, best);
        boolean all = true;
        boolean moved = false;
        for (int slot = 0; slot < npc.getPouch().getSlots(); slot++) {
            ItemStack stack = npc.getPouch().getStackInSlot(slot);
            if (stack.isEmpty() || isTool(stack)) {
                continue;
            }
            if (keepNeeded) {
                boolean needed = false;
                for (BuildMaterials.Req req : remaining) {
                    if (req.matches(stack)) {
                        needed = true;
                        break;
                    }
                }
                if (needed) {
                    continue;
                }
            }
            ItemStack rest = handler == null ? stack : ItemHandlerHelper.insertItem(handler, stack, false);
            if (rest.getCount() != stack.getCount()) {
                moved = true;
            }
            npc.getPouch().setStackInSlot(slot, rest);
            all &= rest.isEmpty();
        }
        if (moved) {
            level.playSound(null, npc.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.3F, 0.8F);
        }
        if (!all) {
            depositRetryAt = level.getGameTime() + 600L; // this storage is full: try again later
        }
        siteWalk.reset();
        return false;
    }

    /** Walks to a storage block. @return true when within reach of it */
    private boolean goNear(Walk walk, BlockPos pos) {
        double distance = npc.distanceToSqr(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        if (distance <= Math.max(npc.getWorkRangeSq(), 4.0D)) {
            walk.reset();
            npc.getNavigation().stop();
            npc.removeAITask(NpcAI.TASK_MOVE);
            return true;
        }
        if (!walk.step(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, pos, 3.0D, false, false, distance)) {
            npc.addAITask(NpcAI.TASK_MOVE);
            mover.move(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, distance); // no route: the plain walk
        }
        return false;
    }

    // ---------------------------------------------------------------- the pouch

    private int pouchCount(BuildMaterials.Req req) {
        int total = 0;
        for (int slot = 0; slot < npc.getPouch().getSlots(); slot++) {
            ItemStack stack = npc.getPouch().getStackInSlot(slot);
            if (req.matches(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private boolean takeFromPouch(BuildMaterials.Req req, int count) {
        if (pouchCount(req) < count) {
            return false;
        }
        int left = count;
        for (int slot = 0; slot < npc.getPouch().getSlots() && left > 0; slot++) {
            if (req.matches(npc.getPouch().getStackInSlot(slot))) {
                left -= npc.getPouch().extractItem(slot, left, false).getCount();
            }
        }
        return left <= 0;
    }

    // ---------------------------------------------------------------- state

    /** Drops the claim on the current step and the crack overlay (the goal ends, the build changes). */
    public void reset() {
        if (npc.level() instanceof ServerLevel level) {
            clearCrack(level);
            if (projectId != null && step != null) {
                npc.getSettlement().ifPresent(settlement -> settlement.getJobBoard().releaseStep(projectId, step.index(), npc.getUUID()));
            }
        }
        step = null;
        started = false;
        progress = 0.0D;
        projectId = null;
        fetchPos = null;
        fetchWanted = null;
        column = null;
        columnComplete = false;
        asideTo = null;
        workWalk.reset();
        siteWalk.reset();
        scaffoldWalk.reset();
    }

    /** True if the builder waits for something (materials) and has no step. */
    public boolean isWaiting() {
        return step == null && waitingSince > 0L;
    }

    /** The deposit of the junk when the pouch is nearly full of things the build does not need; called every tick of a job. */
    public boolean depositJunkIfNeeded(ServerLevel level, Settlement settlement, BuildProject project) {
        long now = level.getGameTime();
        if (now < depositRetryAt || npc.pouchFreeSlots() >= RESERVE || (step != null && started)) {
            return false;
        }
        if (!hasJunk(project, now)) {
            return false;
        }
        return deposit(level, settlement, true);
    }

    // ---------------------------------------------------------------- walking

    /** One way of getting somewhere: on foot first, over the climbable blocks when that finds no way (like the walk of the staffed work). */
    private final class Walk {
        private boolean assist;
        private int noPath;
        private double best = Double.MAX_VALUE;
        private int stuck;

        void reset() {
            if (assist || noPath != 0 || stuck != 0) {
                if (assist) {
                    npc.getRouteAssist().cancel();
                }
                assist = false;
                noPath = 0;
                stuck = 0;
                best = Double.MAX_VALUE;
            }
        }

        /** @return false if there is no way there (the caller gives the target up) */
        boolean step(double x, double y, double z, BlockPos targetPos, double reach, boolean eyes, boolean los, double distanceSq) {
            NpcRouteAssist route = npc.getRouteAssist();
            if (!assist) {
                npc.addAITask(NpcAI.TASK_MOVE);
                mover.move(x, y, z, distanceSq);
                PathNavigation navigation = npc.getNavigation();
                Path path = navigation.getPath();
                noPath = path == null || !path.canReach() ? noPath + 1 : 0;
                if (distanceSq < best - 0.5D) {
                    best = distanceSq;
                    stuck = 0;
                } else {
                    stuck++;
                }
                double dx = x - npc.getX();
                double dz = z - npc.getZ();
                boolean near = dx * dx + dz * dz < 48.0D * 48.0D;
                if (near && (noPath >= NO_PATH_TICKS || stuck > STUCK_TICKS / 2)) {
                    assist = true;
                } else {
                    return stuck <= STUCK_TICKS;
                }
            }
            if (!route.request(targetPos, reach, eyes, los)) {
                return false;
            }
            npc.addAITask(NpcAI.TASK_MOVE);
            return true;
        }
    }
}
