/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the GUI ids GUI_VEHICLE_AMMO_SELECTION (50) and GUI_VEHICLE_INVENTORY (51) of
 * core/network/NetworkHandler and the container registration of vehicle/AncientWarfareVehicles.
 */
package io.github.capttracker.ancientwarfarevehicle.init;

import io.github.capttracker.ancientwarfarevehicle.AncientWarfareVehicles;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBridge;
import io.github.capttracker.ancientwarfarevehicle.menu.VehicleInventoryMenu;
import io.github.capttracker.ancientwarfarevehicle.menu.VehicleMenu;
import io.github.capttracker.ancientwarfarevehicle.menu.VehicleMenuPayloads;
import io.github.capttracker.ancientwarfarevehicle.network.VehicleNetwork;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Registrations of the vehicle menus slice: the two menu types, the payload that asks the server for the ammo selection and
 * the hook that opens the inventory when a player shift-clicks a vehicle. GUI 52 (the vehicle stats) has no menu: the screen is
 * opened by the client from the inventory screen.
 *
 * <p>Wiring: {@code AwVehicleMenus.load()} and {@code AwVehicleMenus.registerEvents(modBus)} from the mod constructor before
 * {@code REG.register(modBus)}. The screens are registered by {@code client.VehicleScreensClient} (client only, automatic).
 */
public final class AwVehicleMenus {
    private AwVehicleMenus() {}

    // 1.12.2: GUI_VEHICLE_AMMO_SELECTION (50)
    public static final DeferredHolder<MenuType<?>, MenuType<VehicleMenu>> AMMO_SELECTION_MENU =
            AncientWarfareVehicles.REG.menus.register("vehicle_ammo_selection", () -> IMenuTypeExtension.create(VehicleMenu::new));

    // 1.12.2: GUI_VEHICLE_INVENTORY (51)
    public static final DeferredHolder<MenuType<?>, MenuType<VehicleInventoryMenu>> INVENTORY_MENU =
            AncientWarfareVehicles.REG.menus.register("vehicle_inventory", () -> IMenuTypeExtension.create(VehicleInventoryMenu::new));

    /** Forces this class (and so the registrations above) to initialise. Call from the mod constructor. */
    public static void load() {}

    /** Installs the inventory opener and registers the menu payloads. Call from the mod constructor. */
    public static void registerEvents(IEventBus modBus) {
        VehicleBridge.setInventoryOpener(VehicleInventoryMenu::open);
        modBus.addListener(AwVehicleMenus::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        VehicleMenuPayloads.registerPayloads(event.registrar(VehicleNetwork.VERSION));
    }
}
