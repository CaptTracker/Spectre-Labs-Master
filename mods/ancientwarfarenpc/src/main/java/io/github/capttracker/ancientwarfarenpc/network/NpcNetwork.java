/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/network/PacketNpcCommand, PacketFactionUpdate, PacketExtendedReachAttack,
 * PacketTeamMembershipUpdate, PacketTeamStandingsUpdate and PacketTeamStandingUpdate, and the packet registration of npc/AncientWarfareNPC.
 */
package io.github.capttracker.ancientwarfarenpc.network;

import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.npccommand.NpcCommand;
import io.github.capttracker.ancientwarfarenpc.npccommand.NpcCommand.CommandType;
import io.github.capttracker.lib.registry.Ids;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The two payloads of the NPC module (the records are nested here) and their registration. The 1.12.2 packet ids (12, 13, 27, 31, 32, 33)
 * are gone; the faction standing and team packets were dropped for Spectre Factions. {@code PacketStructureEntry} (id 29) was registered by this module in 1.12.2 but belongs to the Structures module.
 *
 * <p>Both payloads go client to server.
 *
 * <p>Wiring: {@code AwNpcs.registerEvents} calls {@link #registerPayloads(PayloadRegistrar)} with {@code event.registrar(NpcNetwork.VERSION)}.
 */
public final class NpcNetwork {
    /** Bump when a payload's wire format changes. */
    public static final String VERSION = "1";

    private NpcNetwork() {}

    public static void registerPayloads(PayloadRegistrar registrar) {
        registrar.playToServer(NpcCommandPayload.TYPE, NpcCommandPayload.CODEC, NpcCommandPayload::handle);
        registrar.playToServer(ExtendedReachAttackPayload.TYPE, ExtendedReachAttackPayload.CODEC, ExtendedReachAttackPayload::handle);
    }

    /** Client to server (1.12.2: {@code NetworkHandler.sendToServer}). */
    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    /** Server to every player (1.12.2: {@code NetworkHandler.sendToAllPlayers}). */
    public static void sendToAllPlayers(CustomPacketPayload payload) {
        PacketDistributor.sendToAllPlayers(payload);
    }

    // ---------------------------------------------------------------- baton command

    /**
     * A command of the command baton: the command type and its target, a block position or an entity id (client to server).
     * The server runs it on the NPCs bound to the baton in the sender's hands.
     */
    public record NpcCommandPayload(CommandType commandType, boolean blockTarget, BlockPos pos, int entityId) implements CustomPacketPayload {
        public static final Type<NpcCommandPayload> TYPE = new Type<>(Ids.of(AncientWarfareNPC.MOD_ID, "npc_command"));

        public static final StreamCodec<RegistryFriendlyByteBuf, NpcCommandPayload> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    buf.writeVarInt(payload.commandType.ordinal());
                    buf.writeBoolean(payload.blockTarget);
                    if (payload.blockTarget) {
                        buf.writeBlockPos(payload.pos);
                    } else {
                        buf.writeVarInt(payload.entityId);
                    }
                },
                buf -> {
                    int ordinal = buf.readVarInt();
                    CommandType[] types = CommandType.values();
                    if (ordinal < 0 || ordinal >= types.length) {
                        throw new DecoderException("Invalid npc command type " + ordinal);
                    }
                    boolean block = buf.readBoolean();
                    return block ? new NpcCommandPayload(types[ordinal], buf.readBlockPos())
                            : new NpcCommandPayload(types[ordinal], buf.readVarInt());
                });

        public NpcCommandPayload(CommandType type, BlockPos pos) {
            this(type, true, pos, 0);
        }

        public NpcCommandPayload(CommandType type, int entityId) {
            this(type, false, BlockPos.ZERO, entityId);
        }

        public NpcCommandPayload(CommandType type, Entity entity) {
            this(type, entity.getId());
        }

        @Override
        public Type<NpcCommandPayload> type() {
            return TYPE;
        }

        public static void handle(NpcCommandPayload payload, IPayloadContext context) {
            NpcCommand.handleServerCommand(context.player(), payload.commandType, payload.blockTarget, payload.pos, payload.entityId);
        }
    }

    // ---------------------------------------------------------------- extended reach

    /**
     * An attack on an entity beyond the normal reach (client to server). The server checks that the player's main hand holds a weapon
     * with extended reach, using the player's entity interaction range (the weapons give it as an attribute modifier), and attacks.
     *
     * <p>1.21 already sends and checks attacks within the interaction range, so this is only needed for a client handler that
     * ray traces further than vanilla does; see port-notes. The held item check of the original ({@code IExtendedReachWeapon}) is
     * replaced by the range check, which is item independent.
     */
    public record ExtendedReachAttackPayload(int entityId) implements CustomPacketPayload {
        public static final Type<ExtendedReachAttackPayload> TYPE = new Type<>(Ids.of(AncientWarfareNPC.MOD_ID, "extended_reach_attack"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ExtendedReachAttackPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ExtendedReachAttackPayload::entityId,
                ExtendedReachAttackPayload::new);

        @Override
        public Type<ExtendedReachAttackPayload> type() {
            return TYPE;
        }

        public static void handle(ExtendedReachAttackPayload payload, IPayloadContext context) {
            Player player = context.player();
            Entity entity = player.level().getEntity(payload.entityId);
            if (entity == null || entity == player) {
                return;
            }
            if (!player.canInteractWithEntity(entity, 1.0D)) {
                return;
            }
            player.attack(entity);
        }
    }
}
