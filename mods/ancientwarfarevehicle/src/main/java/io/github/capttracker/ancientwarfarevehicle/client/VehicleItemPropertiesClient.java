/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the custom mesh definition in registerClient() of vehicle/item/ItemSpawner
 * (the model of a spawner depended on the vehicle type and its material level).
 */
package io.github.capttracker.ancientwarfarevehicle.client;

import io.github.capttracker.ancientwarfarevehicle.AncientWarfareVehicles;
import io.github.capttracker.ancientwarfarevehicle.init.AwVehicles;
import io.github.capttracker.ancientwarfarevehicle.item.VehicleSpawnerItem;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Registers the item model property {@code ancientwarfarevehicle:material_level} on the 21 spawner items. Each spawner's model
 * file ({@code models/item/<name>_spawner.json}) has overrides that pick the texture of level 1 to 4 by this property; level 0
 * is the base model. (1.12.2 chose one of 105 blockstate variants per type and level.)
 */
@EventBusSubscriber(modid = AncientWarfareVehicles.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class VehicleItemPropertiesClient {
    public static final ResourceLocation MATERIAL_LEVEL = Ids.of(AncientWarfareVehicles.MOD_ID, "material_level");

    private VehicleItemPropertiesClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> AwVehicles.SPAWNERS.forEach(spawner ->
                ItemProperties.register(spawner.get(), MATERIAL_LEVEL, (stack, level, entity, seed) -> VehicleSpawnerItem.getSpawnData(stack).level())));
    }
}
