/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): building zones.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

import java.util.List;

/**
 * The plots and build footprints the server last sent to this client (plain data, safe to load anywhere). The server keeps sending them while the
 * player holds the building zone tool; entries older than {@link #MAX_AGE_MS} are ignored.
 */
public final class ClientPlotView {
    public static final long MAX_AGE_MS = 3000;

    private static volatile List<BuildingNetwork.SettlementPlots> settlements = List.of();
    private static volatile long receivedAt;

    private ClientPlotView() {}

    static void update(List<BuildingNetwork.SettlementPlots> list) {
        settlements = List.copyOf(list);
        receivedAt = System.currentTimeMillis();
    }

    /** The current plots, empty if they are stale. */
    public static List<BuildingNetwork.SettlementPlots> get() {
        return System.currentTimeMillis() - receivedAt > MAX_AGE_MS ? List.of() : settlements;
    }
}
