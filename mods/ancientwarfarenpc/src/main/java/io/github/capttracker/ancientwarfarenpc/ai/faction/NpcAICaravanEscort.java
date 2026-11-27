/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): a guard that walks with its caravan.
 */
package io.github.capttracker.ancientwarfarenpc.ai.faction;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.caravan.CaravanGuests;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionTrader;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFaction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * Keeps an escort of a caravan within a few blocks of the caravan's leader. It steps aside when the guard has a target (the melee goal fights) and works
 * only for a guard that has a caravan leader set ({@code NpcFaction#getCaravanLeader}). A guard whose leader has not been found for two minutes goes back
 * to ordinary life and may despawn.
 */
public class NpcAICaravanEscort extends NpcAI<NpcFaction> {
    private static final double CLOSE_SQ = 5.0D * 5.0D;
    private static final int LOST_TICKS = 2400;

    @Nullable
    private Entity leader;
    private int lost;

    public NpcAICaravanEscort(NpcFaction npc) {
        super(npc);
        moveSpeed = 1.05D;
        setMutexBits(MOVE);
    }

    @Override
    public boolean canUse() {
        if (!super.canUse() || npc.getCaravanLeader() == null || npc.getTarget() != null || npc.isPassenger() || !(npc.level() instanceof ServerLevel level)) {
            return false;
        }
        Entity found = level.getEntity(npc.getCaravanLeader());
        if (found == null || !found.isAlive()) {
            if (++lost > LOST_TICKS / 2) { // checked every second tick
                npc.setCaravanLeader(null);
                npc.setCanDespawn();
                lost = 0;
            }
            return false;
        }
        lost = 0;
        leader = found;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        moveRetryDelay = 0;
        npc.addAITask(TASK_FOLLOW);
    }

    @Override
    public void stop() {
        leader = null;
        if (npc.isSleeping()) {
            npc.wakeUp();
        }
        npc.removeAITask(TASK_FOLLOW + TASK_MOVE);
    }

    @Override
    public void tick() {
        if (leader == null) {
            return;
        }
        if (leader instanceof NpcFactionTrader trader && trader.getCaravan() != null && npc.level() instanceof ServerLevel level
                && CaravanGuests.sleepInRentedBed(npc, level, trader.getCaravan())) {
            return; // asleep in a bed rented at the inn
        }
        double distance = npc.distanceToSqr(leader);
        if (distance > CLOSE_SQ) {
            npc.addAITask(TASK_MOVE);
            moveToEntity(leader, distance);
        } else {
            npc.removeAITask(TASK_MOVE);
            npc.getNavigation().stop();
        }
    }
}
