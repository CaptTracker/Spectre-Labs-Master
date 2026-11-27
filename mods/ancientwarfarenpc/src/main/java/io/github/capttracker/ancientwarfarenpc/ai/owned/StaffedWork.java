/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 3): a worker doing the tasks of a staffed worksite.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfare.api.IBoatWorksite;
import io.github.capttracker.ancientwarfare.api.IWorksiteTasks;
import io.github.capttracker.ancientwarfare.api.WorkAction;
import io.github.capttracker.ancientwarfare.api.WorkTask;
import io.github.capttracker.ancientwarfare.util.WorldTools;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.ai.path.NpcRouteAssist;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcWorker;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.skill.Skill;
import io.github.capttracker.ancientwarfarenpc.skill.SkillEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * The work of one worker at a staffed worksite ({@link IWorksiteTasks}), driven tick by tick by a goal. The goal gives it the movement call of
 * {@link NpcAI}, so the worker walks like everywhere else.
 *
 * <p>Per task: claim a task from the site, walk within reach of its target (4.5 blocks from the eyes, with a line of sight; no flying: the worker walks
 * on the ground and, where vanilla navigation cannot, follows a route over the ladders of a mine, see {@link NpcRouteAssist}), face it and swing the tool
 * every {@code swing_interval_ticks} while the progress counter fills, then let the site do the action for that target. A target that cannot be reached
 * goes on cooldown at the site. Progress per tick is {@code toolSpeed * skill * torqueBoost}, and it only advances on the ticks
 * {@link io.github.capttracker.ancientwarfarenpc.entity.NpcBase#advanceWorkTick()} allows (morale factor times tool factor; hunger is part of the morale); the target is
 * {@link WorkTask#baseTicks()} (from the block's hardness for breaking actions). For breaking actions the block's crack overlay follows the progress
 * ({@code Level.destroyBlockProgress}) and the worker's tool speed on that block counts; particles and the block's hit sound play with the swing.
 *
 * <p>Drops go into the pouch of the worker. When fewer than {@value #POUCH_RESERVE} slots are free it takes the pouch to the site and empties it into
 * the site's inventory before it claims the next task (and the {@link NpcAIWorkerDeposit} goal does the same at the end of the shift).
 */
public final class StaffedWork {
    /** Replaces the movement call of the owning goal. */
    @FunctionalInterface
    public interface Mover {
        void move(double x, double y, double z, double sqDist);
    }

    public enum State {
        /** Walking to or working at a task target. */
        WORKING,
        /** Walking to the site or emptying the pouch. */
        DEPOSITING,
        /** No task right now; the worker waits at the site. */
        IDLE
    }

    /** The pouch is emptied when fewer slots than this are free: a harvest can drop several stacks. */
    static final int POUCH_RESERVE = 3;
    private static final int STUCK_TICKS = 100;
    /** Ticks without a way on foot after which a route over the ladders is asked for. */
    private static final int NO_PATH_TICKS = 10;
    /** A worker can work a block within this many blocks of its eyes (fishing: at the water's edge). */
    private static final double WORK_REACH = NpcRouteAssist.WORK_REACH;
    private static final double FISH_REACH = 3.0D;
    /** How close to the site block a worker stands to empty its pouch or wait. */
    private static final double SITE_REACH = 3.0D;
    private static final int KEEP_INTERVAL = 20;
    private static final int DEPOSIT_RETRY_TICKS = 200;
    private static final double MAX_TOOL_SPEED = 8.0D;

    private final NpcWorker npc;
    private final Mover mover;

    @Nullable
    private WorkTask task;
    @Nullable
    private BlockPos taskSite;
    /** The skill the current task trains and works with (rework phase 7). */
    @Nullable
    private Skill taskSkill;
    private double progress;
    private int crackStage = -1;
    private int swingTimer;
    /** Ticks a lazy worker still idles before it works on the task it started. */
    private int idlePause;
    private double holdY = Double.NaN;
    private final Walk taskWalk = new Walk();
    private final Walk siteWalk = new Walk();
    private int keepTimer;
    private long depositRetryAt;
    private boolean started;
    /** The boat side of the work at a dock (null until the worker first works at one). */
    @Nullable
    private BoatTrip trip;

    public StaffedWork(NpcWorker npc, Mover mover) {
        this.npc = npc;
        this.mover = mover;
    }

    private BoatTrip trip() {
        if (trip == null) {
            trip = new BoatTrip(npc);
        }
        return trip;
    }

    /** One tick of work at the site; call it every tick while the worker has the job. */
    public State tick(BlockPos sitePos, IWorksiteTasks site) {
        UUID id = npc.getUUID();
        if (taskSite != null && !taskSite.equals(sitePos)) {
            reset(null); // the order moved on to another site
        }
        IBoatWorksite dock = site instanceof IBoatWorksite boatSite ? boatSite : null;
        boolean aboard = dock != null && trip().aboard(dock);
        if (dock != null) {
            BoatTrip boat = trip();
            if (aboard && (boat.returning || npc.pouchFreeSlots() < POUCH_RESERVE || (task == null && boat.catches >= dock.getTripLength()))) {
                return sailHome(sitePos, site, dock);
            }
            if (!aboard && boat.unloadPending && npc.isPouchEmpty()) {
                boat.unloadPending = false;
            }
        }
        boolean forced = dock != null && trip().unloadPending && !npc.isPouchEmpty();
        if (!aboard && !npc.isPouchEmpty() && (forced || npc.pouchFreeSlots() < POUCH_RESERVE) && npc.tickCount >= depositRetryAt) {
            if (task != null) {
                site.keepTask(id, task);
            }
            return deposit(sitePos, site);
        }

        if (task == null && npc.tickCount % 10 == 0) {
            npc.equipRoleTool(); // between tasks the worker holds the tool of its role again
        }
        if (task == null) {
            Optional<WorkTask> claimed = site.claimTask(id, npc.blockPosition());
            if (claimed.isEmpty()) {
                if (aboard) {
                    trip().returning = true; // nothing more to fish: home
                    return State.WORKING;
                }
                waitAtSite(sitePos);
                return State.IDLE;
            }
            task = claimed.get();
            taskSkill = Skill.ofTask(task.action(), site.getWorkType());
            taskSite = sitePos;
            started = false;
            progress = 0;
            taskWalk.reset();
            swingTimer = Integer.MAX_VALUE / 2;
            holdY = Double.NaN;
            keepTimer = 0;
        } else if (++keepTimer >= KEEP_INTERVAL) {
            keepTimer = 0;
            if (!site.keepTask(id, task)) {
                resetKeepingClaim();
                return State.IDLE; // the claim expired or the target is no longer work
            }
        }

        WorkTask current = task;
        ServerLevel level = (ServerLevel) npc.level();
        if (!level.isLoaded(current.pos())) {
            site.releaseTask(id, current, true);
            resetKeepingClaim();
            return State.IDLE;
        }
        Vec3 center = Vec3.atCenterOf(current.pos());
        BlockState atTarget = level.getBlockState(current.pos());
        npc.setDoing("work." + current.action().name().toLowerCase(java.util.Locale.ROOT), current.pos(),
                (atTarget.isAir() ? level.getBlockState(sitePos) : atTarget).getBlock().getName());
        if (dock != null) {
            double distance = npc.distanceToSqr(center);
            BoatTrip boat = trip();
            double reach = 16.0D; // from the boat: 4 blocks
            if (!aboard) {
                Boolean boarded = boat.board(dock, mover);
                if (boarded == null) {
                    site.releaseTask(id, current, false); // no boat to board right now: try again later
                    resetKeepingClaim();
                    return State.IDLE;
                }
                keepTimer = Math.min(keepTimer, KEEP_INTERVAL - 1);
                return State.WORKING;
            }
            if (distance > reach) {
                BoatTrip.Sail sail = boat.sail(dock, current.pos(), 4.0D, false);
                if (sail == BoatTrip.Sail.NO_ROUTE || sail == BoatTrip.Sail.STUCK) {
                    site.releaseTask(id, current, true); // cannot get there by water: the spot is left alone for a while
                    resetKeepingClaim();
                    return State.WORKING;
                }
                if (sail == BoatTrip.Sail.SAILING) {
                    keepTimer = Math.min(keepTimer, KEEP_INTERVAL - 1);
                    return State.WORKING;
                }
            } else {
                boat.hold(dock, current.pos());
            }
        } else if (!NpcRouteAssist.inReach(npc, current.pos(), reachOf(current.action()), true)) {
            if (!approach(current)) {
                site.releaseTask(id, current, true); // there is no way there: the target is left alone for a while
                npc.think("no_reach");
                resetKeepingClaim();
                return State.WORKING;
            }
            keepTimer = Math.min(keepTimer, KEEP_INTERVAL - 1);
            return State.WORKING;
        } else {
            taskWalk.reset(); // within reach: stand and work
        }

        npc.getNavigation().stop();
        npc.removeAITask(NpcAI.TASK_MOVE);
        npc.getLookControl().setLookAt(center.x, center.y, center.z);
        holdOnLadder();
        boolean fishing = current.action() == WorkAction.FISH;
        if (!started) {
            started = true;
            if (io.github.capttracker.ancientwarfarenpc.trait.TraitEffects.lazyPause(npc)) {
                idlePause = 40 + npc.getRandom().nextInt(60); // a lazy worker idles a while before the task
            }
            site.taskStarted(id, current, npc);
            if (fishing) {
                npc.swing(InteractionHand.MAIN_HAND); // the cast
            }
        }

        BlockState state = level.getBlockState(current.pos());
        chooseTool(current, state);
        boolean breaking = current.action().isBreaking() && !state.isAir();
        if (idlePause > 0) {
            idlePause--;
            return State.WORKING;
        }
        if (npc.advanceWorkTick()) {
            progress += rate(site, state, breaking);
            if (npc.takeBonusWorkTick()) {
                progress += rate(site, state, breaking); // high morale: a bonus tick of work now and then
            }
        }
        if (!fishing && swingTimer++ >= SettlementConfig.swingIntervalTicks) {
            swingTimer = 1;
            npc.swing(InteractionHand.MAIN_HAND); // one swing every swing_interval_ticks while the action is in progress
            swingEffects(level, current, state);
        }
        if (breaking) {
            int stage = (int) Math.min(9, progress * 10.0D / current.baseTicks());
            if (stage != crackStage) {
                crackStage = stage;
                npc.crackPos = current.pos();
                level.destroyBlockProgress(npc.getId(), current.pos(), stage);
            }
        }
        if (progress >= current.baseTicks() && site.isTaskReady(id, current)) {
            if (fishing) {
                npc.swing(InteractionHand.MAIN_HAND); // the reel in
            }
            finish(site, sitePos, current, level);
        }
        return State.WORKING;
    }

    // ---------------------------------------------------------------- tool

    /**
     * Takes the tool that fits the block into the main hand before the swing: the kinds come from the block tags ({@link WorkTool#forTask}), the tool from the
     * hand, the tool belt, then the pouch. If there is none it asks the fetch-tool goal to get one from the storage of the settlement and goes on with what it
     * holds (slower, as vanilla breaks a block with the wrong tool). Without a particular kind the worker holds the tool of its role.
     */
    private void chooseTool(WorkTask current, BlockState state) {
        if (!SettlementConfig.workerToolSwitching) {
            return;
        }
        java.util.List<WorkTool> kinds = WorkTool.forTask(current.action(), state);
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

    // ---------------------------------------------------------------- walking to the target

    private static double reachOf(WorkAction action) {
        return action == WorkAction.FISH ? FISH_REACH : WORK_REACH;
    }

    /**
     * Walks towards the target of the task: the vanilla navigation first; a worker that has no way on foot (down in a mine) or stops getting closer gets
     * a route over the ladders. Nobody flies. @return false if there is no way
     */
    private boolean approach(WorkTask current) {
        BlockPos pos = current.pos();
        double distance = npc.distanceToSqr(Vec3.atCenterOf(pos));
        double walkY = Math.min(pos.getY(), npc.getY() + 2.0D);
        return taskWalk.step(pos.getX() + 0.5D, walkY, pos.getZ() + 0.5D, pos, reachOf(current.action()), true, true, distance);
    }

    /** On a ladder while working: hold the height (climb back up when the ladder lets the worker slide down), as a player holds on. */
    private void holdOnLadder() {
        if (npc.onClimbable()) {
            if (Double.isNaN(holdY)) {
                holdY = npc.getY();
            }
            if (npc.getY() < holdY - 0.05D) {
                npc.getJumpControl().jump();
            }
        } else {
            holdY = Double.NaN;
        }
    }

    /** One way of getting somewhere: on foot first, over the ladders when that finds no way. */
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
        boolean step(double x, double y, double z, BlockPos target, double reach, boolean eyes, boolean los, double distanceSq) {
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
            if (!route.request(target, reach, eyes, los)) {
                return false;
            }
            npc.addAITask(NpcAI.TASK_MOVE);
            return true;
        }
    }

    /** Progress per working tick: the tool on the block (breaking actions), the worker's skill and the torque boost of the site. */
    private double rate(IWorksiteTasks site, BlockState state, boolean breaking) {
        double skill = Math.max(0.1D, npc.getBaseWorkEffectiveness(site.getWorkType()));
        double rankFactor = taskSkill != null ? SkillEffects.workSpeed(npc.getSkillRank(taskSkill)) : 1.0D; // the rank of the skill of this task: breaking, tending, the bite time of fishing
        skill *= SkillEffects.clampSpeed(rankFactor * io.github.capttracker.ancientwarfarenpc.trait.TraitEffects.workSpeed(npc, taskSkill)); // with lazy, hardworking, night owl, keen eye
        double tool = 1.0D;
        if (breaking) {
            ItemStack held = npc.getMainHandItem();
            tool = Math.max(1.0D, Math.min(MAX_TOOL_SPEED, held.isEmpty() ? 1.0D : held.getDestroySpeed(state)));
        }
        return tool * skill * site.getTorqueBoost();
    }

    private void swingEffects(ServerLevel level, WorkTask current, BlockState state) {
        if (current.action() == io.github.capttracker.ancientwarfare.api.WorkAction.SMITH) {
            // rework phase 8: the smith hammers at the anvil, sparks fly
            Vec3 top = Vec3.atCenterOf(current.pos()).add(0.0D, 0.5D, 0.0D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, top.x, top.y, top.z, 6, 0.25D, 0.1D, 0.25D, 0.15D);
            level.playSound(null, current.pos(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.35F, 1.1F + npc.getRandom().nextFloat() * 0.3F);
            return;
        }
        if (current.action() == io.github.capttracker.ancientwarfare.api.WorkAction.TAN) {
            Vec3 top = Vec3.atCenterOf(current.pos()).add(0.0D, 0.5D, 0.0D);
            level.sendParticles(ParticleTypes.SPLASH, top.x, top.y, top.z, 6, 0.25D, 0.1D, 0.25D, 0.05D);
            level.playSound(null, current.pos(), SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.2F, 1.2F);
            return;
        }
        BlockState shown = state.isAir() ? level.getBlockState(current.pos().below()) : state;
        if (shown.isAir()) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(current.pos());
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, shown), center.x, center.y, center.z, 4, 0.25D, 0.25D, 0.25D, 0.05D);
        level.playSound(null, current.pos(), shown.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.4F, 0.8F);
    }

    private void finish(IWorksiteTasks site, BlockPos sitePos, WorkTask current, ServerLevel level) {
        int before = npc.pouchItemCount();
        BlockState targetState = level.getBlockState(current.pos());
        java.util.Map<net.minecraft.world.item.Item, Integer> pouchBefore = yieldEligible(current, targetState) ? pouchCounts() : null;
        Skill skill = taskSkill;
        boolean done = site.completeTask(npc.getUUID(), current, npc.getPouch(), npc);
        clearCrack(level);
        task = null;
        taskSkill = null;
        taskSite = null;
        started = false;
        progress = 0;
        if (done) {
            if (site instanceof IBoatWorksite && trip != null) {
                trip.catches++;
            }
            npc.addExperience(AWNPCStatics.npcXpFromWork);
            npc.gainSkillXp(skill, SettlementConfig.skillXpWork);
            if (pouchBefore != null && skill != null) {
                double rankChance = SkillEffects.yieldChance(npc.getSkillRank(skill));
                double traitChance = io.github.capttracker.ancientwarfarenpc.trait.TraitEffects.yieldChance(npc, skill); // green thumb
                extraYield(pouchBefore, 1.0D - (1.0D - rankChance) * (1.0D - traitChance));
            }
            clumsy(level);
            if (npc.pouchItemCount() > before) {
                npc.pouchSite = sitePos;
                level.playSound(null, npc.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.2F, 1.0F + npc.getRandom().nextFloat() * 0.4F);
            }
        }
    }

    // ---------------------------------------------------------------- clumsy

    /** A clumsy worker may wear its tool down one more point, and may drop one carried item at the site. */
    private void clumsy(ServerLevel level) {
        if (io.github.capttracker.ancientwarfarenpc.trait.TraitEffects.clumsyToolWear(npc)) {
            ItemStack held = npc.getMainHandItem();
            if (!held.isEmpty() && held.isDamageableItem()) {
                held.hurtAndBreak(1, npc, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            }
        }
        if (io.github.capttracker.ancientwarfarenpc.trait.TraitEffects.clumsyDrop(npc)) {
            for (int slot = 0; slot < npc.getPouch().getSlots(); slot++) {
                ItemStack stack = npc.getPouch().getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    npc.spawnAtLocation(npc.getPouch().extractItem(slot, 1, false));
                    break;
                }
            }
        }
    }

    // ---------------------------------------------------------------- skill yield

    /** Ores, harvested crops and logs can give an extra item for a skilled worker. */
    private static boolean yieldEligible(WorkTask current, BlockState state) {
        return switch (current.action()) {
            case MINE -> state.is(net.neoforged.neoforge.common.Tags.Blocks.ORES);
            case HARVEST -> true;
            case CHOP -> true;
            default -> false;
        };
    }

    private java.util.Map<net.minecraft.world.item.Item, Integer> pouchCounts() {
        java.util.Map<net.minecraft.world.item.Item, Integer> counts = new java.util.HashMap<>();
        for (int slot = 0; slot < npc.getPouch().getSlots(); slot++) {
            ItemStack stack = npc.getPouch().getStackInSlot(slot);
            if (!stack.isEmpty()) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    /** With the chance of the rank, one more of the item the task gave most of (into the pouch, or at the feet of the worker when it is full). */
    private void extraYield(java.util.Map<net.minecraft.world.item.Item, Integer> before, double chance) {
        if (chance <= 0.0D || npc.getRandom().nextDouble() >= chance) {
            return;
        }
        java.util.Map<net.minecraft.world.item.Item, Integer> after = pouchCounts();
        net.minecraft.world.item.Item best = null;
        int bestGain = 0;
        for (java.util.Map.Entry<net.minecraft.world.item.Item, Integer> entry : after.entrySet()) {
            int gain = entry.getValue() - before.getOrDefault(entry.getKey(), 0);
            if (gain > bestGain) {
                bestGain = gain;
                best = entry.getKey();
            }
        }
        if (best != null) {
            ItemStack left = ItemHandlerHelper.insertItemStacked(npc.getPouch(), new ItemStack(best), false);
            if (!left.isEmpty()) {
                npc.spawnAtLocation(left);
            }
        }
    }

    // ---------------------------------------------------------------- boat

    /** Sails the boat to the berth and gets off; the pouch is emptied at the dock afterwards. */
    private State sailHome(BlockPos sitePos, IWorksiteTasks site, IBoatWorksite dock) {
        if (task != null) {
            site.releaseTask(npc.getUUID(), task, false);
            resetKeepingClaim();
        }
        npc.setDoing("work.sail", sitePos, npc.level().getBlockState(sitePos).getBlock().getName());
        BoatTrip boat = trip();
        BoatTrip.Sail sail = boat.sail(dock, dock.getBerth(), 2.5D, true);
        if (sail == BoatTrip.Sail.SAILING) {
            npc.addAITask(NpcAI.TASK_WORK);
            return State.WORKING;
        }
        // there, or no way home (the boat is stuck): get off at the landing, the dock calls an empty boat home itself
        boat.dismount(dock);
        boat.endTrip();
        return State.DEPOSITING;
    }

    /** The goal ends while the worker is on the boat: it gets off at the landing. */
    public void leaveBoat(@Nullable IWorksiteTasks site) {
        if (trip != null && site instanceof IBoatWorksite dock && trip.aboard(dock)) {
            trip.dismount(dock);
            trip.endTrip();
        } else if (trip != null && site == null && npc.getVehicle() instanceof io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase) {
            npc.stopRiding(); // the dock is gone
        }
    }

    // ---------------------------------------------------------------- pouch

    private State deposit(BlockPos sitePos, IWorksiteTasks site) {
        npc.setDoing("work.deposit", sitePos, npc.level().getBlockState(sitePos).getBlock().getName());
        double distance = npc.distanceToSqr(sitePos.getX() + 0.5D, sitePos.getY(), sitePos.getZ() + 0.5D);
        if (distance > Math.max(npc.getWorkRangeSq(), 4.0D)) {
            goToSite(sitePos, distance);
            return State.DEPOSITING;
        }
        siteWalk.reset();
        npc.getNavigation().stop();
        npc.removeAITask(NpcAI.TASK_MOVE);
        if (!emptyPouchInto(npc, site.getDepositInventory())) {
            depositRetryAt = npc.tickCount + DEPOSIT_RETRY_TICKS; // the site is full: work on and try again later
        }
        return State.DEPOSITING;
    }

    /**
     * Moves what fits of the pouch into the handler. @return true if the pouch is empty afterwards
     */
    public static boolean emptyPouchInto(NpcWorker worker, @Nullable IItemHandler target) {
        boolean moved = false;
        boolean empty = true;
        for (int slot = 0; slot < worker.getPouch().getSlots(); slot++) {
            ItemStack stack = worker.getPouch().getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack left = target == null ? stack : ItemHandlerHelper.insertItemStacked(target, stack, false);
            if (left.getCount() != stack.getCount()) {
                moved = true;
            }
            worker.getPouch().setStackInSlot(slot, left);
            empty &= left.isEmpty();
        }
        if (moved) {
            worker.level().playSound(null, worker.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.3F, 0.8F);
        }
        if (empty) {
            worker.pouchSite = null;
        }
        return empty;
    }

    private void waitAtSite(BlockPos sitePos) {
        npc.setDoing("work.wait", sitePos, npc.level().getBlockState(sitePos).getBlock().getName());
        double distance = npc.distanceToSqr(sitePos.getX() + 0.5D, sitePos.getY(), sitePos.getZ() + 0.5D);
        if (distance > npc.getWorkRangeSq()) {
            goToSite(sitePos, distance);
        } else {
            siteWalk.reset();
            npc.getNavigation().stop();
            npc.removeAITask(NpcAI.TASK_MOVE);
        }
    }

    /** Back to the site block (to empty the pouch, or to wait): a worker down in a mine climbs out over its ladders. */
    private void goToSite(BlockPos sitePos, double distance) {
        if (!siteWalk.step(sitePos.getX() + 0.5D, sitePos.getY(), sitePos.getZ() + 0.5D, sitePos, SITE_REACH, false, false, distance)) {
            npc.addAITask(NpcAI.TASK_MOVE);
            mover.move(sitePos.getX() + 0.5D, sitePos.getY(), sitePos.getZ() + 0.5D, distance); // no route: the plain walk, as before
        }
    }

    // ---------------------------------------------------------------- state

    private void clearCrack(ServerLevel level) {
        if (crackStage >= 0 && task != null) {
            level.destroyBlockProgress(npc.getId(), task.pos(), -1);
        }
        npc.clearCrack();
        crackStage = -1;
    }

    /** Drops the current task: its claim goes back to the site (the given one, or the one it was claimed at) and the crack overlay is cleared. */
    public void reset(@Nullable IWorksiteTasks site) {
        if (task != null) {
            if (npc.level() instanceof ServerLevel level) {
                clearCrack(level);
            }
            IWorksiteTasks owner = site;
            if (owner == null && taskSite != null) {
                owner = WorldTools.getTile(npc.level(), taskSite, IWorksiteTasks.class).orElse(null);
            }
            if (owner != null) {
                owner.releaseTask(npc.getUUID(), task, false);
            }
        }
        resetKeepingClaim();
    }

    /** Forgets the task without touching the site (its claim was already given back or has expired); the crack overlay is cleared. */
    private void resetKeepingClaim() {
        if (task != null && npc.level() instanceof ServerLevel level) {
            clearCrack(level);
        }
        crackStage = -1;
        task = null;
        taskSite = null;
        started = false;
        progress = 0;
        taskWalk.reset();
        holdY = Double.NaN;
    }
}
