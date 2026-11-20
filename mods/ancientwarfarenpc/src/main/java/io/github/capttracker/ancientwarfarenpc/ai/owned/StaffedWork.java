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
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcWorker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
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
 * <p>Per task: claim a task from the site, walk within reach of its target, face it and swing the tool while the progress counter fills, then let the
 * site do the action for that target. Progress per tick is {@code toolSpeed * skill * torqueBoost}, and it only advances on the ticks
 * {@link io.github.capttracker.ancientwarfarenpc.entity.NpcBase#advanceWorkTick()} allows (hunger factor times tool factor); the target is
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
    private static final int SWING_INTERVAL = 6;
    private static final int STUCK_TICKS = 100;
    private static final int KEEP_INTERVAL = 20;
    private static final int DEPOSIT_RETRY_TICKS = 200;
    private static final double MAX_TOOL_SPEED = 8.0D;

    private final NpcWorker npc;
    private final Mover mover;

    @Nullable
    private WorkTask task;
    @Nullable
    private BlockPos taskSite;
    private double progress;
    private double bestDistance;
    private int stuck;
    private int crackStage = -1;
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
            taskSite = sitePos;
            started = false;
            progress = 0;
            stuck = 0;
            bestDistance = Double.MAX_VALUE;
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
        double distance = npc.distanceToSqr(center);
        double reach = Math.max(npc.getWorkRangeSq(), 4.0D);
        if (current.action() == WorkAction.FISH) {
            reach = Math.min(reach, 9.0D); // fishing: at the water's edge
        }
        if (dock != null) {
            BoatTrip boat = trip();
            reach = 16.0D; // from the boat: 4 blocks
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
        } else if (distance > reach && climb(center, current.action())) {
            keepTimer = Math.min(keepTimer, KEEP_INTERVAL - 1);
            return State.WORKING; // going up or down a trunk
        }
        if (distance > reach && !aboard) {
            npc.endWorkClimb();
            double walkY = Math.min(current.pos().getY(), npc.getY() + 2.0D);
            npc.addAITask(NpcAI.TASK_MOVE);
            mover.move(current.pos().getX() + 0.5D, walkY, current.pos().getZ() + 0.5D, distance);
            if (distance < bestDistance - 0.5D) {
                bestDistance = distance;
                stuck = 0;
            } else if (++stuck > STUCK_TICKS) {
                site.releaseTask(id, current, true); // cannot get there: the target is left alone for a while
                resetKeepingClaim();
            }
            return State.WORKING;
        }

        npc.getNavigation().stop();
        npc.removeAITask(NpcAI.TASK_MOVE);
        npc.getLookControl().setLookAt(center.x, center.y, center.z);
        if (npc.workClimbing) {
            holdClimb();
        }
        boolean fishing = current.action() == WorkAction.FISH;
        if (!started) {
            started = true;
            site.taskStarted(id, current, npc);
            if (fishing) {
                npc.swing(InteractionHand.MAIN_HAND); // the cast
            }
        }

        BlockState state = level.getBlockState(current.pos());
        boolean breaking = current.action().isBreaking() && !state.isAir();
        if (npc.advanceWorkTick()) {
            progress += rate(site, state, breaking);
            if (npc.takeBonusWorkTick()) {
                progress += rate(site, state, breaking); // high morale: a bonus tick of work now and then
            }
        }
        if (!fishing && npc.tickCount % SWING_INTERVAL == 0) {
            npc.swing(InteractionHand.MAIN_HAND);
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

    // ---------------------------------------------------------------- climbing

    private static final double CLIMB_SPEED = 0.25D;
    private static final double CLIMB_COLUMN_SQ = 16.0D;
    /** Miners also descend into a pit or shaft from further away (12 blocks). */
    private static final double MINE_COLUMN_SQ = 144.0D;

    /**
     * Steers the worker up (or down) beside the target when it is close in the horizontal plane but out of reach in height: a trunk block high in a tree.
     * The worker rises without gravity and collision to about a block below the target. Returns false when it is not near the column (it walks then).
     */
    private boolean climb(Vec3 target, WorkAction action) {
        double dx = target.x - npc.getX();
        double dz = target.z - npc.getZ();
        boolean mining = action == WorkAction.MINE || action == WorkAction.PLACE;
        if (dx * dx + dz * dz > (mining ? MINE_COLUMN_SQ : CLIMB_COLUMN_SQ)) {
            return false;
        }
        // a miner goes down a shaft or a pit to stand at the level of the target; a trunk is climbed to just below it
        double wantedY = mining ? target.y - 0.5D : target.y - 1.5D;
        double error = wantedY - npc.getY();
        if (error < 0 && !npc.workClimbing && !(mining && error < -2.5D)) {
            return false; // below the worker: it simply walks or drops down
        }
        if (Math.abs(error) < 0.4D && !npc.workClimbing) {
            return false;
        }
        npc.workClimbing = true;
        npc.noPhysics = true;
        npc.fallDistance = 0.0F;
        npc.getNavigation().stop();
        npc.removeAITask(NpcAI.TASK_MOVE);
        double step = Math.max(-CLIMB_SPEED, Math.min(CLIMB_SPEED, error));
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double sideX = horizontal > 1.5D ? dx / horizontal * 0.15D : 0.0D;
        double sideZ = horizontal > 1.5D ? dz / horizontal * 0.15D : 0.0D;
        npc.setDeltaMovement(sideX, Math.abs(error) < 0.4D ? 0.0D : step, sideZ);
        npc.getLookControl().setLookAt(target.x, target.y, target.z);
        return true;
    }

    /** At the working height: hold still in the air. */
    private void holdClimb() {
        npc.noPhysics = true;
        npc.fallDistance = 0.0F;
        npc.setDeltaMovement(0.0D, 0.0D, 0.0D);
    }

    /** Progress per working tick: the tool on the block (breaking actions), the worker's skill and the torque boost of the site. */
    private double rate(IWorksiteTasks site, BlockState state, boolean breaking) {
        double skill = Math.max(0.1D, npc.getWorkEffectiveness(site.getWorkType()));
        double tool = 1.0D;
        if (breaking) {
            ItemStack held = npc.getMainHandItem();
            tool = Math.max(1.0D, Math.min(MAX_TOOL_SPEED, held.isEmpty() ? 1.0D : held.getDestroySpeed(state)));
        }
        return tool * skill * site.getTorqueBoost();
    }

    private void swingEffects(ServerLevel level, WorkTask current, BlockState state) {
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
        boolean done = site.completeTask(npc.getUUID(), current, npc.getPouch(), npc);
        clearCrack(level);
        task = null;
        taskSite = null;
        started = false;
        progress = 0;
        if (done) {
            if (site instanceof IBoatWorksite && trip != null) {
                trip.catches++;
            }
            npc.addExperience(AWNPCStatics.npcXpFromWork);
            if (npc.pouchItemCount() > before) {
                npc.pouchSite = sitePos;
                level.playSound(null, npc.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.2F, 1.0F + npc.getRandom().nextFloat() * 0.4F);
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
        double distance = npc.distanceToSqr(sitePos.getX() + 0.5D, sitePos.getY(), sitePos.getZ() + 0.5D);
        if (distance > Math.max(npc.getWorkRangeSq(), 4.0D)) {
            npc.addAITask(NpcAI.TASK_MOVE);
            mover.move(sitePos.getX() + 0.5D, sitePos.getY(), sitePos.getZ() + 0.5D, distance);
            return State.DEPOSITING;
        }
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
        double distance = npc.distanceToSqr(sitePos.getX() + 0.5D, sitePos.getY(), sitePos.getZ() + 0.5D);
        if (distance > npc.getWorkRangeSq()) {
            npc.addAITask(NpcAI.TASK_MOVE);
            mover.move(sitePos.getX() + 0.5D, sitePos.getY(), sitePos.getZ() + 0.5D, distance);
        } else {
            npc.getNavigation().stop();
            npc.removeAITask(NpcAI.TASK_MOVE);
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
        stuck = 0;
        npc.endWorkClimb();
    }
}
