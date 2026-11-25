/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/compat/ftb/FTBCompat.
 */
package io.github.capttracker.ancientwarfare.compat.ftb;

import io.github.capttracker.ancientwarfare.compat.ICompat;
import io.github.capttracker.ancientwarfare.owner.TeamViewerRegistry;

/**
 * Registers the team viewer for the FTB team mod when it is installed. The mod id is {@code ftbteams}
 * (1.12.2 used {@code ftblib}). The adapter to the team mod's API is not part of this port because it needs
 * the mod as a compile dependency: with the default {@link FtbTeamsBridge#NONE} the registered viewer reports
 * no teams, which is harmless.
 */
public class FTBCompat implements ICompat {
    private static volatile FtbTeamsBridge bridge = FtbTeamsBridge.NONE;

    /** Installs the adapter to the team mod. Call before {@link io.github.capttracker.ancientwarfare.compat.CompatLoader#init()} runs. */
    public static void setBridge(FtbTeamsBridge newBridge) {
        bridge = newBridge;
    }

    @Override
    public String getModId() {
        return "ftbteams";
    }

    @Override
    public void init() {
        TeamViewerRegistry.registerTeamViewer(new FTBTeamViewer(bridge));
    }
}
