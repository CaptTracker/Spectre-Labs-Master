/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/owned/NpcAIPlayerOwnedWorkRandom.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfare.api.IWorkSite;
import io.github.capttracker.ancientwarfare.util.WorldTools;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcWorker;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;

import java.util.Optional;

/**
 * The work loop of the worker without orders: it works at {@code NpcWorker.autoWorkTarget}, the job that {@code NpcAIPlayerOwnedFindWorksite} claimed on
 * the job board. It cranks the torque of worksites that are not staffed; staffed ones (rework phase 3) are worked by {@link NpcAIStaffedWork}.
 */
public class NpcAIPlayerOwnedWorkRandom extends NpcAI<NpcWorker> {
    private int ticksAtSite = 0;

    public NpcAIPlayerOwnedWorkRandom(NpcWorker npc) {
        super(npc);
        setMutexBits(ATTACK + MOVE);
    }

    @Override
    public boolean canUse() {
        if (!super.canUse()) {
            return false;
        }
        if (npc.shouldBeAtHome()) {
            return false;
        }
        // a staffed worksite (rework phase 3) hands out tasks to NpcAIStaffedWork instead of being cranked
        return npc.ordersStack.isEmpty() && npc.autoWorkTarget != null && NpcAIStaffedWork.staffedSite(npc, npc.autoWorkTarget).isEmpty();
    }

    @Override
    public void start() {
        npc.addAITask(TASK_WORK);
        ticksAtSite = 0;
    }

    @Override
    public void tick() {
        BlockPos pos = npc.autoWorkTarget;
        if (pos == null) {
            return;
        }
        double dist = distanceSq(pos);
        if (dist > npc.getWorkRangeSq()) {
            npc.addAITask(TASK_MOVE);
            ticksAtSite = 0;
            moveToPosition(pos, dist);
        } else {
            npc.getNavigation().stop();
            npc.removeAITask(TASK_MOVE);
            workAtSite(pos);
        }
    }

    @Override
    public void stop() {
        npc.removeAITask(TASK_WORK + TASK_MOVE);
    }

    private void workAtSite(BlockPos target) {
        if (npc.tickCount % 10 == 0) {
            npc.swing(InteractionHand.MAIN_HAND);
        }
        if (!npc.advanceWorkTick()) {
            return; // hunger slows the work down
        }
        ticksAtSite++;
        if (npc.takeBonusWorkTick()) {
            ticksAtSite++;
        }
        if (ticksAtSite >= AWNPCStatics.npcWorkTicks) {
            ticksAtSite = 0;
            Optional<IWorkSite> te = WorldTools.getTile(npc.level(), target, IWorkSite.class);
            if (te.isPresent() && npc.canWorkAt(te.get().getWorkType())) {
                IWorkSite site = te.get();
                if (site.hasWork()) {
                    npc.addExperience(AWNPCStatics.npcXpFromWork);
                    site.addEnergyFromWorker(npc);
                }
                return; // with no work the worker waits: the job board releases the claim after the idle time
            }
            npc.releaseJob(); // the site is gone or no longer fits the role
        }
    }

    public void readFromNBT(CompoundTag tag) {
        ticksAtSite = tag.getInt("ticksAtSite");
    }

    public CompoundTag writeToNBT(CompoundTag tag) {
        tag.putInt("ticksAtSite", ticksAtSite);
        return tag;
    }
}
