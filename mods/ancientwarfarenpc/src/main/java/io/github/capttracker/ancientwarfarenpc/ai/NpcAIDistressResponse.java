/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/NpcAIDistressResponse.
 */
package io.github.capttracker.ancientwarfarenpc.ai;

import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.NpcCombat;

import javax.annotation.Nullable;

/** A combat NPC runs to the NPC that sent a distress call ({@code NpcCombat.getDistressedTarget}). It stops within 2 blocks or when the target dies. */
public class NpcAIDistressResponse extends NpcAI<NpcBase> {
    @Nullable
    private NpcBase target = null;
    private static final double FOLLOW_STOP_DISTANCE = 2.d * 2.d;

    public NpcAIDistressResponse(NpcBase npc) {
        super(npc);
        setMutexBits(MOVE + ATTACK);
    }

    @Override
    public boolean canUse() {
        if (!super.canUse()) {
            return false;
        }
        if (npc instanceof NpcCombat combat) {
            target = combat.getDistressedTarget();
            if (target != null) {
                if (target.isAlive()) {
                    return true;
                } else {
                    combat.clearDistress();
                }
            }
        }
        return false;
    }

    @Override
    public void start() {
        moveRetryDelay = 0;
        npc.addAITask(TASK_FOLLOW + TASK_ATTACK);
    }

    @Override
    public void stop() {
        target = null;
        moveRetryDelay = 0;
        npc.removeAITask(TASK_FOLLOW + TASK_MOVE + TASK_ATTACK);
        if (npc instanceof NpcCombat combat) {
            combat.clearDistress();
        }
    }

    @Override
    public void tick() {
        if (target == null || !target.isAlive()) {
            // mission failed!
            stop();
            return;
        }
        npc.getLookControl().setLookAt(target, 10.0F, (float) npc.getMaxHeadXRot());
        double distance = npc.distanceToSqr(target);
        if (distance > FOLLOW_STOP_DISTANCE) {
            npc.addAITask(TASK_MOVE + TASK_ATTACK);
            moveToEntity(target, distance);
        } else {
            // we've reached the distressed NPC, no need to follow them anymore
            stop();
        }
    }
}
