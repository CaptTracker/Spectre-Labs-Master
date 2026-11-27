/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 11): what a trader on a sea route remembers.
 */
package io.github.capttracker.ancientwarfarenpc.trade;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;

/**
 * The state of a trader that sails a sea route ({@link SeaTrade}), saved with the trader under {@code sea}: the ship it took, the stop it is heading for (or
 * moored at), the phase, and the counters of the stop. The route itself is in the chart in the trader's orders slot, so a ship that is lost or a stop that is
 * given up changes nothing there: the record of the route is kept.
 */
public final class SeaTradeState {
    /** IDLE: no ship (looking for one). BOARDING: has a ship, walks or swims to its helm. SAILING: aboard, sailing to the stop. MOORED: at the dock, trading and waiting. */
    public enum Phase { IDLE, BOARDING, SAILING, MOORED }

    /** Bits of {@link #told}: what the owner has been told since the last stop was done, so that a problem is reported once. */
    public static final int TOLD_LOST = 1;
    public static final int TOLD_BLOCKED = 2;
    public static final int TOLD_DOCK = 4;
    public static final int TOLD_SEAT = 8;
    public static final int TOLD_DISABLED = 16;

    public Phase phase = Phase.IDLE;
    @Nullable
    public UUID ship;
    /** Where the ship was last seen (not saved far: used to tell a ship that is unloaded from one that is gone). */
    @Nullable
    public BlockPos shipPos;
    /** The index of the stop the ship is sailing to or moored at. */
    public int stop = 0;
    /** Ticks in the current phase (the wait at a dock). */
    public int timer = 0;
    /** Ticks of the current leg. */
    public int legTicks = 0;
    /** Failed tries at the current stop (no route, stuck, out of time). */
    public int failures = 0;
    /** Ticks the ship has been missing from a loaded place. */
    public int missing = 0;
    /** Ticks to wait before looking for a ship or boarding again. */
    public int cooldown = 0;
    /** Ticks the ship has waited at the last reachable point for a dock that is not loaded. */
    public int dockWait = 0;
    /** The water block of the sail order the trader gave to the helmsman, or null while there is none. */
    @Nullable
    public BlockPos order;
    public int told = 0;

    public boolean hasShip() {
        return ship != null;
    }

    /** Forgets the ship (lost, given up) and goes back to looking for one; the stop and the route are kept. */
    public void dropShip() {
        ship = null;
        shipPos = null;
        phase = Phase.IDLE;
        timer = 0;
        legTicks = 0;
        failures = 0;
        missing = 0;
        dockWait = 0;
        order = null;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("phase", phase.name());
        if (ship != null) {
            tag.putUUID("ship", ship);
        }
        if (shipPos != null) {
            tag.putLong("shipPos", shipPos.asLong());
        }
        tag.putInt("stop", stop);
        tag.putInt("timer", timer);
        tag.putInt("failures", failures);
        tag.putInt("told", told);
        return tag;
    }

    public void load(CompoundTag tag) {
        try {
            phase = Phase.valueOf(tag.getString("phase").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            phase = Phase.IDLE;
        }
        ship = tag.hasUUID("ship") ? tag.getUUID("ship") : null;
        shipPos = tag.contains("shipPos") ? BlockPos.of(tag.getLong("shipPos")) : null;
        stop = Math.max(0, tag.getInt("stop"));
        timer = tag.getInt("timer");
        failures = tag.getInt("failures");
        told = tag.getInt("told");
        legTicks = 0;
        missing = 0;
        dockWait = 0;
        cooldown = 0;
        order = null; // the helmsman's order is saved with the helmsman; the trader plans the leg again
        if (ship == null) {
            phase = Phase.IDLE;
        } else if (phase == Phase.IDLE) {
            phase = Phase.BOARDING;
        }
    }
}
