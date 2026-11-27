/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: VehicleBase.getCapability (the item handler capability of the vehicle entity).
 */
package io.github.capttracker.ancientwarfarevehicle.init;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Registrations of the vehicle entity slice. The entity type itself is registered by {@link AwVehicles#VEHICLE}; the
 * only thing this slice adds is the item handler of the vehicle.
 *
 * <p>Wiring: call {@link #registerEvents(IEventBus)} from the mod constructor (next to {@code AwVehicles.registerEvents}).
 */
public final class AwVehicleEntity {
    private AwVehicleEntity() {}

    /** Forces this class to initialise. */
    public static void load() {}

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(AwVehicleEntity::registerCapabilities);
    }

    /**
     * The storage inventory is the vehicle's item handler for hoppers and pipes (1.12.2: {@code getCapability} returned it
     * for every side). Combat vehicles have none, they expose an empty handler as before.
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerEntity(Capabilities.ItemHandler.ENTITY, AwVehicles.VEHICLE.get(), (vehicle, context) -> vehicle.inventory.storageInventory);
        event.registerEntity(Capabilities.ItemHandler.ENTITY, AwVehicles.SHIP.get(), (vehicle, context) -> vehicle.inventory.storageInventory);
        event.registerEntity(Capabilities.ItemHandler.ENTITY, AwVehicles.SIEGE_TOWER.get(), (vehicle, context) -> vehicle.inventory.storageInventory);
        event.registerEntity(Capabilities.ItemHandler.ENTITY_AUTOMATION, AwVehicles.SHIP.get(), (vehicle, side) -> vehicle.inventory.storageInventory);
        event.registerEntity(Capabilities.ItemHandler.ENTITY_AUTOMATION, AwVehicles.VEHICLE.get(), (vehicle, side) -> vehicle.inventory.storageInventory);
    }
}
