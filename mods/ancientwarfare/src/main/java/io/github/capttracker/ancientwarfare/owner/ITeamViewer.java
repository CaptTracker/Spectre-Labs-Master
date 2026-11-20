/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/owner/ITeamViewer.
 */
package io.github.capttracker.ancientwarfare.owner;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;

/**
 * A source of team information. Every registered viewer is asked and the answers are OR-ed together by
 * {@link TeamViewerRegistry}. The built-in viewer uses vanilla scoreboard teams.
 */
public interface ITeamViewer {
    boolean areTeamMates(Level world, UUID player1, UUID player2, String playerName1, String playerName2);

    boolean areFriendly(Level world, UUID player1, @Nullable UUID player2, String playerName1, String playerName2);

    Set<ResourceLocation> getPlayerTeamNames(Level world, UUID playerId, String playerName);

    default boolean needsRegularMembershipRecheck() {
        return true;
    }

    String getName();
}
