/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 2): the daily schedule.
 */
package io.github.capttracker.ancientwarfarenpc.settlement;

import net.minecraft.world.level.Level;

/** The part of the day the settlement NPCs are in: wake and eat, work, socialise, sleep (the bands are in {@link SettlementConfig}). */
public enum Activity {
    WAKE, WORK, SOCIALISE, SLEEP;

    /** The activity at a day time (any tick count; it is taken modulo 24000). The end of the night band up to the end of the day counts as wake. */
    public static Activity at(long dayTime) {
        int t = (int) (dayTime % 24000L);
        if (t < 0) {
            t += 24000;
        }
        if (t >= SettlementConfig.sleepEnd) {
            return WAKE;
        }
        if (t >= SettlementConfig.socialEnd) {
            return SLEEP;
        }
        if (t >= SettlementConfig.workEnd) {
            return SOCIALISE;
        }
        if (t >= SettlementConfig.wakeEnd) {
            return WORK;
        }
        return WAKE;
    }

    /** The activity in a level: always work when the schedule is off or the dimension has no day cycle. */
    public static Activity of(Level level) {
        return of(level, 0);
    }

    /** The same for an NPC whose clock is {@code shift} ticks ahead (an early riser) or behind (a night owl, negative). */
    public static Activity of(Level level, int shift) {
        if (!SettlementConfig.scheduleEnabled || level.dimensionType().hasFixedTime()) {
            return WORK;
        }
        return at(level.getDayTime() + shift);
    }
}
