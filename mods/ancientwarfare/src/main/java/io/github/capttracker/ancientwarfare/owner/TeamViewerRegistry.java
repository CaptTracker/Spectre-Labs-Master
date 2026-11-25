/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/owner/TeamViewerRegistry.
 */
package io.github.capttracker.ancientwarfare.owner;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

/** Every registered {@link ITeamViewer} is consulted and the answers are OR-ed together. */
public final class TeamViewerRegistry {
    private TeamViewerRegistry() {}

    private static final Set<ITeamViewer> TEAM_VIEWERS = new CopyOnWriteArraySet<>();

    static {
        TEAM_VIEWERS.add(new ScoreboardTeamViewer());
    }

    public static void registerTeamViewer(ITeamViewer teamViewer) {
        TEAM_VIEWERS.add(teamViewer);
    }

    public static boolean areTeamMates(Level world, UUID player1, UUID player2, String playerName1, String playerName2) {
        for (ITeamViewer teamViewer : TEAM_VIEWERS) {
            if (teamViewer.areTeamMates(world, player1, player2, playerName1, playerName2)) {
                return true;
            }
        }
        return false;
    }

    public static boolean areFriendly(Level world, UUID player1, @Nullable UUID player2, String playerName1, String playerName2) {
        for (ITeamViewer teamViewer : TEAM_VIEWERS) {
            if (teamViewer.areFriendly(world, player1, player2, playerName1, playerName2)) {
                return true;
            }
        }
        return false;
    }

    public static Set<ResourceLocation> getPlayerTeamNames(Level world, UUID playerUUID, String playerName) {
        Set<ResourceLocation> ret = new HashSet<>();
        TEAM_VIEWERS.forEach(v -> ret.addAll(v.getPlayerTeamNames(world, playerUUID, playerName)));

        return ret;
    }

    public static Set<String> getRegularlyCheckedViewerNames() {
        Set<String> ret = new HashSet<>();
        for (ITeamViewer teamViewer : TEAM_VIEWERS) {
            if (teamViewer.needsRegularMembershipRecheck()) {
                ret.add(teamViewer.getName());
            }
        }
        return ret;
    }

    public static Set<ResourceLocation> getRegularlyCheckedPlayerTeamNames(Level world, UUID playerUUID, String playerName) {
        Set<ResourceLocation> ret = new HashSet<>();
        for (ITeamViewer teamViewer : TEAM_VIEWERS) {
            if (teamViewer.needsRegularMembershipRecheck()) {
                ret.addAll(teamViewer.getPlayerTeamNames(world, playerUUID, playerName));
            }
        }
        return ret;
    }
}
