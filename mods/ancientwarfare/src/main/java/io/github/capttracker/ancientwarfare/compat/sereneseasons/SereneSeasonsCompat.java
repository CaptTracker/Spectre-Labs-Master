/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (third round, mod integrations): the entry point of the Serene Seasons integration.
 */
package io.github.capttracker.ancientwarfare.compat.sereneseasons;

import io.github.capttracker.ancientwarfare.compat.ICompat;
import io.github.capttracker.ancientwarfare.compat.season.SeasonCompat;

/**
 * Registered with the {@link io.github.capttracker.ancientwarfare.compat.CompatLoader}, which calls {@link #init()} only when Serene Seasons is loaded. This class
 * names no class of the other mod: the adapter that does is only created here.
 */
public final class SereneSeasonsCompat implements ICompat {
    @Override
    public String getModId() {
        return "sereneseasons";
    }

    @Override
    public void init() {
        SeasonCompat.set(new SereneSeasonsAdapter());
    }
}
