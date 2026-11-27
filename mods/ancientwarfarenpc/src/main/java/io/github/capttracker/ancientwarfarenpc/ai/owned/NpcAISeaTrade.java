/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 11): the trader on its way to the cargo ship of its sea route.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.ai.vehicle.ShipApproach;
import io.github.capttracker.ancientwarfarenpc.entity.NpcTrader;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.trade.SeaTrade;
import io.github.capttracker.ancientwarfarenpc.trade.SeaTradeState;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import net.minecraft.server.level.ServerLevel;

/**
 * The part of the sea route of a trader ({@link SeaTrade}) that needs the trader's feet: walking, or swimming out, to the cargo ship it took and boarding it
 * (the helm if it is free, else a crew seat beside the owned helmsman), with the same approach the crew of a ship uses ({@link ShipApproach}). It holds the
 * move flag while the trader is on its way to the ship. Everything else of the route (planning, sailing, loading) runs from the trader's tick, because the trader is
 * seated, and so not free to move, while it sails.
 */
public class NpcAISeaTrade extends NpcAI<NpcTrader> {
    private final ShipApproach approach = new ShipApproach();

    public NpcAISeaTrade(NpcTrader npc) {
        super(npc);
        setMutexBits(MOVE);
    }

    @Override
    public boolean canUse() {
        SeaTradeState state = npc.getSeaState();
        return SettlementConfig.seaTradeEnabled && !npc.level().isClientSide && npc.getIsAIEnabled() && state.phase == SeaTradeState.Phase.BOARDING
                && state.cooldown <= 0 && npc.getSeaRoute() != null && npc.getSeaRoute().size() >= 2 && !npc.isPassenger();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        approach.reset();
        npc.addAITask(TASK_MOVE);
    }

    @Override
    public void stop() {
        npc.removeAITask(TASK_MOVE);
        approach.reset();
    }

    @Override
    public void tick() {
        if (!(npc.level() instanceof ServerLevel level)) {
            return;
        }
        SeaTradeState state = npc.getSeaState();
        VehicleBase ship = SeaTrade.shipOf(level, state);
        if (ship == null) {
            return; // not loaded, or lost: the trader's tick sorts it out
        }
        ShipApproach.Status status = approach.tick(npc, ship, SeaTrade.boardingRoles(ship), SettlementConfig.seaTradeBindRange + 16.0D);
        if (status == ShipApproach.Status.NO_SEAT || status == ShipApproach.Status.FAILED) {
            SeaTrade.boardingFailed(npc, status);
            approach.reset();
        }
    }
}
