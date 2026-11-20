/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 3): the worker without orders works the tasks of its claimed staffed worksite.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfare.api.IWorksiteTasks;
import io.github.capttracker.ancientwarfare.util.WorldTools;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.entity.NpcWorker;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Work loop of the worker whose claimed job ({@code NpcWorker.autoWorkTarget}) is a staffed worksite (a site that hands out tasks, see
 * {@link IWorksiteTasks}): the work itself is {@link StaffedWork}. A claimed site that is not staffed (not converted yet, or with
 * {@code machinesWorkAlone}) is left to {@link NpcAIPlayerOwnedWorkRandom}, which cranks its torque as before.
 */
public class NpcAIStaffedWork extends NpcAI<NpcWorker> {
    private final StaffedWork work;

    public NpcAIStaffedWork(NpcWorker npc) {
        super(npc);
        setMutexBits(ATTACK + MOVE);
        work = new StaffedWork(npc, (x, y, z, sqDist) -> moveToPosition(x, y, z, sqDist));
    }

    /** The staffed site at the position, if there is one. */
    public static Optional<IWorksiteTasks> staffedSite(NpcWorker npc, @Nullable BlockPos pos) {
        return WorldTools.getTile(npc.level(), pos, IWorksiteTasks.class)
                .filter(site -> site.isStaffed() && npc.canWorkAt(site.getWorkType()));
    }

    @Override
    public boolean canUse() {
        if (!super.canUse() || npc.shouldBeAtHome() || !npc.ordersStack.isEmpty() || npc.autoWorkTarget == null) {
            return false;
        }
        return staffedSite(npc, npc.autoWorkTarget).isPresent();
    }

    @Override
    public void start() {
        npc.addAITask(TASK_WORK);
    }

    @Override
    public void tick() {
        BlockPos pos = npc.autoWorkTarget;
        if (pos == null) {
            return;
        }
        Optional<IWorksiteTasks> site = staffedSite(npc, pos);
        if (site.isEmpty()) {
            return; // canUse ends the goal; the job board releases a site that is gone
        }
        work.tick(pos, site.get());
    }

    @Override
    public void stop() {
        BlockPos pos = npc.autoWorkTarget;
        IWorksiteTasks site = pos == null ? null : staffedSite(npc, pos).orElse(null);
        work.reset(site);
        work.leaveBoat(site);
        npc.getNavigation().stop();
        npc.removeAITask(TASK_WORK + TASK_MOVE);
    }
}
