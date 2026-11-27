/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 10, children): the age of a child in years and days of the game year.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.family;

import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import net.minecraft.network.chat.Component;

/**
 * Turns the age of a child (ticks) into in-game years and days: a year is {@code year_length_days} days of 24000 ticks. The numbers are made on the server
 * (the client has no copy of the server config) and shown with {@link #text}, so every player reads them in their language.
 */
public final class FamilyAge {
    private static final long DAY = 24000L;

    private FamilyAge() {}

    /** The whole years of an age in ticks. */
    public static int years(long ticks) {
        return (int) Math.max(0L, ticks / (DAY * Math.max(1, SettlementConfig.yearLengthDays)));
    }

    /** The days after the whole years of an age in ticks. */
    public static int days(long ticks) {
        long perYear = DAY * Math.max(1, SettlementConfig.yearLengthDays);
        return (int) Math.max(0L, (ticks % perYear) / DAY);
    }

    /** How far a child is on its way to adulthood, 0 to 1. */
    public static double progress(long ticks) {
        return Math.max(0.0D, Math.min(1.0D, (double) ticks / SettlementConfig.adulthoodTicks()));
    }

    /** "1 year 40 days", "40 days", "2 years" (0 days of a year is left out unless the age is under a day). */
    public static Component text(int years, int days) {
        Component y = Component.translatable(years == 1 ? "npc.age.year" : "npc.age.years", years);
        Component d = Component.translatable(days == 1 ? "npc.age.day" : "npc.age.days", days);
        if (years <= 0) {
            return d;
        }
        if (days <= 0) {
            return y;
        }
        return Component.translatable("npc.age.both", y, d);
    }

    public static Component text(long ticks) {
        return text(years(ticks), days(ticks));
    }
}
