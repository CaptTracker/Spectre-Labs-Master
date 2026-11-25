package io.github.capttracker.spectrefactions.service;

import io.github.capttracker.spectrefactions.api.ActorId;
import io.github.capttracker.spectrefactions.config.FactionsConfig;
import io.github.capttracker.spectrefactions.data.Faction;
import io.github.capttracker.spectrefactions.data.FactionsSavedData;
import io.github.capttracker.spectrefactions.data.Proposal;
import io.github.capttracker.spectrefactions.data.RelationRecord;
import io.github.capttracker.spectrefactions.network.ClientboundFactionsSnapshot;
import io.github.capttracker.spectrefactions.network.ClientboundFactionsSnapshot.FactionInfo;
import io.github.capttracker.spectrefactions.network.ClientboundFactionsSnapshot.InviteInfo;
import io.github.capttracker.spectrefactions.network.ClientboundFactionsSnapshot.MemberInfo;
import io.github.capttracker.spectrefactions.network.ClientboundFactionsSnapshot.ProposalInfo;
import io.github.capttracker.spectrefactions.network.ClientboundFactionsSnapshot.RelationInfo;
import io.github.capttracker.spectrefactions.network.ClientboundFactionsSnapshot.Settings;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Builds and sends {@link ClientboundFactionsSnapshot}s: on login, and batched to at most one per second after changes. */
public final class FactionsSync {
    private FactionsSync() {}

    /** Marks the data changed so the next batch goes out. */
    public static void requestSync() {
        FactionsSavedData.syncDirty = true;
    }

    /** Called every server tick. Sends the batched snapshot on the second boundary, and drops expired invites and proposals. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        FactionsSavedData d = FactionsSavedData.get(server);
        if (d == null) {
            return;
        }
        d.expire();
        if (FactionsSavedData.syncDirty) {
            FactionsSavedData.syncDirty = false;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                sendTo(p, d);
                refreshNames(p);
            }
        }
    }

    public static void sendTo(ServerPlayer player) {
        FactionsSavedData d = FactionsSavedData.get(player.server);
        if (d != null) {
            sendTo(player, d);
        }
    }

    private static void sendTo(ServerPlayer player, FactionsSavedData d) {
        PacketDistributor.sendToPlayer(player, build(d, player.getUUID()));
    }

    public static void refreshNames(ServerPlayer p) {
        p.refreshDisplayName();
        p.refreshTabListName();
    }

    public static ClientboundFactionsSnapshot build(FactionsSavedData d, UUID player) {
        ActorId me = d.actorOfPlayer(player);
        Faction mine = d.factionOfPlayer(player).orElse(null);
        long now = d.now();

        List<FactionInfo> factions = new ArrayList<>();
        List<RelationInfo> relations = new ArrayList<>();
        List<InviteInfo> invites = new ArrayList<>();
        for (Faction f : d.factions()) {
            List<MemberInfo> members = new ArrayList<>();
            f.getMembers().forEach((id, rank) -> members.add(new MemberInfo(id, d.playerName(id), rank)));
            factions.add(new FactionInfo(f.getId(), f.getName(), f.getTag(), f.getColor(), f.getType(), f.isOpen(), f.getGuardMode(), members));
            ActorId other = f.actor();
            if (!other.equals(me)) {
                relations.add(new RelationInfo(other, f.getName(), d.relation(me, other), d.reputation(me, other),
                        d.treaty(me, other), d.isWar(me, other)));
            }
            Long exp = f.getInvites().get(player);
            if (exp != null && exp > now) {
                invites.add(new InviteInfo(f.getId(), (int) Math.min(Integer.MAX_VALUE, exp - now)));
            }
        }
        for (Map.Entry<FactionsSavedData.PairKey, RelationRecord> e : d.recordsView().entrySet()) {
            if (e.getKey().involves(me)) {
                ActorId other = e.getKey().other(me);
                if (other.isPlayer()) {
                    relations.add(new RelationInfo(other, d.playerName(other.id()), d.relation(me, other), d.reputation(me, other),
                            d.treaty(me, other), d.isWar(me, other)));
                }
            }
        }
        List<ProposalInfo> proposals = new ArrayList<>();
        if (mine != null) {
            for (Proposal p : d.proposals()) {
                if (p.expiry() > now && (p.from().equals(mine.getId()) || p.to().equals(mine.getId()))) {
                    proposals.add(new ProposalInfo(p.from(), p.to(), p.type(), (int) Math.min(Integer.MAX_VALUE, p.expiry() - now)));
                }
            }
        }
        return new ClientboundFactionsSnapshot(mine == null ? null : mine.getId(), d.guardMode(me),
                new Settings(FactionsConfig.officersCanDiplomacy, FactionsConfig.maxMembers, FactionsConfig.chatTags, FactionsConfig.defaultOpen),
                factions, relations, invites, proposals);
    }
}
