/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: replaces npc/faction/FactionTracker, FactionEntry and the team system with Spectre Factions.
 */
package io.github.capttracker.ancientwarfarenpc.faction;

import io.github.capttracker.ancientwarfare.owner.TeamViewerRegistry;
import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.registry.FactionDefinition;
import io.github.capttracker.ancientwarfarenpc.registry.FactionRegistry;
import io.github.capttracker.spectrefactions.api.ActorId;
import io.github.capttracker.spectrefactions.api.FactionEvent;
import io.github.capttracker.spectrefactions.api.FactionsApi;
import io.github.capttracker.spectrefactions.api.GuardMode;
import io.github.capttracker.spectrefactions.api.Relation;
import io.github.capttracker.spectrefactions.client.ClientFactionsView;
import io.github.capttracker.spectrefactions.config.FactionsConfig;
import io.github.capttracker.spectrefactions.data.NpcRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The glue between the Ancient Warfare NPCs and Spectre Factions: NPC faction registration, the actor resolver, the team viewer,
 * and relation lookups that also work on the client (where the synced {@link ClientFactionsView} is used).
 *
 * <p>Wiring: {@link #init()} (called by {@code AwNpcs.registerEvents}) registers the resolver, the team viewer and the event handlers.
 */
public final class AwFactions {
    private static final String REASON_FIXED = "aw_fixed_standing";

    /** Faction actor id to the AW faction name, rebuilt by {@link #registerNpcFactions()}. */
    private static volatile Map<UUID, String> factionNamesById = Map.of();

    private AwFactions() {}

    public static void init() {
        FactionsApi.registerActorResolver(entity -> entity instanceof NpcBase npc ? npc.getFactionActor() : Optional.empty());
        TeamViewerRegistry.registerTeamViewer(new FactionsTeamViewer());
        NeoForge.EVENT_BUS.register(new Events());
    }

    /** The key of the Spectre Factions NPC faction of an AW faction. */
    public static ResourceLocation key(String factionName) {
        return ResourceLocation.fromNamespaceAndPath(AncientWarfareNPC.MOD_ID, factionName);
    }

    public static ActorId actorOfFaction(String factionName) {
        return FactionsApi.npcFaction(key(factionName));
    }

    /** Registers every AW faction as an NPC faction (safe to call again: it updates the registrations). */
    public static void registerNpcFactions() {
        Map<UUID, String> names = new HashMap<>();
        for (String name : FactionRegistry.getFactionNames()) {
            FactionDefinition def = FactionRegistry.getFaction(name);
            int standing = AWNPCStatics.getPlayerDefaultStanding(name);
            // an AW standing below 0 is hostile; the factions mod needs the reputation to be in its hostile band
            int reputation = standing < 0 ? Math.min(standing, FactionsConfig.hostileBelow) : standing;
            FactionsApi.registerNpcFaction(key(name), displayName(name), nearestColor(def.getColor()), GuardMode.AGGRESSIVE, reputation);
            names.put(NpcRegistry.idFor(key(name)), name);
        }
        for (String a : FactionRegistry.getFactionNames()) {
            for (String b : FactionRegistry.getFactionNames()) {
                if (!a.equals(b) && FactionRegistry.getFaction(a).isHostileTowards(b)) {
                    // the factions mod has one record per pair, so hostility in either direction makes the pair hostile
                    FactionsApi.setNpcFactionDefaultRelation(key(a), key(b), -1000);
                }
            }
        }
        factionNamesById = names;
    }

    private static String displayName(String name) {
        return name.isEmpty() ? name : name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    /** The {@link ChatFormatting} colour closest to an RGB colour. */
    public static ChatFormatting nearestColor(int rgb) {
        ChatFormatting best = ChatFormatting.WHITE;
        long bestDist = Long.MAX_VALUE;
        for (ChatFormatting format : ChatFormatting.values()) {
            Integer color = format.getColor();
            if (!format.isColor() || color == null) {
                continue;
            }
            long dr = ((rgb >> 16) & 0xFF) - ((color >> 16) & 0xFF);
            long dg = ((rgb >> 8) & 0xFF) - ((color >> 8) & 0xFF);
            long db = (rgb & 0xFF) - (color & 0xFF);
            long dist = dr * dr + dg * dg + db * db;
            if (dist < bestDist) {
                bestDist = dist;
                best = format;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- lookups (server and client)

    /** The actor of a player. On the client the synced view is used. */
    public static ActorId actorOfPlayer(Level level, UUID playerId) {
        if (level.isClientSide()) {
            return ClientFactionsView.INSTANCE.factionOfPlayer(playerId)
                    .map(f -> ActorId.faction(f.id())).orElseGet(() -> ActorId.player(playerId));
        }
        return FactionsApi.actorOf(playerId);
    }

    /** The actor of an entity: a player, an NPC or whatever the other resolvers know. Empty if there is none. */
    public static Optional<ActorId> actorOf(Level level, Entity entity) {
        if (entity instanceof Player player) {
            return Optional.of(actorOfPlayer(level, player.getUUID()));
        }
        if (entity instanceof NpcBase npc) {
            return npc.getFactionActor();
        }
        // tamed pets and mounts count as their owner's, so faction NPCs do not target them (upstream fix e9b9eeca6)
        if (entity instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null) {
            return Optional.of(actorOfPlayer(level, ownable.getOwnerUUID()));
        }
        return level.isClientSide() ? Optional.empty() : FactionsApi.actorOf(entity);
    }

    /** The relation of two actors. On the client only the relations of the local player's actor are known; the rest is NEUTRAL. */
    public static Relation relation(Level level, ActorId a, ActorId b) {
        if (a.equals(b)) {
            return Relation.ALLIED;
        }
        if (!level.isClientSide()) {
            return FactionsApi.getRelation(a, b);
        }
        ClientFactionsView view = ClientFactionsView.INSTANCE;
        UUID mine = view.myFactionId();
        ActorId local = mine != null ? ActorId.faction(mine) : view.localPlayerId().map(ActorId::player).orElse(null);
        if (local == null) {
            return Relation.NEUTRAL;
        }
        if (local.equals(a)) {
            return view.relationWith(b).map(r -> r.relation()).orElse(Relation.NEUTRAL);
        }
        if (local.equals(b)) {
            return view.relationWith(a).map(r -> r.relation()).orElse(Relation.NEUTRAL);
        }
        return Relation.NEUTRAL;
    }

    /** The AW faction name of an NPC faction actor, if the actor is one. */
    @Nullable
    public static String factionNameOf(ActorId actor) {
        return actor.kind() == ActorId.Kind.FACTION ? factionNamesById.get(actor.id()) : null;
    }

    // ---------------------------------------------------------------- events

    public static final class Events {
        @SubscribeEvent
        public void onServerAboutToStart(ServerAboutToStartEvent event) {
            registerNpcFactions();
        }

        /** The data packs were reloaded ({@code /reload}); the faction definitions may have changed. */
        @SubscribeEvent
        public void onDatapackSync(OnDatapackSyncEvent event) {
            if (event.getPlayer() == null) {
                registerNpcFactions();
            }
        }

        /** Factions whose standing cannot change (the "standing_can_change": false of the data) get their reputation put back. */
        @SubscribeEvent
        public void onReputationChanged(FactionEvent.ReputationChanged event) {
            if (REASON_FIXED.equals(event.getReasonKey())) {
                return;
            }
            fixIfNeeded(event.getA(), event.getB(), event.getOldReputation(), event.getNewReputation());
            fixIfNeeded(event.getB(), event.getA(), event.getOldReputation(), event.getNewReputation());
        }

        private static void fixIfNeeded(ActorId npcSide, ActorId other, int oldRep, int newRep) {
            String name = factionNameOf(npcSide);
            if (name == null || factionNameOf(other) != null) {
                return;
            }
            if (FactionRegistry.getFaction(name).getStandingSettings().canPlayerStandingChange()) {
                return;
            }
            FactionsApi.adjustReputation(npcSide, other, oldRep - newRep, REASON_FIXED);
        }
    }
}
