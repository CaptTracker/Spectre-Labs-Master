/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: TileTorqueBase.sendDataToClient and TileFlywheelStorage.sendDataToClient, which sent
 * the Core PacketBlockEvent to everybody tracking the chunk.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.torque;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The small "type and value" messages the torque block entities send to clients (animation percentages, the
 * redstone-powered flag of the flywheel controller). They travel as ordinary block entity update packets whose tag
 * holds only an event type and a value, and are sent only to the players that track the chunk, so no custom packet
 * has to be registered. The full state for late joiners goes through the usual update tag.
 *
 * <p>The client side is {@code onDataPacket} of the block entities, which checks {@link #isEvent(CompoundTag)}.
 */
public final class TorqueNetwork {
    private static final String EVENT_TYPE_TAG = "torqueEventType";
    private static final String EVENT_DATA_TAG = "torqueEventData";

    private TorqueNetwork() {
    }

    /** Server only: sends a type (0 to 5 is a side, 1 for the flywheel storage, 7 powered) and a value to the trackers of the block entity. */
    public static void sendEvent(BlockEntity blockEntity, int type, int data) {
        if (!(blockEntity.getLevel() instanceof ServerLevel level)) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt(EVENT_TYPE_TAG, type);
        tag.putInt(EVENT_DATA_TAG, data);
        ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(blockEntity, (be, access) -> tag);
        for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(new ChunkPos(blockEntity.getBlockPos()), false)) {
            player.connection.send(packet);
        }
    }

    public static boolean isEvent(CompoundTag tag) {
        return tag.contains(EVENT_TYPE_TAG);
    }

    public static int eventType(CompoundTag tag) {
        return tag.getInt(EVENT_TYPE_TAG);
    }

    public static int eventData(CompoundTag tag) {
        return tag.getInt(EVENT_DATA_TAG);
    }
}
