/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 2): threat response.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.entity.NpcGuardCaptain;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A combat NPC without orders, while the alarm is on and the settlement has a threat inside its land ({@link NpcPlayerOwned#shouldRally}): runs to the
 * nearest threat and, within 20 blocks of it, makes it its attack target, so the melee or ranged goal takes over. The guard mode is respected by
 * {@code shouldRally}: PASSIVE and DEFEND NPCs do not go (they shelter, or fight only when attacked).
 */
public class NpcAIPlayerOwnedRally extends NpcAI<NpcPlayerOwned> {
    private static final double ENGAGE_RANGE_SQ = 20.0D * 20.0D;
    private static final int RESCAN_INTERVAL = 20;
    /** With a captain, a threat this close (blocks) to a soldier of the line is fought. */
    private static final double LINE_ENGAGE = 10.0D;

    @Nullable
    private LivingEntity threat;
    private int rescan;

    public NpcAIPlayerOwnedRally(NpcPlayerOwned npc) {
        super(npc);
        setMutexBits(MOVE);
    }

    @Nullable
    private LivingEntity nearestThreat() {
        if (!(npc.level() instanceof ServerLevel level)) {
            return null;
        }
        Settlement settlement = npc.getSettlement().orElse(null);
        if (settlement == null) {
            return null;
        }
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (UUID id : settlement.getThreatIds()) {
            Entity e = level.getEntity(id);
            if (e instanceof LivingEntity living && living.isAlive()) {
                double d = npc.distanceToSqr(living);
                if (d < bestDist) {
                    bestDist = d;
                    best = living;
                }
            }
        }
        return best;
    }

    @Override
    public boolean canUse() {
        if (!super.canUse() || !npc.shouldRally() || npc.getTarget() != null) {
            return false;
        }
        threat = nearestThreat();
        return threat != null;
    }

    @Override
    public boolean canContinueToUse() {
        return super.canUse() && npc.shouldRally() && npc.getTarget() == null && threat != null && threat.isAlive();
    }

    @Override
    public void start() {
        npc.addAITask(TASK_ALARM);
        rescan = 0;
    }

    @Override
    public void tick() {
        if (rescan-- <= 0) {
            rescan = RESCAN_INTERVAL;
            LivingEntity next = nearestThreat();
            if (next != null) {
                threat = next;
            }
        }
        if (threat == null) {
            return;
        }
        double dist = npc.distanceToSqr(threat);
        if (npc.level() instanceof ServerLevel level && holdLine(level, dist)) {
            return;
        }
        if (dist <= ENGAGE_RANGE_SQ) {
            npc.setTarget(threat);
            return;
        }
        npc.addAITask(TASK_MOVE);
        moveToEntity(threat, dist);
    }

    /**
     * With a guard captain in the settlement the NPCs form a line instead of running at the threat: each goes to its place in the line between the hall and
     * the threat and faces the threat, and only fights (makes it the target) when the threat comes within {@link #LINE_ENGAGE} blocks. Returns true if the
     * line holds this NPC this tick.
     */
    private boolean holdLine(ServerLevel level, double threatDistSq) {
        Settlement settlement = npc.getSettlement().orElse(null);
        if (settlement == null || threat == null || NpcGuardCaptain.captainOf(level, settlement) == null) {
            return false;
        }
        if (threatDistSq <= LINE_ENGAGE * LINE_ENGAGE) {
            npc.setTarget(threat);
            return true;
        }
        BlockPos slot = NpcGuardCaptain.lineSlot(level, settlement, npc.getUUID(), threat.blockPosition());
        double slotDist = npc.distanceToSqr(slot.getX() + 0.5D, slot.getY(), slot.getZ() + 0.5D);
        if (slotDist > 2.25D) {
            npc.addAITask(TASK_MOVE);
            moveToPosition(slot, slotDist);
        } else {
            npc.getNavigation().stop();
            npc.removeAITask(TASK_MOVE);
            npc.getLookControl().setLookAt(threat, 30.0F, 30.0F);
        }
        return true;
    }

    @Override
    public void stop() {
        moveRetryDelay = 0;
        threat = null;
        npc.getNavigation().stop();
        npc.removeAITask(TASK_ALARM + TASK_MOVE);
    }
}
