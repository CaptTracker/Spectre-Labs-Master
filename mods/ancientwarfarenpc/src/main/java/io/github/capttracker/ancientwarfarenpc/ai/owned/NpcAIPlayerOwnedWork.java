/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/owned/NpcAIPlayerOwnedWork.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfare.api.IWorkSite;
import io.github.capttracker.ancientwarfare.api.IWorker;
import io.github.capttracker.ancientwarfare.util.WorldTools;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.orders.WorkOrder;
import io.github.capttracker.ancientwarfarenpc.orders.WorkOrder.WorkEntry;
import io.github.capttracker.ancientwarfare.api.IWorksiteTasks;
import io.github.capttracker.ancientwarfarenpc.entity.NpcWorker;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Worker: follows the {@link WorkOrder} of its orders slot. It walks to each site, swings its arm every 10 ticks and every {@code npcWorkTicks} (default 50)
 * calls {@link IWorkSite#addEnergyFromWorker} and gets experience. The next site is chosen by the priority type of the order. It stops when {@code shouldBeAtHome()}; hunger only slows it down. Saves the ticks at the site and the work index. Worksites are only reached through the interface of Core.
 */
public class NpcAIPlayerOwnedWork extends NpcAI<NpcBase> {
    private int ticksAtSite;
    private int workIndex;
    @Nullable
    private WorkOrder order;
    private boolean init = false;
    private final IWorker worker;
    /** Does the tasks of the entries that are staffed worksites (rework phase 3); null for an NPC that is not a worker. */
    @Nullable
    private final StaffedWork staffedWork;

    public NpcAIPlayerOwnedWork(NpcBase npc) {
        super(npc);
        if (!(npc instanceof IWorker iWorker)) {
            throw new IllegalArgumentException("cannot instantiate work ai task on non-worker npc");
        }
        worker = iWorker;
        staffedWork = npc instanceof NpcWorker w ? new StaffedWork(w, (x, y, z, sqDist) -> moveToPosition(x, y, z, sqDist)) : null;
        setMutexBits(MOVE + ATTACK);
        ticksAtSite = 0;
    }

    @Override
    public boolean canUse() {
        if (!super.canUse()) {
            return false;
        }
        if (!init) {
            order = WorkOrder.getWorkOrder(npc.ordersStack);
            init = true;
            if (order == null || workIndex >= order.size()) {
                workIndex = 0;
            }
        }
        return !npc.shouldBeAtHome() && order != null && !order.isEmpty();
    }

    @Override
    public void tick() {
        if (order == null || order.isEmpty()) {
            return;
        }
        if (workIndex >= order.size()) {
            workIndex = 0;
        }
        WorkEntry entry = order.get(workIndex);
        BlockPos pos = entry.getPosition();
        if (staffedWork != null && npc instanceof NpcWorker w) {
            Optional<IWorksiteTasks> staffed = NpcAIStaffedWork.staffedSite(w, pos);
            if (staffed.isPresent()) {
                workStaffed(entry, pos, staffed.get());
                return;
            }
            staffedWork.reset(null);
        }
        double dist = distanceSq(pos);
        if (dist > worker.getWorkRangeSq()) {
            npc.addAITask(TASK_MOVE);
            ticksAtSite = 0;
            moveToPosition(pos, dist);
        } else {
            if (dist < 10 || shouldMoveFromTimeAtSite(entry) || shouldMoveFromNoWork()) {
                npc.getNavigation().stop();
                npc.removeAITask(TASK_MOVE);
            }
            workAtSite(entry);
        }
    }

    @Override
    public void start() {
        npc.addAITask(TASK_WORK);
    }

    /** An entry that is a staffed worksite: the worker does its tasks; with no task (or when the time is up) it moves on to the next entry as for the other sites. */
    private void workStaffed(WorkEntry entry, BlockPos pos, IWorksiteTasks site) {
        StaffedWork.State state = staffedWork.tick(pos, site);
        ticksAtSite++;
        if (shouldMoveFromTimeAtSite(entry) || (state == StaffedWork.State.IDLE && shouldMoveFromNoWork())) {
            staffedWork.reset(site);
            setMoveToNextSite();
        }
    }

    private void workAtSite(WorkEntry entry) {
        if (!npc.advanceWorkTick()) {
            swingArm();
            return; // hunger slows the work down
        }
        ticksAtSite++;
        if (ticksAtSite == 1) {
            BlockPos pos = entry.getPosition();
            if (WorldTools.getTile(npc.level(), pos, IWorkSite.class).map(t -> !worker.canWorkAt(t.getWorkType()) || !t.hasWork()).orElse(false)) {
                setMoveToNextSite();
                return;
            }
        }
        if (npc.takeBonusWorkTick()) {
            ticksAtSite++; // high morale: a bonus tick of work now and then
        }
        swingArm();
        doWork(entry);
    }

    private void swingArm() {
        if (npc.tickCount % 10 == 0) {
            npc.swing(InteractionHand.MAIN_HAND);
        }
    }

    private void doWork(WorkEntry entry) {
        if (ticksAtSite >= AWNPCStatics.npcWorkTicks) {
            ticksAtSite = 0;
            Optional<IWorkSite> workSite = WorldTools.getTile(npc.level(), entry.getPosition(), IWorkSite.class)
                    .filter(s -> worker.canWorkAt(s.getWorkType()));
            if (workSite.isPresent()) {
                IWorkSite site = workSite.get();
                if (site.hasWork()) {
                    npc.addExperience(AWNPCStatics.npcXpFromWork);
                    site.addEnergyFromWorker(worker);
                } else {
                    if (shouldMoveFromNoWork()) {
                        setMoveToNextSite();
                    }
                }
                if (shouldMoveFromTimeAtSite(entry)) {
                    setMoveToNextSite();
                }
                return;
            }
            setMoveToNextSite();
        }
    }

    private boolean shouldMoveFromNoWork() {
        return order != null && !order.getPriorityType().isTimed() && order.size() > 1;
    }

    private boolean shouldMoveFromTimeAtSite(WorkEntry entry) {
        return order != null && order.getPriorityType().isTimed() && ticksAtSite > entry.getWorkLength();
    }

    private void setMoveToNextSite() {
        if (order == null) {
            return;
        }
        ticksAtSite = 0;
        moveRetryDelay = 0;
        workIndex = order.getPriorityType().getNextWorkIndex(workIndex, order.getEntries(), npc);
    }

    public void onOrdersChanged() {
        if (staffedWork != null) {
            staffedWork.reset(null);
        }
        order = WorkOrder.getWorkOrder(npc.ordersStack);
        workIndex = 0;
        ticksAtSite = 0;
    }

    @Override
    public void stop() {
        if (staffedWork != null) {
            staffedWork.reset(null);
        }
        ticksAtSite = 0;
        npc.removeAITask(TASK_WORK + TASK_MOVE);
    }

    public void readFromNBT(CompoundTag tag) {
        ticksAtSite = tag.getInt("ticksAtSite");
        workIndex = tag.getInt("workIndex");
    }

    public CompoundTag writeToNBT(CompoundTag tag) {
        tag.putInt("ticksAtSite", ticksAtSite);
        tag.putInt("workIndex", workIndex);
        return tag;
    }
}
