package io.github.capttracker.spectrefactions.network;

import io.github.capttracker.spectrefactions.SpectreFactions;
import io.github.capttracker.spectrefactions.api.ActorId;
import io.github.capttracker.spectrefactions.api.FactionType;
import io.github.capttracker.spectrefactions.api.GuardMode;
import io.github.capttracker.spectrefactions.api.ProposalType;
import io.github.capttracker.spectrefactions.api.Rank;
import io.github.capttracker.spectrefactions.api.Relation;
import io.github.capttracker.spectrefactions.api.Treaty;
import io.github.capttracker.spectrefactions.client.FactionsClient;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server to client: everything the receiving player's screens and nameplates need. Sent on login and (at most once
 * per second) after any change.
 *
 * @param myFactionId the receiving player's faction, or null if factionless
 * @param myGuardMode the guard mode of the receiving player's actor
 * @param relations   the relation of the receiving player's actor with every faction and every player it has a record with
 * @param invites     pending invites of the receiving player
 * @param proposals   pending proposals to or from the receiving player's faction
 */
public record ClientboundFactionsSnapshot(@Nullable UUID myFactionId, GuardMode myGuardMode, Settings settings,
                                          List<FactionInfo> factions, List<RelationInfo> relations,
                                          List<InviteInfo> invites, List<ProposalInfo> proposals) implements CustomPacketPayload {
    public static final Type<ClientboundFactionsSnapshot> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SpectreFactions.MOD_ID, "factions_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundFactionsSnapshot> CODEC =
            StreamCodec.of(ClientboundFactionsSnapshot::write, ClientboundFactionsSnapshot::read);

    /** Server settings the client needs to decide what to offer. */
    public record Settings(boolean officersCanDiplomacy, int maxMembers, boolean chatTags, boolean defaultOpen) {}

    public record MemberInfo(UUID id, String name, Rank rank) {}

    public record FactionInfo(UUID id, String name, String tag, ChatFormatting color, FactionType type, boolean open,
                              GuardMode guardMode, List<MemberInfo> members) {
        public ActorId actor() {
            return ActorId.faction(id);
        }
    }

    public record RelationInfo(ActorId other, String name, Relation relation, int reputation, Treaty treaty, boolean war) {}

    public record InviteInfo(UUID factionId, int ticksLeft) {}

    public record ProposalInfo(UUID from, UUID to, ProposalType type, int ticksLeft) {}

    @Override
    public Type<ClientboundFactionsSnapshot> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, ClientboundFactionsSnapshot s) {
        buf.writeBoolean(s.myFactionId != null);
        if (s.myFactionId != null) {
            buf.writeUUID(s.myFactionId);
        }
        buf.writeEnum(s.myGuardMode);
        buf.writeBoolean(s.settings.officersCanDiplomacy);
        buf.writeVarInt(s.settings.maxMembers);
        buf.writeBoolean(s.settings.chatTags);
        buf.writeBoolean(s.settings.defaultOpen);

        buf.writeVarInt(s.factions.size());
        for (FactionInfo f : s.factions) {
            buf.writeUUID(f.id);
            buf.writeUtf(f.name, 64);
            buf.writeUtf(f.tag, 16);
            buf.writeVarInt(f.color.getId());
            buf.writeEnum(f.type);
            buf.writeBoolean(f.open);
            buf.writeEnum(f.guardMode);
            buf.writeVarInt(f.members.size());
            for (MemberInfo m : f.members) {
                buf.writeUUID(m.id);
                buf.writeUtf(m.name, 32);
                buf.writeEnum(m.rank);
            }
        }

        buf.writeVarInt(s.relations.size());
        for (RelationInfo r : s.relations) {
            buf.writeBoolean(r.other.isFaction());
            buf.writeUUID(r.other.id());
            buf.writeUtf(r.name, 64);
            buf.writeEnum(r.relation);
            buf.writeVarInt(r.reputation + 1000);
            buf.writeEnum(r.treaty);
            buf.writeBoolean(r.war);
        }

        buf.writeVarInt(s.invites.size());
        for (InviteInfo i : s.invites) {
            buf.writeUUID(i.factionId);
            buf.writeVarInt(i.ticksLeft);
        }

        buf.writeVarInt(s.proposals.size());
        for (ProposalInfo p : s.proposals) {
            buf.writeUUID(p.from);
            buf.writeUUID(p.to);
            buf.writeEnum(p.type);
            buf.writeVarInt(p.ticksLeft);
        }
    }

    private static ClientboundFactionsSnapshot read(RegistryFriendlyByteBuf buf) {
        UUID my = buf.readBoolean() ? buf.readUUID() : null;
        GuardMode guard = buf.readEnum(GuardMode.class);
        Settings settings = new Settings(buf.readBoolean(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean());

        int fn = buf.readVarInt();
        List<FactionInfo> factions = new ArrayList<>(fn);
        for (int i = 0; i < fn; i++) {
            UUID id = buf.readUUID();
            String name = buf.readUtf(64);
            String tag = buf.readUtf(16);
            ChatFormatting color = ChatFormatting.getById(buf.readVarInt());
            if (color == null) {
                color = ChatFormatting.WHITE;
            }
            FactionType type = buf.readEnum(FactionType.class);
            boolean open = buf.readBoolean();
            GuardMode gm = buf.readEnum(GuardMode.class);
            int mn = buf.readVarInt();
            List<MemberInfo> members = new ArrayList<>(mn);
            for (int j = 0; j < mn; j++) {
                members.add(new MemberInfo(buf.readUUID(), buf.readUtf(32), buf.readEnum(Rank.class)));
            }
            factions.add(new FactionInfo(id, name, tag, color, type, open, gm, members));
        }

        int rn = buf.readVarInt();
        List<RelationInfo> relations = new ArrayList<>(rn);
        for (int i = 0; i < rn; i++) {
            boolean isFaction = buf.readBoolean();
            UUID id = buf.readUUID();
            ActorId other = isFaction ? ActorId.faction(id) : ActorId.player(id);
            relations.add(new RelationInfo(other, buf.readUtf(64), buf.readEnum(Relation.class), buf.readVarInt() - 1000,
                    buf.readEnum(Treaty.class), buf.readBoolean()));
        }

        int in = buf.readVarInt();
        List<InviteInfo> invites = new ArrayList<>(in);
        for (int i = 0; i < in; i++) {
            invites.add(new InviteInfo(buf.readUUID(), buf.readVarInt()));
        }

        int pn = buf.readVarInt();
        List<ProposalInfo> proposals = new ArrayList<>(pn);
        for (int i = 0; i < pn; i++) {
            proposals.add(new ProposalInfo(buf.readUUID(), buf.readUUID(), buf.readEnum(ProposalType.class), buf.readVarInt()));
        }
        return new ClientboundFactionsSnapshot(my, guard, settings, factions, relations, invites, proposals);
    }

    public static void handle(ClientboundFactionsSnapshot payload, IPayloadContext context) {
        // The lambda keeps the client class out of this class's linkage on a dedicated server.
        context.enqueueWork(() -> FactionsClient.acceptSnapshot(payload));
    }
}
