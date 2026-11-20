package io.github.capttracker.spectrefactions.network;

import io.github.capttracker.spectrefactions.SpectreFactions;
import io.github.capttracker.spectrefactions.client.FactionsClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: open the faction screen (sent by {@code /faction gui}). */
public record ClientboundOpenFactionScreen() implements CustomPacketPayload {
    public static final Type<ClientboundOpenFactionScreen> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SpectreFactions.MOD_ID, "open_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundOpenFactionScreen> CODEC =
            StreamCodec.unit(new ClientboundOpenFactionScreen());

    @Override
    public Type<ClientboundOpenFactionScreen> type() {
        return TYPE;
    }

    public static void handle(ClientboundOpenFactionScreen payload, IPayloadContext context) {
        context.enqueueWork(() -> FactionsClient.openScreen());
    }
}
