/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (third round): the settlement list for map mods (JourneyMap plugin), server to client.
 */
package io.github.capttracker.ancientwarfarenpc.settlement;

import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.lib.registry.Ids;
import io.github.capttracker.spectrefactions.api.ActorId;
import io.github.capttracker.spectrefactions.api.FactionsApi;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Sends every player the settlements they own or belong to (name, dimension, hall, markers, colour), in all dimensions, so a map mod on the client can
 * show them. It is a few dozen bytes for each settlement and only goes out when the list changed (checked every {@link #INTERVAL} ticks) and at login.
 * The client keeps it in {@link ClientSettlementMap}; nothing is saved.
 */
public final class SettlementMapSync {
    public static final int INTERVAL = 100;
    private static final Map<UUID, Integer> LAST = new HashMap<>();

    private SettlementMapSync() {}

    /** One settlement as the map sees it. */
    public record Entry(UUID id, String name, ResourceLocation dimension, BlockPos hall, List<BlockPos> markers, int radius, int color) {}

    public static void registerPayloads(PayloadRegistrar registrar) {
        registrar.playToClient(Payload.TYPE, Payload.CODEC, Payload::handle);
    }

    /** Whether the player owns the settlement, founded it, or is a member of the faction that owns it. */
    static boolean belongsTo(Settlement settlement, ServerPlayer player) {
        ActorId actor = FactionsApi.actorOf(player.getUUID());
        if (settlement.getOwnerActor().equals(actor) || settlement.getFounderId().equals(player.getUUID())) {
            return true;
        }
        ActorId owner = settlement.getOwnerActor();
        return owner.isFaction() && FactionsApi.getFaction(owner.id()).map(f -> f.isMember(player.getUUID())).orElse(false);
    }

    /** Called from the server tick every {@link #INTERVAL} ticks. */
    public static void tick(MinecraftServer server) {
        SettlementData data = SettlementData.get(server);
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            List<Entry> list = new ArrayList<>();
            for (Settlement settlement : data.all()) {
                if (list.size() >= SettlementNetwork.MAX_SETTLEMENTS) {
                    break;
                }
                if (!belongsTo(settlement, player)) {
                    continue;
                }
                List<BlockPos> markers = settlement.getMarkers();
                if (markers.size() > SettlementNetwork.MAX_MARKERS) {
                    markers = markers.subList(0, SettlementNetwork.MAX_MARKERS);
                }
                list.add(new Entry(settlement.getId(), settlement.getName(), settlement.getDimension().location(), settlement.getHallPos(),
                        new ArrayList<>(markers), SettlementConfig.defaultRadius, SettlementEvents.colorOf(settlement.getOwnerActor())));
            }
            int hash = Objects.hash(list.toArray());
            Integer last = LAST.get(player.getUUID());
            if (last == null || last != hash) {
                LAST.put(player.getUUID(), hash);
                PacketDistributor.sendToPlayer(player, new Payload(list));
            }
        }
        LAST.keySet().retainAll(online);
    }

    public record Payload(List<Entry> settlements) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Ids.of(AncientWarfareNPC.MOD_ID, "settlement_map"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.settlements.size());
                    for (Entry e : payload.settlements) {
                        buf.writeUUID(e.id());
                        buf.writeUtf(e.name(), 128);
                        buf.writeResourceLocation(e.dimension());
                        buf.writeBlockPos(e.hall());
                        buf.writeVarInt(e.markers().size());
                        for (BlockPos marker : e.markers()) {
                            buf.writeBlockPos(marker);
                        }
                        buf.writeVarInt(e.radius());
                        buf.writeInt(e.color());
                    }
                },
                buf -> {
                    int count = Math.min(buf.readVarInt(), SettlementNetwork.MAX_SETTLEMENTS);
                    List<Entry> list = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        UUID id = buf.readUUID();
                        String name = buf.readUtf(128);
                        ResourceLocation dimension = buf.readResourceLocation();
                        BlockPos hall = buf.readBlockPos();
                        int markerCount = Math.min(buf.readVarInt(), SettlementNetwork.MAX_MARKERS);
                        List<BlockPos> markers = new ArrayList<>();
                        for (int j = 0; j < markerCount; j++) {
                            markers.add(buf.readBlockPos());
                        }
                        list.add(new Entry(id, name, dimension, hall, markers, buf.readVarInt(), buf.readInt()));
                    }
                    return new Payload(list);
                });

        @Override
        public Type<Payload> type() {
            return TYPE;
        }

        public static void handle(Payload payload, IPayloadContext context) {
            ClientSettlementMap.update(payload.settlements);
        }
    }
}
