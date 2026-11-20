/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 1): settlements.
 */
package io.github.capttracker.ancientwarfarenpc.settlement;

import java.util.List;

/**
 * The settlement geometry the server last sent to this client (plain data, safe to load anywhere). The server keeps sending it while the
 * player holds a boundary marker or the ledger; the entries are ignored once they are older than {@link #MAX_AGE_MS}.
 */
public final class ClientSettlementView {
    public static final long MAX_AGE_MS = 3000;

    private static volatile List<SettlementNetwork.Geometry> settlements = List.of();
    private static volatile long receivedAt;

    private ClientSettlementView() {}

    static void update(List<SettlementNetwork.Geometry> list) {
        settlements = List.copyOf(list);
        receivedAt = System.currentTimeMillis();
    }

    /** The current geometry, empty if it is stale. */
    public static List<SettlementNetwork.Geometry> get() {
        return System.currentTimeMillis() - receivedAt > MAX_AGE_MS ? List.of() : settlements;
    }
}
