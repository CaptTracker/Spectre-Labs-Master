/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/owned/NpcAIPlayerOwnedAlarmResponse.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementLife;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * When the alarm of the settlement is on ({@code NpcPlayerOwned.isAlarmed}), the NPC runs to shelter: a roofed spot inside the land away from the
 * threat, or the hall. An NPC without a settlement runs to its upkeep point as before. NPCs that rally, heal or fight back
 * ({@link NpcPlayerOwned#ignoresAlarmShelter}) are left to their own goals.
 */
public class NpcAIPlayerOwnedAlarmResponse extends NpcAI<NpcPlayerOwned> {
    private static final int RETHINK_INTERVAL = 100;

    @Nullable
    private BlockPos shelter;
    private long nextRethink;

    public NpcAIPlayerOwnedAlarmResponse(NpcPlayerOwned npc) {
        super(npc);
        setMutexBits(ATTACK + MOVE);
    }

    private Optional<Settlement> settlementHere() {
        return npc.getSettlement().filter(s -> s.getDimension().equals(npc.level().dimension()));
    }

    @Override
    public boolean canUse() {
        return super.canUse() && npc.isAlarmed && !npc.ignoresAlarmShelter()
                && (settlementHere().isPresent() || npc.getUpkeepPoint().isPresent() && npc.getUpkeepDimension().equals(npc.level().dimension()));
    }

    @Override
    public void start() {
        npc.addAITask(TASK_ALARM);
        npc.think("alarm");
        shelter = null;
        nextRethink = 0;
    }

    @Override
    public void tick() {
        Optional<Settlement> settlement = settlementHere();
        if (settlement.isPresent() && npc.level() instanceof ServerLevel level) {
            if (shelter == null || npc.tickCount >= nextRethink) {
                nextRethink = npc.tickCount + RETHINK_INTERVAL;
                shelter = SettlementLife.findShelter(level, settlement.get(), npc.blockPosition(), settlement.get().getThreatPos().orElse(null), npc.getRandom());
            }
            goTo(shelter);
        } else {
            npc.getUpkeepPoint().ifPresent(this::goTo);
        }
    }

    private void goTo(BlockPos pos) {
        double dist = npc.distanceToSqr(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d);
        if (dist > AWNPCStatics.npcActionRange * AWNPCStatics.npcActionRange) {
            npc.addAITask(TASK_MOVE);
            moveToPosition(pos, dist);
        } else {
            npc.removeAITask(TASK_MOVE);
        }
    }

    @Override
    public void stop() {
        moveRetryDelay = 0;
        shelter = null;
        npc.removeAITask(TASK_ALARM + TASK_MOVE);
    }
}
