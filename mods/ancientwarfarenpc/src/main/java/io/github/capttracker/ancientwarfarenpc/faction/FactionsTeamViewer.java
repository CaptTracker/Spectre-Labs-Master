/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: Core's team viewer for Spectre Factions (replaces the Ancient Warfare team system).
 */
package io.github.capttracker.ancientwarfarenpc.faction;

import io.github.capttracker.ancientwarfare.owner.ITeamViewer;
import io.github.capttracker.spectrefactions.api.ActorId;
import io.github.capttracker.spectrefactions.api.FactionsApi;
import io.github.capttracker.spectrefactions.data.Faction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Team mates are the members of one faction; friendly also covers FRIENDLY and ALLIED relations between the factions (or solo
 * players) of the two players. Server side only: on the client there is no faction data and the answers are "no".
 */
public class FactionsTeamViewer implements ITeamViewer {
    @Override
    public boolean areTeamMates(Level world, UUID player1, UUID player2, String playerName1, String playerName2) {
        if (world.isClientSide()) {
            return false;
        }
        Optional<UUID> f1 = FactionsApi.getFactionOf(player1).map(Faction::getId);
        return f1.isPresent() && f1.equals(FactionsApi.getFactionOf(player2).map(Faction::getId));
    }

    @Override
    public boolean areFriendly(Level world, UUID player1, @Nullable UUID player2, String playerName1, String playerName2) {
        if (world.isClientSide() || player2 == null) {
            return false;
        }
        ActorId a = FactionsApi.actorOf(player1);
        ActorId b = FactionsApi.actorOf(player2);
        return a.equals(b) || FactionsApi.getRelation(a, b).isFriendlyOrBetter();
    }

    @Override
    public Set<ResourceLocation> getPlayerTeamNames(Level world, UUID playerId, String playerName) {
        if (world.isClientSide()) {
            return Collections.emptySet();
        }
        return FactionsApi.getFactionOf(playerId)
                .map(f -> Collections.singleton(ResourceLocation.fromNamespaceAndPath("spectrefactions", f.getId().toString())))
                .orElse(Collections.emptySet());
    }

    @Override
    public boolean needsRegularMembershipRecheck() {
        return false;
    }

    @Override
    public String getName() {
        return "spectrefactions";
    }
}
