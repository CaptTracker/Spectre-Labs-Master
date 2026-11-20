/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the AWGateInfo item NBT (pos1, pos2) of structure/item/ItemGateSpawner.
 */
package io.github.capttracker.ancientwarfarestructure.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;

/** The two corners that a player has chosen with a gate spawner item (the item component {@code gate_bounds}). */
public record GateBounds(Optional<BlockPos> pos1, Optional<BlockPos> pos2) {
    public static final GateBounds EMPTY = new GateBounds(Optional.empty(), Optional.empty());

    public static final Codec<GateBounds> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.optionalFieldOf("pos1").forGetter(GateBounds::pos1),
            BlockPos.CODEC.optionalFieldOf("pos2").forGetter(GateBounds::pos2)
    ).apply(instance, GateBounds::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, GateBounds> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), GateBounds::pos1,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), GateBounds::pos2,
            GateBounds::new);

    /** Both corners are set. */
    public boolean isComplete() {
        return pos1.isPresent() && pos2.isPresent();
    }

    public GateBounds withPos1(BlockPos pos) {
        return new GateBounds(Optional.of(pos), pos2);
    }

    public GateBounds withPos2(BlockPos pos) {
        return new GateBounds(pos1, Optional.of(pos));
    }
}
