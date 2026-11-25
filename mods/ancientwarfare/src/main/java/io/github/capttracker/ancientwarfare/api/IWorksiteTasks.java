/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 3): the staffed view of a worksite, next to IWorkSite.
 */
package io.github.capttracker.ancientwarfare.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A worksite that hands out {@link WorkTask}s to NPC workers instead of editing blocks by itself. Implemented by the worksite block entities (next to
 * {@link IWorkSite}); a site that is not converted yet, or runs with {@code machinesWorkAlone}, says {@link #isStaffed()} is false and works as before
 * (workers only crank its torque).
 *
 * <p>The worker's side: claim the job on the job board of the settlement, then {@link #claimTask} one task, walk within reach of its target, swing
 * until the duration is up (refreshing the claim with {@link #keepTask}), call {@link #completeTask} (the site does for that one target what its old
 * unstaffed action did) and carry the drops to {@link #getDepositInventory()}. A claim that is not refreshed for a few seconds returns to the open
 * tasks. Server thread only.
 */
public interface IWorksiteTasks {

    /** True if this site hands out tasks right now (it is converted and {@code machinesWorkAlone} is off). */
    boolean isStaffed();

    /** The kind of work, for the worker's skill. */
    IWorkSite.WorkType getWorkType();

    /** The tasks nobody has claimed: how many workers the site can use at once. Cheap enough to ask once a second. */
    List<WorkTask> getOpenTasks();

    /** The number of open tasks. */
    default int getOpenTaskCount() {
        return getOpenTasks().size();
    }

    /** True if there is any task at all, claimed or open (the job board's "has work"). */
    boolean hasTasks();

    /**
     * Claims a task for the worker: the one it already holds, or else the best open one (highest priority first, the nearest to {@code from} among
     * those). Empty if there is none or the site is switched off.
     */
    Optional<WorkTask> claimTask(UUID worker, BlockPos from);

    /** Refreshes the claim. @return false if the claim is gone (expired, the site changed) and the worker must drop the task */
    boolean keepTask(UUID worker, WorkTask task);

    /** Gives the task back. {@code failed} puts the target on a cooldown (the worker could not reach it). */
    void releaseTask(UUID worker, WorkTask task, boolean failed);

    /**
     * The worker has done the swinging: the site does the action at the target. Drops go into {@code drops} (the worker's pouch), or into the site's
     * own inventory if that is null.
     *
     * @return true if the action was done
     */
    boolean completeTask(UUID worker, WorkTask task, @Nullable IItemHandler drops);

    /**
     * The worker is within reach of the target and starts on the task (once per claim). A site that needs a visible set-up, as the fisher's bobber,
     * does it here. The default does nothing.
     */
    default void taskStarted(UUID worker, WorkTask task, LivingEntity actor) {
    }

    /**
     * Whether the worker may complete the task now, once its duration is up. A task that waits for something of the world (a fish biting) says false
     * until then; the worker keeps its claim and stays at the target. The default is true.
     */
    default boolean isTaskReady(UUID worker, WorkTask task) {
        return true;
    }

    /** As {@link #completeTask(UUID, WorkTask, IItemHandler)}, with the worker entity (its tool and luck count for fishing). The default ignores the actor. */
    default boolean completeTask(UUID worker, WorkTask task, @Nullable IItemHandler drops, @Nullable LivingEntity actor) {
        return completeTask(worker, task, drops);
    }

    /** The inventory a worker empties its pouch into; null if the site has none. */
    @Nullable
    IItemHandler getDepositInventory();

    /** The work speed factor from torque: 1 without stored torque, up to {@code torqueMaxBoost} with a full buffer. */
    double getTorqueBoost();
}
