/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): the trader that leads a caravan.
 */
package io.github.capttracker.ancientwarfarenpc.ai.faction;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.caravan.CaravanGuests;
import io.github.capttracker.ancientwarfarenpc.caravan.CaravanLogic;
import io.github.capttracker.ancientwarfarenpc.caravan.CaravanState;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionTrader;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Walks the caravan: to the trading place of the settlement, standing there while it sells, then back the way it came. It waits whenever the wagon has
 * fallen too far behind (the horse follows the leader, see {@code CartHitch#setLeader}). While it runs it holds the move flag, so that nothing else
 * (wandering, going home) moves the trader.
 */
public class NpcAICaravanLead extends NpcAI<NpcFactionTrader> {
    private static final double WAGON_WAIT_SQ = 12.0D * 12.0D;
    private static final double ARRIVED_SQ = 16.0D;

    public NpcAICaravanLead(NpcFactionTrader npc) {
        super(npc);
        moveSpeed = 0.9D;
        setMutexBits(MOVE);
    }

    @Override
    public boolean canUse() {
        return super.canUse() && npc.getCaravan() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        moveRetryDelay = 0;
        npc.addAITask(TASK_MOVE);
    }

    @Override
    public void stop() {
        npc.removeAITask(TASK_MOVE);
        npc.getNavigation().stop();
    }

    @Override
    public void tick() {
        CaravanState state = npc.getCaravan();
        if (state == null || !(npc.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos goal = switch (state.phase) {
            case APPROACH -> state.target;
            case LEAVING -> state.exit;
            case TRADING -> null;
        };
        if (goal == null) {
            if (CaravanGuests.sleepInRentedBed(npc, level, state)) {
                return;
            }
            standAtStall(level, state);
            return;
        }
        VehicleBase wagon = CaravanLogic.wagonOf(level, state);
        if (wagon != null && npc.distanceToSqr(wagon) > WAGON_WAIT_SQ) {
            npc.getNavigation().stop(); // wait for the wagon to catch up
            return;
        }
        double sq = distanceSq(goal);
        if (sq > ARRIVED_SQ) {
            moveToPosition(goal, sq);
        } else {
            npc.getNavigation().stop();
        }
    }

    /** While selling the trader stays put and looks at the nearest player. */
    private void standAtStall(ServerLevel level, CaravanState state) {
        npc.getNavigation().stop();
        var player = level.getNearestPlayer(npc, 8.0D);
        if (player != null) {
            npc.getLookControl().setLookAt(player, 10.0F, (float) npc.getMaxHeadXRot());
        }
    }
}
