/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (third round): the settlements of the player for map mods.
 */
package io.github.capttracker.ancientwarfarenpc.settlement;

import java.util.List;

/** The settlement list the server last sent to this client (plain data, safe to load anywhere). {@link #version()} changes with each update. */
public final class ClientSettlementMap {
    private static volatile List<SettlementMapSync.Entry> settlements = List.of();
    private static volatile int version = 0;

    private ClientSettlementMap() {}

    static void update(List<SettlementMapSync.Entry> list) {
        settlements = List.copyOf(list);
        version++;
    }

    public static List<SettlementMapSync.Entry> get() {
        return settlements;
    }

    public static int version() {
        return version;
    }

    /** Forget everything (the client left the world). */
    public static void clear() {
        settlements = List.of();
        version++;
    }
}
