package io.github.capttracker.spectrefactions.api;

import io.github.capttracker.spectrefactions.client.ClientFactionsView;
import io.github.capttracker.spectrefactions.data.Faction;
import io.github.capttracker.spectrefactions.data.FactionsSavedData;
import io.github.capttracker.spectrefactions.data.NpcRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * The public, stable API of Spectre Factions. Everything is server-side unless it says client. Methods that need the
 * world return empty or neutral results when no server is running.
 */
public final class FactionsApi {
    private static final List<Function<Entity, Optional<ActorId>>> RESOLVERS = new CopyOnWriteArrayList<>();

    private FactionsApi() {}

    public static Optional<Faction> getFaction(UUID factionId) {
        FactionsSavedData d = FactionsSavedData.current();
        return d == null ? Optional.empty() : d.faction(factionId);
    }

    public static Optional<Faction> getFactionOf(UUID playerId) {
        FactionsSavedData d = FactionsSavedData.current();
        return d == null ? Optional.empty() : d.factionOfPlayer(playerId);
    }

    /** The faction of a player if they are a member, else the player. */
    public static ActorId actorOf(UUID playerId) {
        FactionsSavedData d = FactionsSavedData.current();
        return d == null ? ActorId.player(playerId) : d.actorOfPlayer(playerId);
    }

    /** Players, plus whatever the registered resolvers return (first match wins). */
    public static Optional<ActorId> actorOf(Entity e) {
        if (e == null) {
            return Optional.empty();
        }
        if (e instanceof Player p) {
            return Optional.of(actorOf(p.getUUID()));
        }
        for (Function<Entity, Optional<ActorId>> r : RESOLVERS) {
            Optional<ActorId> a = r.apply(e);
            if (a != null && a.isPresent()) {
                return a;
            }
        }
        return Optional.empty();
    }

    public static Relation getRelation(ActorId a, ActorId b) {
        if (a.equals(b)) {
            return Relation.ALLIED;
        }
        FactionsSavedData d = FactionsSavedData.current();
        return d == null ? Relation.NEUTRAL : d.relation(a, b);
    }

    /** NEUTRAL if either entity has no actor. */
    public static Relation getRelation(Entity a, Entity b) {
        Optional<ActorId> aa = actorOf(a);
        Optional<ActorId> ab = actorOf(b);
        if (aa.isEmpty() || ab.isEmpty()) {
            return Relation.NEUTRAL;
        }
        return getRelation(aa.get(), ab.get());
    }

    /** True if both entities have the same actor (same faction, or the same solo player and their NPCs). */
    public static boolean isSameFaction(Entity a, Entity b) {
        Optional<ActorId> aa = actorOf(a);
        return aa.isPresent() && aa.equals(actorOf(b));
    }

    public static int getReputation(ActorId a, ActorId b) {
        if (a.equals(b)) {
            return 1000;
        }
        FactionsSavedData d = FactionsSavedData.current();
        return d == null ? 0 : d.reputation(a, b);
    }

    public static void adjustReputation(ActorId a, ActorId b, int delta, String reasonKey) {
        FactionsSavedData d = FactionsSavedData.current();
        if (d != null) {
            d.adjustReputation(a, b, delta, reasonKey);
        }
    }

    public static Treaty getTreaty(ActorId a, ActorId b) {
        FactionsSavedData d = FactionsSavedData.current();
        return d == null ? Treaty.NONE : d.treaty(a, b);
    }

    public static boolean isAtWar(ActorId a, ActorId b) {
        FactionsSavedData d = FactionsSavedData.current();
        return d != null && d.isWar(a, b);
    }

    /**
     * Registers (or updates) an NPC faction. Call on every server start, for example from {@code ServerAboutToStartEvent}.
     * Updating keeps the faction's reputation records.
     */
    public static void registerNpcFaction(ResourceLocation key, String displayName, ChatFormatting color,
                                          GuardMode guardMode, int defaultReputation) {
        NpcRegistry.register(key, displayName, color, guardMode, defaultReputation);
        FactionsSavedData.current(); // applies the registration now if a world is loaded
    }

    /** Default reputation between two NPC factions, for example bandit versus viking. */
    public static void setNpcFactionDefaultRelation(ResourceLocation a, ResourceLocation b, int reputation) {
        NpcRegistry.setPairDefault(a, b, reputation);
        FactionsSavedData.current();
    }

    /** The actor of an NPC faction. Needs no world. */
    public static ActorId npcFaction(ResourceLocation key) {
        return NpcRegistry.actorFor(key);
    }

    /** Resolves entities that are not players: for example an owned NPC to its owner's actor. Return empty to pass. */
    public static void registerActorResolver(Function<Entity, Optional<ActorId>> resolver) {
        RESOLVERS.add(resolver);
    }

    public static GuardMode getGuardMode(ActorId actor) {
        FactionsSavedData d = FactionsSavedData.current();
        return d == null ? GuardMode.PASSIVE : d.guardMode(actor);
    }

    /** Display name of an actor (faction name or last known player name). */
    public static String getActorName(ActorId actor) {
        FactionsSavedData d = FactionsSavedData.current();
        return d == null ? "?" : d.actorName(actor);
    }

    /** Client: the synced snapshot for screens and nameplates. */
    public static ClientFactionsView client() {
        return ClientFactionsView.INSTANCE;
    }
}
