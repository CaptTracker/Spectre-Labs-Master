/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 12): what a soldier does in a siege order.
 */
package io.github.capttracker.ancientwarfarenpc.ai.siege;

/** The role of an NPC in a {@link SiegeJob}. */
public enum SiegeRole {
    /** Takes the driver or a pusher seat of a siege tower and pushes it to the wall (the first one steers). After the dock he climbs, if there is room. */
    PUSHER,
    /** Takes a ladder seat of a siege tower, rides to the wall, climbs when it has docked and crosses the bridge onto the wall top. */
    CLIMBER,
    /** The archer who pushes a mantlet (its one seat). */
    MANTLET_PUSHER,
    /** An archer who follows a mantlet and shoots from behind it. */
    COVER,
    /** The soldier who drives a battering ram at a gate and swings it. */
    RAM_CREW;

    /** True for the roles that sit in the driver or pusher seat of a vehicle and move it. */
    public boolean pushes() {
        return this == PUSHER || this == MANTLET_PUSHER || this == RAM_CREW;
    }
}
