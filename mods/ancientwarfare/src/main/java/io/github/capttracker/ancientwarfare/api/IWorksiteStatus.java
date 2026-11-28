/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 6): what a worksite is waiting for, for the optional Jade and WTHIT tooltips.
 */
package io.github.capttracker.ancientwarfare.api;

import javax.annotation.Nullable;

/**
 * Optional, next to {@link IWorkSite}: a worksite that can say what it is waiting for. Read on the server thread by the look-at tooltips.
 */
public interface IWorksiteStatus {
    /** The site waits for a courier or a player to stock ladders. */
    String LADDERS = "ladders";
    /** A production site (sawmill, bakery) waits for a courier to bring raw input. */
    String INPUT = "needs_input";
    /** A production site waits for fuel. */
    String FUEL = "needs_fuel";
    /** A production site has no recipe switched on. */
    String NO_RECIPE = "no_recipe";
    /** A tannery waits for water (a water source next to it, or a water cauldron). */
    String WATER = "needs_water";
    /** A farm has seeds but none that may be sown in the current season (Serene Seasons): it still harvests and tills, but does not plant. */
    String OUT_OF_SEASON = "out_of_season";

    /** @return a short key for the missing thing (for example {@link #LADDERS}), or null if the site waits for nothing */
    @Nullable
    String getWaitingFor();
}
