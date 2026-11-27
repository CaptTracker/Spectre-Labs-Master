/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 12): the siege equipment that NPC soldiers crew.
 */
package io.github.capttracker.ancientwarfarenpc.ai.siege;

/** The three kinds of siege equipment the NPC siege AI uses. */
public enum SiegeKind {
    /** A siege tower: pushers walk it to the wall, climbers cross its drop bridge. */
    TOWER,
    /** A mantlet: one archer pushes it to bow range, the others shoot from behind it. */
    MANTLET,
    /** A battering ram: one soldier drives it at a gate and swings it. */
    RAM
}
