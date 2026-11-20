/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/compat/ftb/FTBTeamViewer.
 */
package io.github.capttracker.ancientwarfare.compat.ftb;

import io.github.capttracker.ancientwarfare.owner.ITeamViewer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Team viewer over a third-party team mod, reached through a {@link FtbTeamsBridge}. Teams of such mods
 * change through the mod's own events, so membership does not need regular rechecking (as in 1.12.2).
 */
public class FTBTeamViewer implements ITeamViewer {
    /** Namespace of the team ids returned by {@link #getPlayerTeamNames}. */
    private static final String TEAM_NAMESPACE = "ftbteams";

    private final FtbTeamsBridge bridge;

    public FTBTeamViewer(FtbTeamsBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public boolean areTeamMates(Level world, UUID player1, UUID player2, String playerName1, String playerName2) {
        return bridge.areInSameTeam(player1, player2);
    }

    @Override
    public boolean areFriendly(Level world, UUID player1, @Nullable UUID player2, String playerName1, String playerName2) {
        return bridge.areAllies(player1, player2);
    }

    @Override
    public Set<ResourceLocation> getPlayerTeamNames(Level world, UUID playerId, String playerName) {
        return bridge.getTeamId(playerId)
                .map(id -> Collections.singleton(ResourceLocation.fromNamespaceAndPath(TEAM_NAMESPACE, id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_"))))
                .orElse(Collections.emptySet());
    }

    @Override
    public boolean needsRegularMembershipRecheck() {
        return false;
    }

    @Override
    public String getName() {
        return TEAM_NAMESPACE;
    }
}
