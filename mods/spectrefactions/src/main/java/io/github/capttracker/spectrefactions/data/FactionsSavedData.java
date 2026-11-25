package io.github.capttracker.spectrefactions.data;

import io.github.capttracker.spectrefactions.api.ActorId;
import io.github.capttracker.spectrefactions.api.FactionEvent;
import io.github.capttracker.spectrefactions.api.FactionType;
import io.github.capttracker.spectrefactions.api.GuardMode;
import io.github.capttracker.spectrefactions.api.ProposalType;
import io.github.capttracker.spectrefactions.api.Rank;
import io.github.capttracker.spectrefactions.api.Relation;
import io.github.capttracker.spectrefactions.api.Treaty;
import io.github.capttracker.spectrefactions.config.FactionsConfig;
import io.github.capttracker.spectrefactions.service.FactionMessages;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * All faction data, saved with the overworld under the name {@code spectrefactions}: the factions, the relation
 * records, each player's last known name, the pending proposals and the invites (which live on their faction).
 *
 * <p>Read methods are safe for anyone; the mutating methods are for the faction service and the API. Every
 * mutation marks the data dirty and requests a client sync.
 */
public final class FactionsSavedData extends SavedData {
    public static final String NAME = "spectrefactions";
    private static final SavedData.Factory<FactionsSavedData> FACTORY = new SavedData.Factory<>(FactionsSavedData::new, FactionsSavedData::load);

    /** Set whenever something a client could see changed. The sync manager clears it after sending. */
    public static volatile boolean syncDirty = false;

    /** An ordered pair of actors, the key of a relation record. */
    public record PairKey(ActorId lo, ActorId hi) {
        public static PairKey of(ActorId a, ActorId b) {
            return a.compareTo(b) <= 0 ? new PairKey(a, b) : new PairKey(b, a);
        }

        public ActorId other(ActorId me) {
            return lo.equals(me) ? hi : lo;
        }

        public boolean involves(ActorId a) {
            return lo.equals(a) || hi.equals(a);
        }
    }

    private final LinkedHashMap<UUID, Faction> factions = new LinkedHashMap<>();
    private final Map<UUID, UUID> playerFaction = new HashMap<>();
    private final Map<PairKey, RelationRecord> records = new HashMap<>();
    private final Map<UUID, String> playerNames = new HashMap<>();
    private final Map<UUID, GuardMode> playerGuardModes = new HashMap<>();
    private final List<Proposal> proposals = new ArrayList<>();
    @Nullable
    private MinecraftServer server;
    private int appliedRegistryVersion = -1;

    // ---- access ----

