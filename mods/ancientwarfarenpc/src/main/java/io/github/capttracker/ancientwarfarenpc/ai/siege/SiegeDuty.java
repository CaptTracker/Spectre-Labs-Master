/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 12): the siege duty of one NPC.
 */
package io.github.capttracker.ancientwarfarenpc.ai.siege;

/**
 * The part one NPC plays in a {@link SiegeJob}: its role and where it is in it. Not saved (the attack order is, and the order's own goal gives a new duty after a load).
 */
public final class SiegeDuty {
    /** Where the NPC is in its role. */
    public enum Stage { WALK, RIDE, CROSS, COVER }

    public final SiegeJob job;
    public SiegeRole role;
    public Stage stage = Stage.WALK;
    /** The place among the covering archers of a mantlet. */
    public int slot = 0;
    public int ticks = 0;
    public int stageTicks = 0;
    public int waitTicks = 0;
    public int attackDelay = 0;
    public int peekTicks = 0;
    public int coverTicks = 0;

    public SiegeDuty(SiegeJob job, SiegeRole role) {
        this.job = job;
        this.role = role;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        this.stageTicks = 0;
    }
}
