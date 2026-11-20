/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/owned/NpcAIPlayerOwnedFindWorksite; reworked in phase 2 (the job board).
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.entity.NpcWorker;
import io.github.capttracker.ancientwarfarenpc.settlement.JobBoard;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import net.minecraft.core.BlockPos;

import java.util.Optional;

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
            return;
        }
        JobBoard board = settlement.get().getJobBoard();
        BlockPos target = npc.autoWorkTarget;
        if (target != null && board.holds(npc.getUUID(), target)) {
            return; // keeps the claim (also while it eats or sleeps)
        }
        npc.autoWorkTarget = board.claim(npc, npc.getRoleWorkType()).orElse(null);
    }
}
