/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): when the faction caravans come.
 */
package io.github.capttracker.ancientwarfarenpc.caravan;

import io.github.capttracker.ancientwarfarenpc.faction.AwFactions;
import io.github.capttracker.ancientwarfarenpc.registry.FactionRegistry;
import io.github.capttracker.ancientwarfarenpc.registry.NpcDefaultsRegistry;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementData;
import io.github.capttracker.spectrefactions.api.FactionsApi;
import io.github.capttracker.spectrefactions.api.Relation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every {@code caravan_interval_days} in-game days (the interval is counted in game time and saved, so it survives a restart) each settlement has a
 * {@code caravan_chance} (50%) of a caravan coming. The faction is drawn from those that trade (their defaults have a trader and a soldier) and whose relation
 * to the settlement owner is NEUTRAL or better. A caravan that finds no loaded place near a player is retried every five seconds for five minutes.
 */
public final class CaravanScheduler {
    public static final CaravanScheduler INSTANCE = new CaravanScheduler();
    private static final int CHECK_INTERVAL = 100;
    private static final long PENDING_TICKS = 6000L;
    /** A record of a caravan whose leader never reported back for this long is dropped. */
    private static final long STALE_TICKS = 72000L;

    private CaravanScheduler() {}

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_INTERVAL != 0 || !SettlementConfig.caravansEnabled) {
            return;
        }
        tick(server);
    }

    private static void tick(MinecraftServer server) {
        CaravanData data = CaravanData.get(server);
        long now = server.overworld().getGameTime();
        for (Map.Entry<UUID, CaravanData.Active> entry : data.active().entrySet()) {
            if (now - entry.getValue().start > STALE_TICKS) {
                data.forget(entry.getKey());
            }
        }
        if (data.lastRoll < 0) {
            data.markRolled(now);
        } else if (now - data.lastRoll >= (long) SettlementConfig.caravanIntervalDays * 24000L) {
            data.markRolled(now);
            roll(server, data, now);
        }
        for (CaravanData.Pending pending : data.pending()) {
            Settlement settlement = SettlementData.get(server).byId(pending.settlement).orElse(null);
            if (settlement == null || now > pending.expires) {
                data.removePending(pending);
                continue;
            }
            ServerLevel level = server.getLevel(settlement.getDimension());
            if (level != null && level.isLoaded(settlement.getHallPos()) && spawnSafely(level, settlement, pending.faction)) {
                data.removePending(pending);
            }
        }
    }

    /** A caravan that cannot be built must not take the server tick down: the error is logged and the request is dropped. */
    private static boolean spawnSafely(ServerLevel level, Settlement settlement, String faction) {
        try {
            return CaravanSpawner.spawn(level, settlement, faction);
        } catch (RuntimeException e) {
            com.mojang.logging.LogUtils.getLogger().error("A caravan of {} could not be built for settlement {}", faction, settlement.getName(), e);
            return true; // dropped, not retried
        }
    }

    private static void roll(MinecraftServer server, CaravanData data, long now) {
        var random = server.overworld().getRandom();
        for (Settlement settlement : SettlementData.get(server).all()) {
            if (data.hasActive(settlement.getId()) || data.isWaiting(settlement.getId())) {
                continue;
            }
            if (data.activeCount() + data.pending().size() >= SettlementConfig.caravanMaxActive || random.nextDouble() >= SettlementConfig.caravanChance) {
                continue;
            }
            List<String> factions = eligibleFactions(settlement);
            if (factions.isEmpty()) {
                continue;
            }
            data.addPending(settlement.getId(), factions.get(random.nextInt(factions.size())), now + PENDING_TICKS);
        }
    }

    /** The factions that trade (have a trader and a soldier) and are NEUTRAL or better towards the settlement's owner. */
    public static List<String> eligibleFactions(Settlement settlement) {
        List<String> result = new ArrayList<>();
        for (String name : FactionRegistry.getFactionNames()) {
            if (NpcDefaultsRegistry.getFactionNpcDefault(name, "trader") == null || NpcDefaultsRegistry.getFactionNpcDefault(name, "soldier") == null) {
                continue;
            }
            Relation relation = FactionsApi.getRelation(settlement.getOwnerActor(), AwFactions.actorOfFaction(name));
            if (relation != Relation.HOSTILE) {
                result.add(name);
            }
        }
        return result;
    }
}
