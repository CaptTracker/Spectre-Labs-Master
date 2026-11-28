/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (third round, mod integrations): what the settlements make of the seasons of Serene Seasons.
 */
package io.github.capttracker.ancientwarfarenpc.settlement;

import io.github.capttracker.ancientwarfare.compat.season.SeasonCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * The seasons as the settlements use them: the season shown on the town hall, the length of the family year, and the winter: a little more food is used up and
 * wanted in stock, and an NPC without a home is cold. Everything goes through {@link SeasonCompat} (the core module), which knows no season at all unless
 * Serene Seasons is installed, and every part is off with {@code serene_seasons.enabled}. Server side.
 */
public final class Seasons {
    private static final long DAY = 24000L;

    private Seasons() {}

    /** True when the seasons are in use: the mod is installed and the switch is on. */
    public static boolean enabled() {
        return SettlementConfig.sereneEnabled && SeasonCompat.get().isActive();
    }

    /** The season at the position, or null when seasons are not in use or the dimension has none. */
    @Nullable
    public static SeasonCompat.Info at(Level level, BlockPos pos) {
        return enabled() ? SeasonCompat.get().info(level, pos) : null;
    }

    /** True in winter at the position (never in a tropical biome, never without seasons). */
    public static boolean isWinter(Level level, BlockPos pos) {
        SeasonCompat.Info info = at(level, pos);
        return info != null && info.isWinter();
    }

    /** The lang key of the season line of the town hall: {@code guistrings.settlement.season.<sub season>}; empty for none. */
    public static String hallKey(ServerLevel level, Settlement settlement) {
        SeasonCompat.Info info = at(level, settlement.getHallPos());
        return info == null ? "" : info.subSeason();
    }

    /** How much more food the settlement wants now: 1.0 outside winter, more in winter at the hall. */
    public static double foodFactor(Level level, BlockPos hall) {
        return SettlementConfig.sereneWinterFoodPercent > 0 && isWinter(level, hall) ? 1.0D + SettlementConfig.sereneWinterFoodPercent / 100.0D : 1.0D;
    }

    /** The extra hunger of a winter tick: the configured percent of a tick's drain, spread over the ticks (a whole point of food now and then). */
    public static int winterExtraDrain(int tickCount) {
        int percent = SettlementConfig.sereneWinterFoodPercent;
        return percent / 100 + (tickCount % 100 < percent % 100 ? 1 : 0);
    }

    /** Once a second for each settlement: follows the length of the Serene Seasons year (for the age of children). */
    public static void tick(ServerLevel level, Settlement settlement) {
        if (!enabled() || !SettlementConfig.sereneUseYear) {
            SettlementConfig.seasonYearDays = 0;
            return;
        }
        long ticks = SeasonCompat.get().yearTicks(level);
        SettlementConfig.seasonYearDays = ticks <= 0 ? 0 : (int) Math.max(1L, Math.round((double) ticks / DAY));
    }
}