    /** The data of a running server. Applies pending NPC faction registrations. */
    @Nullable
    public static FactionsSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return null;
        }
        FactionsSavedData data = overworld.getDataStorage().computeIfAbsent(FACTORY, NAME);
        data.server = server;
        data.applyRegistrations();
        return data;
    }

    /** The data of the current server, or null when no world is loaded. */
    @Nullable
    public static FactionsSavedData current() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        return s == null ? null : get(s);
    }

    @Nullable
    public MinecraftServer getServer() {
        return server;
    }

    public long now() {
        if (server != null) {
            ServerLevel ow = server.getLevel(Level.OVERWORLD);
            if (ow != null) {
                return ow.getGameTime();
            }
        }
        return 0;
    }

    public void markChanged() {
        setDirty();
        syncDirty = true;
    }

    // ---- factions: queries ----

    public Collection<Faction> factions() {
        return Collections.unmodifiableCollection(factions.values());
    }

    public Optional<Faction> faction(UUID id) {
        return Optional.ofNullable(factions.get(id));
    }

    public Optional<Faction> factionOfPlayer(UUID player) {
        UUID id = playerFaction.get(player);
        return id == null ? Optional.empty() : Optional.ofNullable(factions.get(id));
    }

    public Optional<Faction> findFaction(String name) {
        for (Faction f : factions.values()) {
            if (f.name.equalsIgnoreCase(name)) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }

    public boolean nameTaken(String name, @Nullable UUID ignore) {
        for (Faction f : factions.values()) {
            if (f.name.equalsIgnoreCase(name) && !f.id.equals(ignore)) {
                return true;
            }
        }
        return false;
    }

    public ActorId actorOfPlayer(UUID player) {
        UUID f = playerFaction.get(player);
        return f != null ? ActorId.faction(f) : ActorId.player(player);
    }

    public GuardMode guardMode(ActorId actor) {
        if (actor.isFaction()) {
            Faction f = factions.get(actor.id());
            return f != null ? f.guardMode : GuardMode.PASSIVE;
        }
        return playerGuardModes.getOrDefault(actor.id(), FactionsConfig.defaultPlayerGuardMode);
    }

    public void setPlayerGuardMode(UUID player, GuardMode mode) {
        playerGuardModes.put(player, mode);
        markChanged();
    }

    /** Last known name of a player, or a shortened UUID. */
    public String playerName(UUID id) {
        String n = playerNames.get(id);
        return n != null ? n : id.toString().substring(0, 8);
    }

    @Nullable
    public String knownPlayerName(UUID id) {
        return playerNames.get(id);
    }

    public void rememberName(UUID id, String name) {
        if (!name.equals(playerNames.put(id, name))) {
            markChanged();
        }
    }

    @Nullable
    public UUID findPlayerByName(String name) {
        for (Map.Entry<UUID, String> e : playerNames.entrySet()) {
            if (e.getValue().equalsIgnoreCase(name)) {
                return e.getKey();
            }
        }
        return null;
    }

    public Collection<UUID> knownPlayers() {
        return Collections.unmodifiableCollection(playerNames.keySet());
    }

    /** Display name of an actor: faction name or player name. */
    public String actorName(ActorId actor) {
        if (actor.isFaction()) {
            Faction f = factions.get(actor.id());
            return f != null ? f.name : "?";
        }
        return playerName(actor.id());
    }

    // ---- factions: mutations ----

    public Faction createPlayerFaction(String name, String tag, ChatFormatting color, UUID leader) {
        Faction f = new Faction(UUID.randomUUID(), name, tag, color, FactionType.PLAYER, FactionsConfig.defaultOpen,
                FactionsConfig.defaultPlayerGuardMode, now());
        factions.put(f.id, f);
        f.members.put(leader, Rank.LEADER);
        playerFaction.put(leader, f.id);
        clearInvitesFor(leader);
        markChanged();
        NeoForge.EVENT_BUS.post(new FactionEvent.Created(f));
        NeoForge.EVENT_BUS.post(new FactionEvent.MemberJoined(f, leader));
        return f;
    }

    public void addMember(Faction f, UUID player) {
        f.members.put(player, Rank.MEMBER);
        playerFaction.put(player, f.id);
        clearInvitesFor(player);
        markChanged();
        NeoForge.EVENT_BUS.post(new FactionEvent.MemberJoined(f, player));
    }

    /**
     * Removes a member. If they were the leader the oldest officer (else the oldest member) becomes leader.
     *
     * @return true if the faction had no members left and was disbanded
     */
    public boolean removeMember(Faction f, UUID player) {
        Rank rank = f.members.remove(player);
        if (rank == null) {
            return false;
        }
        playerFaction.remove(player);
        NeoForge.EVENT_BUS.post(new FactionEvent.MemberLeft(f, player));
        if (f.members.isEmpty()) {
            disband(f);
            return true;
        }
        if (rank == Rank.LEADER) {
            UUID next = null;
            for (Map.Entry<UUID, Rank> e : f.members.entrySet()) {
                if (e.getValue() == Rank.OFFICER) {
                    next = e.getKey();
                    break;
                }
            }
            if (next == null) {
                next = f.members.keySet().iterator().next();
            }
            f.members.put(next, Rank.LEADER);
            if (server != null) {
                FactionMessages.notifyFaction(server, f, net.minecraft.network.chat.Component.translatable(
                        "message.spectrefactions.new_leader", playerName(next)));
            }
        }
        markChanged();
        return false;
    }

    public void setRank(Faction f, UUID player, Rank rank) {
        if (f.members.containsKey(player)) {
            f.members.put(player, rank);
            markChanged();
        }
    }

    /** Makes {@code newLeader} the leader and the old leader an officer. */
    public void transferLeadership(Faction f, UUID newLeader) {
        UUID old = f.getLeader();
        if (old != null) {
            f.members.put(old, Rank.OFFICER);
        }
        f.members.put(newLeader, Rank.LEADER);
        markChanged();
    }

    public void setName(Faction f, String name) {
        f.name = name;
        markChanged();
    }

    public void setTag(Faction f, String tag) {
        f.tag = tag;
        markChanged();
    }

    public void setColor(Faction f, ChatFormatting color) {
        f.color = color;
        markChanged();
    }

    public void setOpen(Faction f, boolean open) {
        f.open = open;
        markChanged();
    }

    public void setGuardMode(Faction f, GuardMode mode) {
        f.guardMode = mode;
        markChanged();
    }

    public void addInvite(Faction f, UUID player, long expiry) {
        f.invites.put(player, expiry);
        markChanged();
    }

    public boolean hasValidInvite(Faction f, UUID player) {
        Long exp = f.invites.get(player);
        return exp != null && exp > now();
    }

    public void clearInvitesFor(UUID player) {
        boolean any = false;
        for (Faction f : factions.values()) {
            any |= f.invites.remove(player) != null;
        }
        if (any) {
            markChanged();
        }
    }

    /** Deletes a faction and everything that refers to it. Posts {@link FactionEvent.Disbanded}. */
    public void disband(Faction f) {
        if (factions.remove(f.id) == null) {
            return;
        }
        for (UUID m : f.members.keySet()) {
            playerFaction.remove(m, f.id);
        }
        ActorId actor = f.actor();
        records.keySet().removeIf(k -> k.involves(actor));
        proposals.removeIf(p -> p.from().equals(f.id) || p.to().equals(f.id));
        markChanged();
        NeoForge.EVENT_BUS.post(new FactionEvent.Disbanded(f));
    }

    // ---- NPC factions ----

    private void applyRegistrations() {
        int v = NpcRegistry.version();
        if (v == appliedRegistryVersion) {
            return;
        }
        appliedRegistryVersion = v;
        for (NpcRegistry.Entry e : NpcRegistry.entries()) {
            UUID id = NpcRegistry.idFor(e.key());
            String name = e.name();
            if (nameTaken(name, id)) {
                name = name + " (NPC)";
            }
            Faction f = factions.get(id);
            if (f == null) {
                f = new Faction(id, name, deriveTag(e.name()), e.color(), FactionType.NPC, false, e.guardMode(), now());
                factions.put(id, f);
            } else {
                f.name = name;
                f.color = e.color();
                f.guardMode = e.guardMode();
            }
            f.key = e.key().toString();
            f.defaultReputation = e.defaultReputation();
        }
        markChanged();
    }

    private static String deriveTag(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (Character.isLetterOrDigit(c) && sb.length() < 3) {
                sb.append(Character.toUpperCase(c));
            }
        }
        while (sb.length() < 2) {
            sb.append('X');
        }
        return sb.toString();
    }

    // ---- relations ----

    public int defaultReputation(ActorId a, ActorId b) {
        Faction fa = a.isFaction() ? factions.get(a.id()) : null;
        Faction fb = b.isFaction() ? factions.get(b.id()) : null;
        boolean na = fa != null && fa.isNpc();
        boolean nb = fb != null && fb.isNpc();
        if (na && nb) {
            Integer pair = NpcRegistry.pairDefault(a, b);
            return pair != null ? pair : FactionsConfig.defaultReputation;
        }
        if (na) {
            return fa.defaultReputation;
        }
        if (nb) {
            return fb.defaultReputation;
        }
        return FactionsConfig.defaultReputation;
    }

    @Nullable
    public RelationRecord record(ActorId a, ActorId b) {
        return records.get(PairKey.of(a, b));
    }

    public Map<PairKey, RelationRecord> recordsView() {
        return Collections.unmodifiableMap(records);
    }

    public int reputation(ActorId a, ActorId b) {
        if (a.equals(b)) {
            return 1000;
        }
        RelationRecord r = record(a, b);
        return r != null ? r.reputation : defaultReputation(a, b);
    }

    public Treaty treaty(ActorId a, ActorId b) {
        RelationRecord r = record(a, b);
        return r != null ? r.treaty : Treaty.NONE;
    }

    public boolean isWar(ActorId a, ActorId b) {
        RelationRecord r = record(a, b);
        return r != null && r.war;
    }

    /** The effective relation, worked out as the design says: same actor, war, reputation band, treaty. */
    public Relation relation(ActorId a, ActorId b) {
        if (a.equals(b)) {
            return Relation.ALLIED;
        }
        RelationRecord r = record(a, b);
        int rep = r != null ? r.reputation : defaultReputation(a, b);
        if (r != null && r.war) {
            return Relation.HOSTILE;
        }
        Relation band;
        if (rep <= FactionsConfig.hostileBelow) {
            band = Relation.HOSTILE;
        } else if (rep >= FactionsConfig.alliedAbove) {
            band = Relation.ALLIED;
        } else if (rep >= FactionsConfig.friendlyAbove) {
            band = Relation.FRIENDLY;
        } else {
            band = Relation.NEUTRAL;
        }
        if (!FactionsConfig.reputationCanAlly && band.isFriendlyOrBetter()) {
            band = Relation.NEUTRAL;
        }
        if (band != Relation.HOSTILE && r != null) {
            if (r.treaty == Treaty.ALLIANCE) {
                band = Relation.max(band, Relation.ALLIED);
            } else if (r.treaty == Treaty.PEACE) {
                band = Relation.max(band, Relation.FRIENDLY);
            }
        }
        return band;
    }

    public void adjustReputation(ActorId a, ActorId b, int delta, String reason) {
        if (delta == 0) {
            return;
        }
        mutate(a, b, reason, r -> r.reputation += delta);
    }

    public void setReputation(ActorId a, ActorId b, int value, String reason) {
        mutate(a, b, reason, r -> r.reputation = value);
    }

    /** War: takes effect at once, cancels any treaty and costs {@code penalty} reputation. */
    public void declareWar(ActorId a, ActorId b, int penalty) {
        long now = now();
        removeProposalsBetween(a, b);
        mutate(a, b, "war", r -> {
            r.war = true;
            r.warSince = now;
            r.treaty = Treaty.NONE;
            r.reputation -= penalty;
        });
    }

    /** Ends a war and sets a peace treaty. Reputation is lifted just out of the hostile band so the treaty holds. */
    public void makePeace(ActorId a, ActorId b) {
        removeProposalsBetween(a, b);
        mutate(a, b, "peace", r -> {
            r.war = false;
            r.treaty = Treaty.PEACE;
            r.reputation = Math.max(r.reputation, FactionsConfig.hostileBelow + 1);
        });
    }

    public void makeAlliance(ActorId a, ActorId b) {
        removeProposalsBetween(a, b);
        mutate(a, b, "alliance", r -> r.treaty = Treaty.ALLIANCE);
    }

    public void breakTreaty(ActorId a, ActorId b, int penalty) {
        removeProposalsBetween(a, b);
        mutate(a, b, "treaty_break", r -> {
            r.treaty = Treaty.NONE;
            r.reputation -= penalty;
        });
    }

    private void mutate(ActorId a, ActorId b, String reason, Consumer<RelationRecord> change) {
        if (a.equals(b)) {
            return;
        }
        PairKey key = PairKey.of(a, b);
        int def = defaultReputation(a, b);
        Relation oldRel = relation(a, b);
        RelationRecord existing = records.get(key);
        int oldRep = existing != null ? existing.reputation : def;
        RelationRecord rec = existing != null ? existing : new RelationRecord(def);
        change.accept(rec);
        rec.reputation = Math.max(-1000, Math.min(1000, rec.reputation));
        boolean broke = false;
        if (rec.treaty != Treaty.NONE && !rec.war && rec.reputation <= FactionsConfig.hostileBelow) {
            rec.treaty = Treaty.NONE;
            broke = true;
        }
        if (rec.isDefault(def)) {
            records.remove(key);
        } else {
            records.put(key, rec);
        }
        markChanged();
        Relation newRel = relation(a, b);
        if (broke && server != null) {
            FactionMessages.treatyBroken(server, this, a, b);
        }
        if (oldRep != rec.reputation) {
            NeoForge.EVENT_BUS.post(new FactionEvent.ReputationChanged(a, b, oldRep, rec.reputation, reason));
        }
        if (oldRel != newRel) {
            if (server != null) {
                FactionMessages.relationChanged(server, this, a, b, newRel);
            }
            NeoForge.EVENT_BUS.post(new FactionEvent.RelationChanged(a, b, oldRel, newRel));
        }
    }

    // ---- proposals ----

    public List<Proposal> proposals() {
        return Collections.unmodifiableList(proposals);
    }

    public Optional<Proposal> findProposal(UUID from, UUID to) {
        long now = now();
        for (Proposal p : proposals) {
            if (p.from().equals(from) && p.to().equals(to) && p.expiry() > now) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    public void addProposal(UUID from, UUID to, ProposalType type, long expiry) {
        proposals.removeIf(p -> p.from().equals(from) && p.to().equals(to));
        proposals.add(new Proposal(from, to, type, expiry));
        markChanged();
    }

    public void removeProposal(Proposal p) {
        if (proposals.remove(p)) {
            markChanged();
        }
    }

    private void removeProposalsBetween(ActorId a, ActorId b) {
        if (proposals.removeIf(p -> (p.from().equals(a.id()) && p.to().equals(b.id())) || (p.from().equals(b.id()) && p.to().equals(a.id())))) {
            markChanged();
        }
    }

    // ---- ticking ----

    /** Drops expired invites and proposals. */
    public void expire() {
        long now = now();
        boolean changed = proposals.removeIf(p -> p.expiry() <= now);
        for (Faction f : factions.values()) {
            changed |= f.invites.values().removeIf(exp -> exp <= now);
        }
        if (changed) {
            markChanged();
        }
    }

    /** Moves every record's reputation towards its default, except at war. */
    public void decay() {
        int amount = FactionsConfig.decayAmount;
        if (amount <= 0) {
            return;
        }
        for (Map.Entry<PairKey, RelationRecord> e : new ArrayList<>(records.entrySet())) {
            RelationRecord r = e.getValue();
            if (r.war) {
                continue;
            }
            PairKey k = e.getKey();
            int def = defaultReputation(k.lo(), k.hi());
            int diff = def - r.reputation;
            if (diff == 0) {
                continue;
            }
            int step = Math.min(amount, Math.abs(diff));
            int target = r.reputation + (diff > 0 ? step : -step);
            mutate(k.lo(), k.hi(), "decay", rec -> rec.reputation = target);
        }
    }

    // ---- persistence ----

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag fs = new ListTag();
        factions.values().forEach(f -> fs.add(f.save()));
        tag.put("factions", fs);

        ListTag rs = new ListTag();
        records.forEach((k, r) -> {
            CompoundTag t = new CompoundTag();
            t.put("a", k.lo().toTag());
            t.put("b", k.hi().toTag());
            r.save(t);
            rs.add(t);
        });
        tag.put("records", rs);

        ListTag ns = new ListTag();
        playerNames.forEach((u, n) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", u);
            t.putString("name", n);
            ns.add(t);
        });
        tag.put("names", ns);

        ListTag gs = new ListTag();
        playerGuardModes.forEach((u, m) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", u);
            t.putString("mode", m.name());
            gs.add(t);
        });
        tag.put("playerGuard", gs);

        ListTag ps = new ListTag();
        proposals.forEach(p -> ps.add(p.save()));
        tag.put("proposals", ps);
        return tag;
    }

    private static FactionsSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        FactionsSavedData d = new FactionsSavedData();
        ListTag fs = tag.getList("factions", Tag.TAG_COMPOUND);
        for (int i = 0; i < fs.size(); i++) {
            Faction f = Faction.load(fs.getCompound(i));
            d.factions.put(f.id, f);
            for (UUID m : f.members.keySet()) {
                d.playerFaction.put(m, f.id);
            }
        }
        ListTag rs = tag.getList("records", Tag.TAG_COMPOUND);
        for (int i = 0; i < rs.size(); i++) {
            CompoundTag t = rs.getCompound(i);
            d.records.put(PairKey.of(ActorId.fromTag(t.getCompound("a")), ActorId.fromTag(t.getCompound("b"))), RelationRecord.load(t));
        }
        ListTag ns = tag.getList("names", Tag.TAG_COMPOUND);
        for (int i = 0; i < ns.size(); i++) {
            CompoundTag t = ns.getCompound(i);
            d.playerNames.put(t.getUUID("id"), t.getString("name"));
        }
        ListTag gs = tag.getList("playerGuard", Tag.TAG_COMPOUND);
        for (int i = 0; i < gs.size(); i++) {
            CompoundTag t = gs.getCompound(i);
            d.playerGuardModes.put(t.getUUID("id"), GuardMode.byName(t.getString("mode"), GuardMode.GUARD));
        }
        ListTag ps = tag.getList("proposals", Tag.TAG_COMPOUND);
        for (int i = 0; i < ps.size(); i++) {
            d.proposals.add(Proposal.load(ps.getCompound(i)));
        }
        return d;
    }

    /** Lowercase helper for case-insensitive keys. */
    public static String norm(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
