/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 1): settlements.
 */
package io.github.capttracker.ancientwarfarenpc.settlement;

import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The settlement geometry that the owner's client needs to draw the border while holding a marker or the ledger (server to client).
 * Registered by {@code AwNpcSettlements}.
 */
public final class SettlementNetwork {
    /** The most settlements and markers one payload carries (the decoder drops the rest, so the sender must not exceed it). */
    public static final int MAX_SETTLEMENTS = 64;
    public static final int MAX_MARKERS = 4096;

    private SettlementNetwork() {}

    public static void registerPayloads(PayloadRegistrar registrar) {
        registrar.playToClient(GeometryPayload.TYPE, GeometryPayload.CODEC, GeometryPayload::handle);
    }

    /** What the client needs of one settlement: the hall, the markers, the radius used below 3 markers, and the colour of the border. */
    public record Geometry(UUID id, BlockPos hall, List<BlockPos> markers, int radius, int color) {}

    public record GeometryPayload(List<Geometry> settlements) implements CustomPacketPayload {
        public static final Type<GeometryPayload> TYPE = new Type<>(Ids.of(AncientWarfareNPC.MOD_ID, "settlement_geometry"));

        public static final StreamCodec<RegistryFriendlyByteBuf, GeometryPayload> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.settlements.size());
                    for (Geometry geometry : payload.settlements) {
                        buf.writeUUID(geometry.id);
                        buf.writeBlockPos(geometry.hall);
                        buf.writeVarInt(geometry.markers.size());
                        for (BlockPos marker : geometry.markers) {
                            buf.writeBlockPos(marker);
                        }
                        buf.writeVarInt(geometry.radius);
                        buf.writeInt(geometry.color);
                    }
                },
                buf -> {
                    int count = Math.min(buf.readVarInt(), MAX_SETTLEMENTS);
                    List<Geometry> list = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        UUID id = buf.readUUID();
                        BlockPos hall = buf.readBlockPos();
                        int markerCount = Math.min(buf.readVarInt(), MAX_MARKERS);
                        List<BlockPos> markers = new ArrayList<>();
                        for (int j = 0; j < markerCount; j++) {
                            markers.add(buf.readBlockPos());
                        }
                        list.add(new Geometry(id, hall, markers, buf.readVarInt(), buf.readInt()));
                    }
                    return new GeometryPayload(list);
                });

        @Override
        public Type<GeometryPayload> type() {
            return TYPE;
        }

        public static void handle(GeometryPayload payload, IPayloadContext context) {
            ClientSettlementView.update(payload.settlements);
        }
    }
}
