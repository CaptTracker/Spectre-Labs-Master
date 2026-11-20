/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 3): a worker takes its carry pouch to the worksite at the end of its shift.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfare.api.IWorksiteTasks;
import io.github.capttracker.ancientwarfare.util.WorldTools;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.entity.NpcWorker;
import io.github.capttracker.ancientwarfarenpc.settlement.Activity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * A worker that still carries items from a staffed worksite when its shift is over (the work band has ended, it has to go home, or it has no job any
 * more) walks to that worksite and empties its pouch into the site's inventory (priority 5, before the schedule goal, so it happens before the
 * socialising and the sleep). Without a loaded site it tries again after a while; if the site is gone for good (its chunk is loaded and nothing is
 * there) the items are dropped where the worker stands. It gives up after {@value #MAX_TICKS} ticks and retries after {@value #RETRY_TICKS}.
 */
public class NpcAIWorkerDeposit extends NpcAI<NpcWorker> {
    private static final int MAX_TICKS = 600;
    private static final int RETRY_TICKS = 400;

    private long nextTry = 0;
    private int ticks;

    public NpcAIWorkerDeposit(NpcWorker npc) {
        super(npc);
        setMutexBits(MOVE);
    }

    private boolean shiftOver() {
        if (npc.shouldBeAtHome()) {
            return true;
        }
        if (!npc.ordersStack.isEmpty()) {
            return false;
        }
        return npc.autoWorkTarget == null || npc.getActivity() != Activity.WORK;
    }

    @Override
    public boolean canUse() {
        if (!super.canUse() || npc.pouchSite == null || npc.isPouchEmpty() || npc.tickCount < nextTry) {
            return false;
        }
        return shiftOver();
    }

    @Override
    public boolean canContinueToUse() {
        return npc.pouchSite != null && !npc.isPouchEmpty() && ticks < MAX_TICKS && super.canUse();
    }

    @Override
    public void start() {
        ticks = 0;
        npc.addAITask(TASK_WORK);
    }

    @Override
    public void tick() {
        ticks++;
        BlockPos pos = npc.pouchSite;
        if (pos == null || !(npc.level() instanceof ServerLevel level)) {
            return;
        }
        if (!level.isLoaded(pos)) {
            nextTry = npc.tickCount + RETRY_TICKS;
            ticks = MAX_TICKS;
            return;
        }
        Optional<IWorksiteTasks> site = WorldTools.getTile(level, pos, IWorksiteTasks.class);
        if (site.isEmpty()) {
            // the site is gone: the worker keeps nothing it cannot hand in
            for (int slot = 0; slot < npc.getPouch().getSlots(); slot++) {
                if (!npc.getPouch().getStackInSlot(slot).isEmpty()) {
                    npc.spawnAtLocation(npc.getPouch().getStackInSlot(slot), 0.0F);
                    npc.getPouch().setStackInSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
                }
            }
            npc.pouchSite = null;
            return;
        }
        double distance = npc.distanceToSqr(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        if (distance > Math.max(npc.getWorkRangeSq(), 4.0D)) {
            npc.addAITask(TASK_MOVE);
            moveToPosition(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, distance);
            return;
        }
        npc.getNavigation().stop();
        npc.removeAITask(TASK_MOVE);
        if (!StaffedWork.emptyPouchInto(npc, site.get().getDepositInventory())) {
            nextTry = npc.tickCount + RETRY_TICKS; // the site is full
            ticks = MAX_TICKS;
        }
    }

    @Override
    public void stop() {
        npc.removeAITask(TASK_WORK + TASK_MOVE);
        if (ticks >= MAX_TICKS && nextTry < npc.tickCount) {
            nextTry = npc.tickCount + RETRY_TICKS;
        }
    }
}
