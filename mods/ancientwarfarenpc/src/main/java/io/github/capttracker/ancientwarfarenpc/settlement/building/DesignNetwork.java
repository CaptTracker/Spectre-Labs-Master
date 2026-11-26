/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): building designs.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.settlement.building.client.DesignClientHooks;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

/**
 * The payloads of the designs: the list of names and numbers (server to client), the full data of one design on request (for the ghost
 * preview), the opening of the scanner screen and the saving of a blueprint from it. Registered by {@code SettlementNetwork}.
 */
public final class DesignNetwork {
    public static final int MAX_METAS = 2048;
    private static final int MAX_JSON = 1_000_000;

    private DesignNetwork() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(ListPayload.TYPE, ListPayload.CODEC, ListPayload::handle);
        registrar.playToClient(DataPayload.TYPE, DataPayload.CODEC, DataPayload::handle);
        registrar.playToClient(OpenScannerPayload.TYPE, OpenScannerPayload.CODEC, OpenScannerPayload::handle);
        registrar.playToServer(RequestPayload.TYPE, RequestPayload.CODEC, RequestPayload::handle);
        registrar.playToServer(SavePayload.TYPE, SavePayload.CODEC, SavePayload::handle);
    }

    /** The designs the player may use (built-in and own blueprints), names and numbers only. */
    public record ListPayload(List<DesignMeta> metas) implements CustomPacketPayload {
        public static final Type<ListPayload> TYPE = new Type<>(Ids.of(AncientWarfareNPC.MOD_ID, "design_list"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ListPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.metas.size());
                    for (DesignMeta meta : p.metas) {
                        meta.write(buf);
                    }
                },
                buf -> {
                    int count = Math.min(buf.readVarInt(), MAX_METAS);
                    List<DesignMeta> list = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        list.add(DesignMeta.read(buf));
                    }
                    return new ListPayload(list);
                });

        @Override
        public Type<ListPayload> type() {
            return TYPE;
        }

        public static void handle(ListPayload payload, IPayloadContext context) {
            DesignClientHooks.setMetas(payload.metas);
        }
    }

    /** Client asks for the blocks of one design (to draw a ghost of it). */
    public record RequestPayload(ResourceLocation id) implements CustomPacketPayload {
        public static final Type<RequestPayload> TYPE = new Type<>(Ids.of(AncientWarfareNPC.MOD_ID, "design_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestPayload> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeResourceLocation(p.id), buf -> new RequestPayload(buf.readResourceLocation()));

        @Override
        public Type<RequestPayload> type() {
            return TYPE;
        }

        public static void handle(RequestPayload payload, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            BuildingDesign design = BuildingDesigns.get(payload.id);
            if (design != null && BuildingDesigns.usableBy(design, player.getUUID())) {
                String json = design.toJson().toString();
                if (json.length() <= MAX_JSON / 2) { // the packet limit is 1 MiB of bytes; a huge design is not sent (no ghost preview for it)
                    PacketDistributor.sendToPlayer(player, new DataPayload(design.getId(), json));
                }
            }
        }
    }

    /** The full design as JSON (the answer to a request). */
    public record DataPayload(ResourceLocation id, String json) implements CustomPacketPayload {
        public static final Type<DataPayload> TYPE = new Type<>(Ids.of(AncientWarfareNPC.MOD_ID, "design_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DataPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeResourceLocation(p.id);
                    buf.writeUtf(p.json, MAX_JSON);
                },
                buf -> new DataPayload(buf.readResourceLocation(), buf.readUtf(MAX_JSON)));

        @Override
        public Type<DataPayload> type() {
            return TYPE;
        }

        public static void handle(DataPayload payload, IPayloadContext context) {
            DesignClientHooks.setFull(payload.id, payload.json);
        }
    }

    /** Opens the scanner screen: the size of the scanned box, the front, and what was found in it. */
    public record OpenScannerPayload(BlockPos min, BlockPos max, Direction front, int beds, int storage, List<String> worksiteBlocks)
            implements CustomPacketPayload {
        public static final Type<OpenScannerPayload> TYPE = new Type<>(Ids.of(AncientWarfareNPC.MOD_ID, "design_open_scanner"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenScannerPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBlockPos(p.min);
                    buf.writeBlockPos(p.max);
                    buf.writeEnum(p.front);
                    buf.writeVarInt(p.beds);
                    buf.writeVarInt(p.storage);
                    int count = Math.min(p.worksiteBlocks.size(), 16);
                    buf.writeVarInt(count);
                    for (int i = 0; i < count; i++) {
                        buf.writeUtf(p.worksiteBlocks.get(i), 128);
                    }
                },
                buf -> {
                    BlockPos min = buf.readBlockPos();
                    BlockPos max = buf.readBlockPos();
                    Direction front = buf.readEnum(Direction.class);
                    int beds = buf.readVarInt();
                    int storage = buf.readVarInt();
                    int count = Math.min(buf.readVarInt(), 16);
                    List<String> list = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        list.add(buf.readUtf(128));
                    }
                    return new OpenScannerPayload(min, max, front, beds, storage, list);
                });

        @Override
        public Type<OpenScannerPayload> type() {
            return TYPE;
        }

        public static void handle(OpenScannerPayload payload, IPayloadContext context) {
            DesignClientHooks.openScanner(payload);
        }
    }

    /** The scanner screen's answer: name, category, style and the worksite block to mark (empty for none). The box is the one on the held scanner. */
    public record SavePayload(String name, int category, int style, String worksite) implements CustomPacketPayload {
        public static final Type<SavePayload> TYPE = new Type<>(Ids.of(AncientWarfareNPC.MOD_ID, "design_save"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SavePayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUtf(p.name, 40);
                    buf.writeVarInt(p.category);
                    buf.writeVarInt(p.style);
                    buf.writeUtf(p.worksite, 128);
                },
                buf -> new SavePayload(buf.readUtf(40), buf.readVarInt(), buf.readVarInt(), buf.readUtf(128)));

        @Override
        public Type<SavePayload> type() {
            return TYPE;
        }

        public static void handle(SavePayload payload, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            String name = payload.name.trim();
            if (name.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.ancientwarfarenpc.blueprint.no_name"), true);
                return;
            }
            ResourceLocation worksite = payload.worksite.isEmpty() ? null : ResourceLocation.tryParse(payload.worksite);
            BlueprintScannerItem.save(player, name, DesignCategory.byOrdinal(payload.category), DesignStyle.byOrdinal(payload.style), worksite);
        }
    }

    /** Parses design JSON text, or returns null when it is not an object. Client use. */
    public static JsonObject parseObject(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
