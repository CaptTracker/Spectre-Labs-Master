/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 1): settlements.
 */
package io.github.capttracker.ancientwarfarenpc.settlement;

/** What a member NPC of a settlement was last seen doing. */
public enum MemberStatus {
    WORKING, HUNGRY, STARVING, SLEEPING, IDLE;

    public static MemberStatus byOrdinal(int ordinal) {
        MemberStatus[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : IDLE;
    }
}
