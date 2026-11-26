/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/owned/NpcAIPlayerOwnedFindWorksite; reworked in phase 2 (the job board).
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfare.api.IWorkSite;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.entity.NpcWorker;
import io.github.capttracker.ancientwarfarenpc.settlement.JobBoard;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.building.BuildJobs;
import io.github.capttracker.ancientwarfarenpc.settlement.building.BuildProject;
import io.github.capttracker.ancientwarfarenpc.settlement.building.BuildingDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.UUID;

/**
 * Sensor goal of the worker (in the target goals, no flags): every 40 ticks, with no work order, it keeps or claims a job on the job board of its
 * settlement. A worker without a claim takes a job that matches its role, has work and a free slot (see {@link JobBoard#claim}) and stores the
 * site as {@code autoWorkTarget}; a claim the board has released (the site had no work for 60 seconds, was removed, or the role changed) clears the
 * target. A worker that is not in a settlement has no jobs: the 1.12.2 scan of 80 x 40 x 80 blocks is gone. Work orders override all of this.
 */
public class NpcAIPlayerOwnedFindWorksite extends NpcAI<NpcWorker> {
    private int lastExecuted = -1;//set to -1 default to trigger should execute lookup on first run
    private static final int CHECK_FREQUENCY = 40;

    public NpcAIPlayerOwnedFindWorksite(NpcWorker npc) {
        super(npc);
    }

    @Override
    public boolean canUse() {
        if (!super.canUse()) {
            return false;
        }
        return (lastExecuted == -1 || npc.tickCount - lastExecuted > CHECK_FREQUENCY) && npc.ordersStack.isEmpty();
    }

    @Override
    public void start() {
        lastExecuted = npc.tickCount;
        Optional<Settlement> settlement = npc.getSettlement();
        if (settlement.isEmpty() || !settlement.get().getDimension().equals(npc.level().dimension())) {
            npc.autoWorkTarget = null;
            npc.buildJobId = null;
            return;
        }
        if (npc.getRoleWorkType() == IWorkSite.WorkType.BUILDING) {
            findBuild(settlement.get()); // rework phase 9: a builder claims a build of the queue, not a worksite
            return;
        }
        JobBoard board = settlement.get().getJobBoard();
        BlockPos target = npc.autoWorkTarget;
        if (target != null && board.holds(npc.getUUID(), target)) {
            return; // keeps the claim (also while it eats or sleeps)
        }
        npc.autoWorkTarget = board.claim(npc, npc.getRoleWorkType()).orElse(null);
        if (npc.autoWorkTarget != null) {
            npc.think("got_job", npc.level().getBlockState(npc.autoWorkTarget).getBlock().getName());
        } else if (npc.getRoleWorkType() != io.github.capttracker.ancientwarfare.api.IWorkSite.WorkType.NONE) {
            npc.think("no_job");
        }
    }

    /** A builder keeps the build it claimed (also while it eats or sleeps), or claims the first free one of the queue. */
    private void findBuild(Settlement settlement) {
        ServerLevel level = (ServerLevel) npc.level();
        JobBoard board = settlement.getJobBoard();
        UUID held = npc.buildJobId;
        if (held != null && board.holdsBuild(npc.getUUID(), held)) {
            return;
        }
        npc.buildJobId = board.claimBuild(level, settlement, npc).orElse(null);
        if (npc.buildJobId != null) {
            BuildProject project = settlement.getBuilding().project(npc.buildJobId).orElse(null);
            BuildingDesign design = project == null ? null : BuildJobs.designOf(project);
            npc.think("got_build", design == null ? Component.translatable("npc.thought.building.generic") : Component.literal(design.getName()));
        }
    }
}
