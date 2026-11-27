/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): the plots and builds that the owner's client draws.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The plots and the footprints of the builds of the settlements near a player who holds the building zone tool (server to client). Registered by
 * {@code SettlementNetwork}. The client draws them with {@code PlotRenderer}.
 */
public final class BuildingNetwork {
    public static final int MAX_SETTLEMENTS = 16;
    public static final int MAX_PLOTS = 256;
    public static final int MAX_PROJECTS = 256;

    private BuildingNetwork() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(PlotsPayload.TYPE, PlotsPayload.CODEC, PlotsPayload::handle);
    }

    public record PlotView(UUID id, int number, int minX, int minZ, int maxX, int maxZ, int y) {}

    /** The footprint of a build: the lowest north-west corner, the size, the turn, the status ordinal and the kind ordinal. */
    public record ProjectView(BlockPos origin, int sizeX, int sizeY, int sizeZ, Direction facing, int status, int kind) {}

    public record SettlementPlots(UUID settlement, List<PlotView> plots, List<ProjectView> projects) {}

    public record PlotsPayload(List<SettlementPlots> settlements) implements CustomPacketPayload {
        public static final Type<PlotsPayload> TYPE = new Type<>(Ids.of(AncientWarfareNPC.MOD_ID, "building_plots"));

        public static final StreamCodec<RegistryFriendlyByteBuf, PlotsPayload> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    int count = Math.min(payload.settlements.size(), MAX_SETTLEMENTS);
                    buf.writeVarInt(count);
                    for (int i = 0; i < count; i++) {
                        SettlementPlots s = payload.settlements.get(i);
                        buf.writeUUID(s.settlement);
                        int plots = Math.min(s.plots.size(), MAX_PLOTS);
                        buf.writeVarInt(plots);
                        for (int j = 0; j < plots; j++) {
                            PlotView p = s.plots.get(j);
                            buf.writeUUID(p.id);
                            buf.writeVarInt(p.number);
                            buf.writeInt(p.minX);
                            buf.writeInt(p.minZ);
                            buf.writeInt(p.maxX);
                            buf.writeInt(p.maxZ);
                            buf.writeInt(p.y);
                        }
                        int projects = Math.min(s.projects.size(), MAX_PROJECTS);
                        buf.writeVarInt(projects);
                        for (int j = 0; j < projects; j++) {
                            ProjectView p = s.projects.get(j);
                            buf.writeBlockPos(p.origin);
                            buf.writeVarInt(p.sizeX);
                            buf.writeVarInt(p.sizeY);
                            buf.writeVarInt(p.sizeZ);
                            buf.writeEnum(p.facing);
                            buf.writeVarInt(p.status);
                            buf.writeVarInt(p.kind);
                        }
                    }
                },
                buf -> {
                    int count = Math.min(buf.readVarInt(), MAX_SETTLEMENTS);
                    List<SettlementPlots> list = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        UUID id = buf.readUUID();
                        int plotCount = Math.min(buf.readVarInt(), MAX_PLOTS);
                        List<PlotView> plots = new ArrayList<>();
                        for (int j = 0; j < plotCount; j++) {
                            plots.add(new PlotView(buf.readUUID(), buf.readVarInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));
                        }
                        int projectCount = Math.min(buf.readVarInt(), MAX_PROJECTS);
                        List<ProjectView> projects = new ArrayList<>();
                        for (int j = 0; j < projectCount; j++) {
                            projects.add(new ProjectView(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readEnum(Direction.class),
                                    buf.readVarInt(), buf.readVarInt()));
                        }
                        list.add(new SettlementPlots(id, plots, projects));
                    }
                    return new PlotsPayload(list);
                });

        @Override
        public Type<PlotsPayload> type() {
            return TYPE;
        }

        public static void handle(PlotsPayload payload, IPayloadContext context) {
            ClientPlotView.update(payload.settlements);
        }
    }
}
