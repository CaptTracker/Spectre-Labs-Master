package io.github.capttracker.spectrefactions.network;

import io.github.capttracker.spectrefactions.SpectreFactions;
import io.github.capttracker.spectrefactions.service.FactionService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Client to server: a faction action from the screen. The server validates it exactly like the matching command
 * (both go through {@link FactionService}). {@code actionType} is one of {@link FactionService#ACTIONS}; the
 * arguments are strings (faction and player references are UUIDs or names).
 */
public record ServerboundFactionAction(String actionType, List<String> args) implements CustomPacketPayload {
    public static final Type<ServerboundFactionAction> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SpectreFactions.MOD_ID, "faction_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundFactionAction> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), ServerboundFactionAction::actionType,
            ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(8)), ServerboundFactionAction::args,
            ServerboundFactionAction::new);

    @Override
    public Type<ServerboundFactionAction> type() {
        return TYPE;
    }

    public static void handle(ServerboundFactionAction payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            FactionService.perform(player, payload.actionType, payload.args);
        }
    }
}
