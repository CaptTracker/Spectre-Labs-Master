/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/faction/attributes/EntityVehicleProperty (an EntityProperty of the loot tables, now a loot condition).
 */
package io.github.capttracker.ancientwarfarenpc.registry;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.capttracker.ancientwarfarenpc.entity.vehicle.IVehicleUser;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcs;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

import java.util.Set;

/**
 * Loot condition {@code ancientwarfarenpc:rides_vehicle}: true if the killed entity is an NPC that rides a vehicle whose config name contains
 * {@code vehicle} ({@code ballista}, {@code catapult}, {@code trebuchet}, {@code cannon}, {@code hwacha}). The loot table of the faction siege
 * engineer uses it to drop ammunition that fits the vehicle. 1.12.2 wrote it as the entity property {@code ancientwarfarenpc:rides_vehicle}.
 *
 * <p>JSON: {@code {"condition": "ancientwarfarenpc:rides_vehicle", "vehicle": "ballista"}}.
 */
public record RidesVehicleCondition(String vehicle) implements LootItemCondition {
    public static final MapCodec<RidesVehicleCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            com.mojang.serialization.Codec.STRING.fieldOf("vehicle").forGetter(RidesVehicleCondition::vehicle)
    ).apply(instance, RidesVehicleCondition::new));

    @Override
    public LootItemConditionType getType() {
        return AwNpcs.RIDES_VEHICLE_CONDITION.get();
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.THIS_ENTITY);
    }

    @Override
    public boolean test(LootContext context) {
        Entity entity = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (entity instanceof IVehicleUser user && user.isRidingVehicle() && user.getAssignedVehicle().isPresent()) {
            VehicleBase vehicle = user.getAssignedVehicle().get();
            return vehicle.vehicleType.getConfigName().contains(this.vehicle);
        }
        return false;
    }
}
