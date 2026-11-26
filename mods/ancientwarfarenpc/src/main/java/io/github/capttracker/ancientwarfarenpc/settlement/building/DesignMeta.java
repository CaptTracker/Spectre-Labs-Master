/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): building designs.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** The name and numbers of a design that a client screen needs (no blocks). Sent from the server to the clients. */
public record DesignMeta(ResourceLocation id, String name, DesignCategory category, DesignStyle style, int sizeX, int sizeY, int sizeZ,
                         Direction front, int beds, int storage, String worksite, List<String> tags, boolean builtIn, int minLevel,
                         int minPopulation) {
    private static final int MAX_TAGS = 32;

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(id);
        buf.writeUtf(name, 64);
        buf.writeByte(category.ordinal());
        buf.writeByte(style.ordinal());
        buf.writeVarInt(sizeX);
        buf.writeVarInt(sizeY);
        buf.writeVarInt(sizeZ);
        buf.writeEnum(front);
        buf.writeVarInt(beds);
        buf.writeVarInt(storage);
        buf.writeUtf(worksite, 128);
        int count = Math.min(tags.size(), MAX_TAGS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeUtf(tags.get(i), 128);
        }
        buf.writeBoolean(builtIn);
        buf.writeVarInt(minLevel);
        buf.writeVarInt(minPopulation);
    }

    public static DesignMeta read(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        String name = buf.readUtf(64);
        DesignCategory category = DesignCategory.byOrdinal(buf.readByte());
        DesignStyle style = DesignStyle.byOrdinal(buf.readByte());
        int sx = buf.readVarInt();
        int sy = buf.readVarInt();
        int sz = buf.readVarInt();
        Direction front = buf.readEnum(Direction.class);
        int beds = buf.readVarInt();
        int storage = buf.readVarInt();
        String worksite = buf.readUtf(128);
        int count = Math.min(buf.readVarInt(), MAX_TAGS);
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tags.add(buf.readUtf(128));
        }
        return new DesignMeta(id, name, category, style, sx, sy, sz, front, beds, storage, worksite, List.copyOf(tags), buf.readBoolean(),
                buf.readVarInt(), buf.readVarInt());
    }
}
