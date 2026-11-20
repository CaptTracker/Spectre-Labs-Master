package io.github.capttracker.spectrefactions.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * Anything that has reputation: a faction, or a factionless player.
 */
public record ActorId(Kind kind, UUID id) implements Comparable<ActorId> {
    public enum Kind {
        FACTION, PLAYER
    }

    public static final StreamCodec<ByteBuf, ActorId> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, a -> a.kind == Kind.FACTION,
            UUIDUtil.STREAM_CODEC, ActorId::id,
            (isFaction, id) -> new ActorId(isFaction ? Kind.FACTION : Kind.PLAYER, id));

    public static ActorId faction(UUID id) {
        return new ActorId(Kind.FACTION, id);
    }

    public static ActorId player(UUID id) {
        return new ActorId(Kind.PLAYER, id);
    }

    public boolean isFaction() {
        return kind == Kind.FACTION;
    }

    public boolean isPlayer() {
        return kind == Kind.PLAYER;
    }

    @Override
    public int compareTo(ActorId o) {
        int c = kind.compareTo(o.kind);
        return c != 0 ? c : id.compareTo(o.id);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", kind.name());
        tag.putUUID("id", id);
        return tag;
    }

    public static ActorId fromTag(CompoundTag tag) {
        Kind k = "PLAYER".equals(tag.getString("kind")) ? Kind.PLAYER : Kind.FACTION;
        return new ActorId(k, tag.getUUID("id"));
    }
}
