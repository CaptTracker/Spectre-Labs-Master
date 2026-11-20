/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/owner/ScoreboardTeamViewer.
 */
package io.github.capttracker.ancientwarfare.owner;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Vanilla scoreboard teams. Like vanilla, membership is by player name, not UUID. */
public class ScoreboardTeamViewer implements ITeamViewer {
    @Override
    public boolean areTeamMates(Level world, UUID player1, UUID player2, String playerName1, String playerName2) {
        return player1.equals(player2) || isSameTeam(world, playerName1, playerName2);
    }

    @Override
    public boolean areFriendly(Level world, UUID player1, @Nullable UUID player2, String playerName1, String playerName2) {
        return playerName1.equals(playerName2) || player1.equals(player2) || isSameTeam(world, playerName1, playerName2);
    }

    @Override
    public Set<ResourceLocation> getPlayerTeamNames(Level world, UUID playerId, String playerName) {
        PlayerTeam team = world.getScoreboard().getPlayersTeam(playerName);
        if (team == null) {
            return Collections.emptySet();
        }
        // Team names may contain characters a resource path cannot; 1.12.2 lower-cased them.
        String path = team.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
        return Collections.singleton(ResourceLocation.withDefaultNamespace(path));
    }

    @Override
    public String getName() {
        return "minecraft";
    }

    private boolean isSameTeam(Level world, String playerName1, String playerName2) {
        Team team = world.getScoreboard().getPlayersTeam(playerName1);
        return team != null && team.isAlliedTo(world.getScoreboard().getPlayersTeam(playerName2));
    }
}
