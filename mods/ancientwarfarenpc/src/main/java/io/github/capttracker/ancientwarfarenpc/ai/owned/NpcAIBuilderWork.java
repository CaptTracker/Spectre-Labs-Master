/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): the builder works on the build it claimed.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfare.api.IWorkSite.WorkType;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.entity.NpcWorker;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.building.BuildJobs;
import io.github.capttracker.ancientwarfarenpc.settlement.building.BuildProject;
import io.github.capttracker.ancientwarfarenpc.settlement.building.BuildingDesign;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Work loop of the builder (worker with the role {@code builder}) without orders: it works on the build of the queue that {@code NpcAIPlayerOwnedFindWorksite}
 * claimed at the job board ({@code NpcWorker.buildJobId}); the work itself is {@link BuilderWork}. When the build is finished or gone it writes the
 * thought "finished building a ...", lets go of the claim, takes down its scaffolding and takes what is left in its pouch to the storage.
 */
public class NpcAIBuilderWork extends NpcAI<NpcWorker> {
    private final BuilderWork work;

    public NpcAIBuilderWork(NpcWorker npc) {
        super(npc);
        setMutexBits(ATTACK + MOVE);
        work = new BuilderWork(npc, (x, y, z, sqDist) -> moveToPosition(x, y, z, sqDist));
    }

    @Override
    public boolean canUse() {
        if (!super.canUse() || npc.getRoleWorkType() != WorkType.BUILDING || npc.shouldBeAtHome() || !npc.ordersStack.isEmpty()
                || !(npc.level() instanceof ServerLevel level)) {
            return false;
        }
        return npc.buildJobId != null || work.needsCleanup(level.getGameTime());
    }

    @Override
    public void start() {
        npc.addAITask(TASK_WORK);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        if (!(npc.level() instanceof ServerLevel level)) {
            return;
        }
        Settlement settlement = npc.getSettlement().orElse(null);
        if (settlement == null || !settlement.getDimension().equals(level.dimension())) {
            npc.buildJobId = null;
            work.reset();
            return;
        }
        UUID id = npc.buildJobId;
        BuildProject project = id == null ? null : settlement.getBuilding().project(id).orElse(null);
        if (id != null && (project == null || project.isDone() || !project.isOpen() || !settlement.getJobBoard().holdsBuild(npc.getUUID(), id))) {
            if (project != null && project.isDone()) {
                npc.think("built", nameOf(project));
            }
            npc.buildJobId = null; // finished, cancelled (its plot was removed) or taken away by the board: look for another build
            settlement.getJobBoard().releaseBuild(npc.getUUID());
            work.reset();
            return;
        }
        if (project == null) {
            if (!work.cleanup(level, settlement)) {
                npc.getNavigation().stop();
                npc.removeAITask(TASK_MOVE);
            }
            return;
        }
        work.tick(level, settlement, project);
    }

    @Override
    public void stop() {
        work.reset();
        npc.getNavigation().stop();
        npc.removeAITask(TASK_WORK + TASK_MOVE);
    }

    private static Component nameOf(@Nullable BuildProject project) {
        BuildingDesign design = project == null ? null : BuildJobs.designOf(project);
        return design == null ? Component.translatable("npc.thought.building.generic") : Component.literal(design.getName());
    }
}
