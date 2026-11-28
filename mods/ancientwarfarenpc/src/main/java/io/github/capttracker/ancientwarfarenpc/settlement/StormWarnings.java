/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (third round, mod integrations): storm warnings from Weather2.
 */
package io.github.capttracker.ancientwarfarenpc.settlement;

import io.github.capttracker.ancientwarfarenpc.compat.ModCompat;
import io.github.capttracker.ancientwarfarenpc.compat.WeatherCompat;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.entity.NpcWorker;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The storm warning of a settlement: while a severe storm, a tornado or a sandstorm is within {@code warning_distance} of the hall (sensed through the
 * optional Weather2 integration, {@link ModCompat#WEATHER}), the settlement has a warning. It is not saved (it is read again from the weather a few seconds
 * after a restart) and it has no effect without Weather2. The warning ends {@code calm_seconds} after the storm is gone.
 *
 * <p>What the warning does: the town hall overview lists it as an alert, the NPCs go indoors ({@code NpcAIScheduleGoal}) and outdoor work pauses
 * ({@link #pausesWork}), the census and the thoughts say so, and the founder gets one chat message for each storm.
 */
public final class StormWarnings {
    /** The warning of one settlement: the worst kind sensed lately, and the game time it was last sensed at. */
    private record State(WeatherCompat.Kind kind, long lastSensed) {}

    private static final Map<UUID, State> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> NEXT_SCAN = new HashMap<>();
    /** The storms the founder was told about: storm id to the game time it was told at. */
    private static final Map<UUID, Map<Long, Long>> ANNOUNCED = new HashMap<>();
    private static final long ANNOUNCE_MEMORY_TICKS = 72000L;

    private StormWarnings() {}

    // ---------------------------------------------------------------- queries

    public static boolean isWarned(UUID settlementId) {
        return !ACTIVE.isEmpty() && ACTIVE.containsKey(settlementId);
    }

    public static boolean isWarned(Settlement settlement) {
        return isWarned(settlement.getId());
    }

    /** The kind of storm of the warning of the settlement, or null. */
    @Nullable
    public static WeatherCompat.Kind kindOf(Settlement settlement) {
        State state = ACTIVE.get(settlement.getId());
        return state == null ? null : state.kind;
    }

    /** The key of the alert the town hall shows for the warning ({@code guistrings.settlement.alert.<key>}), or null without a warning. */
    @Nullable
    public static String alertKey(Settlement settlement) {
        WeatherCompat.Kind kind = kindOf(settlement);
        return kind == null ? null : "storm_" + kind.key();
    }

    /** True while the settlement of the NPC has a warning and the NPCs go indoors for it (the config switch is on). Cheap when there is no warning anywhere. */
    public static boolean isShelterWarned(NpcPlayerOwned npc) {
        return !ACTIVE.isEmpty() && SettlementConfig.weather2Shelter && warned(npc);
    }

    /** True if the NPC is under a roof and its work site (if it has one) is too: it can go on with indoor work through the storm. */
    public static boolean isSettled(NpcPlayerOwned npc) {
        return isIndoors(npc) && !worksOutdoors(npc);
    }

    /** True while the settlement of the NPC has a warning and outdoor work is paused: the NPC is outside, or its work site is. */
    public static boolean pausesWork(NpcPlayerOwned npc) {
        if (ACTIVE.isEmpty() || !SettlementConfig.weather2PauseWork) {
            return false;
        }
        return warned(npc) && (!isIndoors(npc) || worksOutdoors(npc));
    }

    private static boolean warned(NpcPlayerOwned npc) {
        UUID id = npc.getSettlementId();
        return id != null && ACTIVE.containsKey(id) && !npc.level().isClientSide();
    }

    private static boolean isIndoors(NpcPlayerOwned npc) {
        return npc.level() instanceof ServerLevel level && isRoofed(level, npc.blockPosition());
    }

    private static boolean worksOutdoors(NpcPlayerOwned npc) {
        if (npc instanceof NpcWorker worker && worker.autoWorkTarget != null && npc.level() instanceof ServerLevel level) {
            return !isRoofed(level, worker.autoWorkTarget);
        }
        return false;
    }

    /** A roof two or more blocks above (leaves count). Unlike the rain shelter it does not ask whether it rains there: sandstorms do not rain. */
    public static boolean isRoofed(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) >= pos.getY() + 2;
    }

    // ---------------------------------------------------------------- the scan

    /** Once a second from {@code SettlementData.serverTick}: reads the weather every {@code scan_seconds} and keeps the warning of the settlement. */
    public static void tick(ServerLevel level, Settlement settlement) {
        UUID id = settlement.getId();
        if (!ModCompat.WEATHER.isActive() || !SettlementConfig.weather2Enabled) {
            if (ACTIVE.remove(id) != null) {
                NEXT_SCAN.remove(id);
            }
            return;
        }
        long now = level.getGameTime();
        long interval = SettlementConfig.weather2ScanSeconds * 20L;
        Long next = NEXT_SCAN.get(id);
        if (next != null && now < next && now >= next - interval) {
            return;
        }
        NEXT_SCAN.put(id, now + interval);
        WeatherCompat.Sensed sensed = ModCompat.WEATHER.get().sense(level, settlement.getHallPos(), SettlementConfig.weather2WarningDistance);
        State old = ACTIVE.get(id);
        long calm = SettlementConfig.weather2CalmSeconds * 20L;
        if (sensed != null) {
            WeatherCompat.Kind kind = old != null && old.kind.ordinal() > sensed.kind().ordinal() && now - old.lastSensed < calm ? old.kind : sensed.kind();
            ACTIVE.put(id, new State(kind, now));
            announce(level, settlement, sensed, now);
        } else if (old != null && now - old.lastSensed >= calm) {
            ACTIVE.remove(id);
            Map<Long, Long> told = ANNOUNCED.get(id);
            if (told != null) {
                told.values().removeIf(time -> now - time > ANNOUNCE_MEMORY_TICKS);
                if (told.isEmpty()) {
                    ANNOUNCED.remove(id);
                }
            }
        }
    }

    /** Tells the founder, if online, about a storm they were not told about yet: once for each storm. */
    private static void announce(ServerLevel level, Settlement settlement, WeatherCompat.Sensed sensed, long now) {
        if (!SettlementConfig.weather2Announce) {
            return;
        }
        Map<Long, Long> told = ANNOUNCED.computeIfAbsent(settlement.getId(), k -> new HashMap<>());
        if (told.putIfAbsent(sensed.id(), now) != null) {
            return;
        }
        ServerPlayer founder = level.getServer().getPlayerList().getPlayer(settlement.getFounderId());
        if (founder != null) {
            long blocks = Math.max(0L, Math.round(sensed.distance()));
            founder.sendSystemMessage(Component.translatable("guistrings.settlement.storm_warning", settlement.getName(),
                    Component.translatable("guistrings.settlement.storm." + sensed.kind().key()), blocks));
        }
    }

    /** Forgets a settlement that is gone. */
    public static void forget(UUID settlementId) {
        ACTIVE.remove(settlementId);
        NEXT_SCAN.remove(settlementId);
        ANNOUNCED.remove(settlementId);
    }
}
