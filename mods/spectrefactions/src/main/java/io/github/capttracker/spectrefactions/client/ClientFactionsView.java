package io.github.capttracker.spectrefactions.client;

import io.github.capttracker.spectrefactions.api.ActorId;
import io.github.capttracker.spectrefactions.api.FactionType;
import io.github.capttracker.spectrefactions.api.GuardMode;
import io.github.capttracker.spectrefactions.api.ProposalType;
import io.github.capttracker.spectrefactions.api.Rank;
import io.github.capttracker.spectrefactions.network.ClientboundFactionsSnapshot;
import io.github.capttracker.spectrefactions.network.ClientboundFactionsSnapshot.FactionInfo;
import io.github.capttracker.spectrefactions.network.ClientboundFactionsSnapshot.InviteInfo;
import io.github.capttracker.spectrefactions.network.ClientboundFactionsSnapshot.MemberInfo;
import io.github.capttracker.spectrefactions.network.ClientboundFactionsSnapshot.ProposalInfo;
import io.github.capttracker.spectrefactions.network.ClientboundFactionsSnapshot.RelationInfo;
import io.github.capttracker.spectrefactions.network.ClientboundFactionsSnapshot.Settings;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The client's copy of the last {@link ClientboundFactionsSnapshot}. Plain Java (no Minecraft client classes), so
 * it is safe to reference from common code; screens and nameplates read from it. Replaced wholesale on every sync.
 *
 * <p>Tick counters in invites and proposals are the server's values at the time of the snapshot.
 */
public final class ClientFactionsView {
    public static final ClientFactionsView INSTANCE = new ClientFactionsView();

    private static final ClientboundFactionsSnapshot EMPTY = new ClientboundFactionsSnapshot(null, GuardMode.GUARD,
            new Settings(true, 0, true, true), List.of(), List.of(), List.of(), List.of());

    private volatile ClientboundFactionsSnapshot snapshot = EMPTY;
    private volatile Map<UUID, FactionInfo> byId = Map.of();
    private volatile Map<UUID, FactionInfo> byPlayer = Map.of();
    private volatile Map<ActorId, RelationInfo> relationByActor = Map.of();
    private volatile UUID localPlayer;
    private volatile long revision;

    private ClientFactionsView() {}

    // ---- updates ----

    public void update(ClientboundFactionsSnapshot s, @Nullable UUID localPlayerId) {
        Map<UUID, FactionInfo> ids = new HashMap<>();
        Map<UUID, FactionInfo> players = new HashMap<>();
        for (FactionInfo f : s.factions()) {
            ids.put(f.id(), f);
            for (MemberInfo m : f.members()) {
                players.put(m.id(), f);
            }
        }
        Map<ActorId, RelationInfo> rels = new HashMap<>();
        for (RelationInfo r : s.relations()) {
            rels.put(r.other(), r);
        }
        this.byId = ids;
        this.byPlayer = players;
        this.relationByActor = rels;
        this.localPlayer = localPlayerId;
        this.snapshot = s;
        this.revision++;
    }

    public void reset() {
        this.snapshot = EMPTY;
        this.byId = Map.of();
        this.byPlayer = Map.of();
        this.relationByActor = Map.of();
        this.revision++;
    }

    /** Increases on every update; screens can poll it to know when to rebuild. */
    public long revision() {
        return revision;
    }

    // ---- reading ----

    public boolean isLoaded() {
        return snapshot != EMPTY;
    }

    public Settings settings() {
        return snapshot.settings();
    }

    public List<FactionInfo> factions() {
        return snapshot.factions();
    }

    public Optional<FactionInfo> faction(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<FactionInfo> factionOfPlayer(UUID playerId) {
        return Optional.ofNullable(byPlayer.get(playerId));
    }

    @Nullable
    public UUID myFactionId() {
        return snapshot.myFactionId();
    }

    public Optional<FactionInfo> myFaction() {
        UUID id = snapshot.myFactionId();
        return id == null ? Optional.empty() : faction(id);
    }

    public Optional<UUID> localPlayerId() {
        return Optional.ofNullable(localPlayer);
    }

    public Optional<Rank> myRank() {
        UUID me = localPlayer;
        if (me == null) {
            return Optional.empty();
        }
        return myFaction().flatMap(f -> f.members().stream().filter(m -> m.id().equals(me)).map(MemberInfo::rank).findFirst());
    }

    /** The guard mode of the local player's actor (their faction's, or their own when factionless). */
    public GuardMode myGuardMode() {
        return snapshot.myGuardMode();
    }

    public boolean isLeader() {
        return myRank().orElse(null) == Rank.LEADER;
    }

    public boolean canInvite() {
        Rank r = myRank().orElse(null);
        return r == Rank.LEADER || r == Rank.OFFICER;
    }

    public boolean canDiplomacy() {
        Rank r = myRank().orElse(null);
        return r == Rank.LEADER || (r == Rank.OFFICER && settings().officersCanDiplomacy());
    }

    /** Whether the local player may kick this member. */
    public boolean canKick(MemberInfo target) {
        Rank r = myRank().orElse(null);
        if (r == Rank.LEADER) {
            return target.rank() != Rank.LEADER;
        }
        return r == Rank.OFFICER && target.rank() == Rank.MEMBER;
    }

    /** Relation of the local player's actor with another actor, if known. */
    public Optional<RelationInfo> relationWith(ActorId other) {
        return Optional.ofNullable(relationByActor.get(other));
    }

    public Optional<RelationInfo> relationWithFaction(UUID factionId) {
        return relationWith(ActorId.faction(factionId));
    }

    public List<InviteInfo> invites() {
        return snapshot.invites();
    }

    public List<ProposalInfo> incomingProposals() {
        UUID me = snapshot.myFactionId();
        return snapshot.proposals().stream().filter(p -> p.to().equals(me)).toList();
    }

    public List<ProposalInfo> outgoingProposals() {
        UUID me = snapshot.myFactionId();
        return snapshot.proposals().stream().filter(p -> p.from().equals(me)).toList();
    }

    /** True if joining this faction needs no invite. */
    public boolean canJoin(FactionInfo f) {
        if (snapshot.myFactionId() != null || f.type() != FactionType.PLAYER) {
            return false;
        }
        int max = settings().maxMembers();
        if (max > 0 && f.members().size() >= max) {
            return false;
        }
        return f.open() || invites().stream().anyMatch(i -> i.factionId().equals(f.id()));
    }

    /** Proposal type helper for screens. */
    public static boolean isPeace(ProposalInfo p) {
        return p.type() == ProposalType.PEACE;
    }
}
