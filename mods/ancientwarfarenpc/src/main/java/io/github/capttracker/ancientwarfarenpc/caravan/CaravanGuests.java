/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): the beds of an inn rented to the NPCs of a caravan.
 */
package io.github.capttracker.ancientwarfarenpc.caravan;

import io.github.capttracker.ancientwarfare.api.IInn;
import io.github.capttracker.ancientwarfarenpc.caravan.CaravanState.Phase;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFaction;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionTrader;
import io.github.capttracker.ancientwarfarenpc.inn.InnApi;
import io.github.capttracker.ancientwarfarenpc.settlement.Activity;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Inn beds for the visiting caravans. While a caravan sells at a settlement that has an open inn with free beds, its leader and guards each rent a bed
 * until the end of the stay and pay the rent in coins into the pantry of the inn ({@link InnApi#rentToCaravan}); the rental is kept by the inn. At night
 * (the sleep band) a caravan NPC that holds a bed walks to it and sleeps there, and gets up in the morning or when the caravan leaves.
 */
public final class CaravanGuests {
    private CaravanGuests() {}

    /** Called by the leader every few seconds while the caravan trades: the leader and the guards rent beds. */
    public static void rentBeds(ServerLevel level, Settlement settlement, NpcFactionTrader leader, CaravanState state) {
        InnApi.rentToCaravan(level, settlement, leader.getUUID(), state.stayUntil);
        for (NpcFaction guard : CaravanLogic.guardsOf(level, state)) {
            InnApi.rentToCaravan(level, settlement, guard.getUUID(), state.stayUntil);
        }
    }

    /**
     * Called by the goals of the caravan NPCs every tick while the caravan trades. Returns true if the NPC is busy with its bed (it walks to it or
     * sleeps) and the goal should do nothing else this tick.
     */
    public static boolean sleepInRentedBed(NpcFaction npc, ServerLevel level, CaravanState state) {
        Optional<Settlement> settlement = state.phase == Phase.TRADING ? SettlementData.get(level).byId(state.settlement) : Optional.empty();
        BlockPos bed = null;
        if (settlement.isPresent() && Activity.of(level) == Activity.SLEEP) {
            long now = level.getGameTime();
            for (IInn inn : InnApi.innsOf(level, settlement.get())) {
                Optional<BlockPos> rented = inn.getRentedBed(npc.getUUID(), now);
                if (rented.isPresent()) {
                    bed = rented.get();
                    break;
                }
            }
        }
        if (bed == null) {
            if (npc.isSleeping()) {
                npc.wakeUp();
            }
            return false;
        }
        if (npc.isSleeping()) {
            return true;
        }
        if (npc.distanceToSqr(bed.getX() + 0.5D, bed.getY(), bed.getZ() + 0.5D) > 6.25D) {
            npc.getNavigation().moveTo(bed.getX() + 0.5D, bed.getY(), bed.getZ() + 0.5D, 1.0D);
            return true;
        }
        npc.assignBed(bed);
        if (npc.lieDown(bed)) {
            npc.getNavigation().stop();
        }
        return true;
    }
}
