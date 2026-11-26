/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): building designs.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

/** A design that cannot be used; the message says why and is logged as a warning by the loader. */
public final class DesignException extends Exception {
    public DesignException(String message) {
        super(message);
    }
}
