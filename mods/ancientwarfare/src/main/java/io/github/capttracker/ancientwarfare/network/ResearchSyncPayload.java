/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/network/PacketResearchInit and PacketResearchUpdate.
 */
package io.github.capttracker.ancientwarfare.network;

import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.ancientwarfare.research.Research;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: the receiving player's whole research entry. Sent on login and after every change. */
public record ResearchSyncPayload(CompoundTag entry) implements CustomPacketPayload {
    public static final Type<ResearchSyncPayload> TYPE = new Type<>(Ids.of(AncientWarfareCore.MOD_ID, "research_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchSyncPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, ResearchSyncPayload::entry, ResearchSyncPayload::new);

    @Override
    public Type<ResearchSyncPayload> type() {
        return TYPE;
    }

    public static void handle(ResearchSyncPayload payload, IPayloadContext context) {
        Research.acceptSync(payload);
    }
}
