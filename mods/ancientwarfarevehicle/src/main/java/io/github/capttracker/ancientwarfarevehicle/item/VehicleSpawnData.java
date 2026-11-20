/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the "spawnData" NBT tag ({level:int, health:float}) of vehicle/item/ItemSpawner.
 */
package io.github.capttracker.ancientwarfarevehicle.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;

/**
 * Item data component of the vehicle spawner items ({@code ancientwarfarevehicle:vehicle_spawn_data}): the material level
 * (0 to 4) of the vehicle and, for a vehicle that was packed up, its remaining health. Without a health the vehicle spawns
 * with full health. A spawner without the component counts as level 0 (the original refused such an item).
 */
public record VehicleSpawnData(int level, Optional<Float> health) {
    public static final VehicleSpawnData DEFAULT = new VehicleSpawnData(0, Optional.empty());

    public static final Codec<VehicleSpawnData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("level", 0).forGetter(VehicleSpawnData::level),
            Codec.FLOAT.optionalFieldOf("health").forGetter(VehicleSpawnData::health)
    ).apply(instance, VehicleSpawnData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, VehicleSpawnData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, VehicleSpawnData::level,
            ByteBufCodecs.optional(ByteBufCodecs.FLOAT), VehicleSpawnData::health,
            VehicleSpawnData::new);

    public VehicleSpawnData(int level) {
        this(level, Optional.empty());
    }

    public VehicleSpawnData(int level, float health) {
        this(level, Optional.of(health));
    }
}
