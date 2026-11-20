/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the stack tags npcType, npcSubtype, faction and npcStoredData of npc/item/ItemNpcSpawner.
 */
package io.github.capttracker.ancientwarfarenpc.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * The content of an NPC spawner stack (the {@code ancientwarfarenpc:npc_spawner} data component): the NPC type ({@code combat}, {@code worker},
 * {@code faction.archer}, ...), its subtype (may be empty), the faction of a faction NPC, and when the stack holds a whole NPC (a repacked or
 * dead one) the data written by {@code NpcBase.writeAdditionalItemData}. The compound is never changed in place; use {@link #storedDataCopy()}.
 */
public record NpcSpawnerData(String npcType, String npcSubtype, Optional<String> faction, Optional<CompoundTag> storedData) {
    public static final Codec<NpcSpawnerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("npcType").forGetter(NpcSpawnerData::npcType),
            Codec.STRING.optionalFieldOf("npcSubtype", "").forGetter(NpcSpawnerData::npcSubtype),
            Codec.STRING.optionalFieldOf("faction").forGetter(NpcSpawnerData::faction),
            CompoundTag.CODEC.optionalFieldOf("npcStoredData").forGetter(NpcSpawnerData::storedData)
    ).apply(instance, NpcSpawnerData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, NpcSpawnerData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NpcSpawnerData::npcType,
            ByteBufCodecs.STRING_UTF8, NpcSpawnerData::npcSubtype,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), NpcSpawnerData::faction,
            ByteBufCodecs.optional(ByteBufCodecs.COMPOUND_TAG), NpcSpawnerData::storedData,
            NpcSpawnerData::new);

    public NpcSpawnerData(String npcType, String npcSubtype, String faction) {
        this(npcType, npcSubtype, faction.isEmpty() ? Optional.empty() : Optional.of(faction), Optional.empty());
    }

    public NpcSpawnerData withStoredData(CompoundTag data) {
        return new NpcSpawnerData(npcType, npcSubtype, faction, Optional.of(data.copy()));
    }

    /** A copy of the stored NPC data, or null if the stack holds no NPC. */
    @Nullable
    public CompoundTag storedDataCopy() {
        return storedData.map(CompoundTag::copy).orElse(null);
    }

    /** The custom name of the stored NPC (the tag {@code name} of its data), if it has one. */
    public Optional<String> storedName() {
        return storedData.filter(tag -> tag.contains("name")).map(tag -> tag.getString("name"));
    }
}
