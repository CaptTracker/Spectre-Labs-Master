/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/network/PacketEntity as used by NpcBase (owner name, attack target and skin
 * settings updates sent to the players tracking an NPC).
 */
package io.github.capttracker.ancientwarfarenpc.entity;

import io.github.capttracker.ancientwarfare.api.IEntityPacketHandler;
import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Server-to-client entity data: an entity id and an NBT tag that the entity interprets in
 * {@link IEntityPacketHandler#handlePacketData}. Used by {@link NpcBase} (key {@code ownerName}, {@code attackTarget})
 * and by the skin slice (the skin settings tag).
 */
public final class NpcEntityPayloads {
    private NpcEntityPayloads() {}

    /** Registers the payloads of this class; call from the {@code RegisterPayloadHandlersEvent} listener of the module. */
    public static void registerPayloads(PayloadRegistrar registrar) {
        registrar.playToClient(EntityData.TYPE, EntityData.CODEC, EntityData::handle);
    }

    /** Sends the tag to every player tracking the entity (1.12.2: {@code NetworkHandler.sendToAllTracking(entity, new PacketEntity(entity))}). */
    public static void sendToTracking(Entity entity, CompoundTag tag) {
        if (!entity.level().isClientSide) {
            PacketDistributor.sendToPlayersTrackingEntity(entity, new EntityData(entity.getId(), tag));
        }
    }

    /** What 1.12.2 called {@code PacketEntity}: blind data for an {@link IEntityPacketHandler} entity. */
    public record EntityData(int entityId, CompoundTag tag) implements CustomPacketPayload {
        public static final Type<EntityData> TYPE = new Type<>(Ids.of(AncientWarfareNPC.MOD_ID, "entity_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, EntityData> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, EntityData::entityId,
                ByteBufCodecs.COMPOUND_TAG, EntityData::tag,
                EntityData::new);

        @Override
        public Type<EntityData> type() {
            return TYPE;
        }

        /** Client side: the entity may not be known yet (the packet raced the spawn), then the data is dropped like in 1.12.2. */
        public static void handle(EntityData payload, IPayloadContext context) {
            Entity entity = context.player().level().getEntity(payload.entityId);
            if (entity instanceof IEntityPacketHandler handler) {
                handler.handlePacketData(payload.tag);
            }
        }
    }
}
