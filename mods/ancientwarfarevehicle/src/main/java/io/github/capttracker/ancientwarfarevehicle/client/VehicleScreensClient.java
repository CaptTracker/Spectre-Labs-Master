/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the registerGui calls of vehicle/proxy/ClientProxy.
 */
package io.github.capttracker.ancientwarfarevehicle.client;

import io.github.capttracker.ancientwarfarevehicle.AncientWarfareVehicles;
import io.github.capttracker.ancientwarfarevehicle.client.screen.GuiVehicleAmmoSelection;
import io.github.capttracker.ancientwarfarevehicle.client.screen.GuiVehicleInventory;
import io.github.capttracker.ancientwarfarevehicle.init.AwVehicleMenus;
import io.github.capttracker.ancientwarfarevehicle.menu.VehicleInventoryMenu;
import io.github.capttracker.ancientwarfarevehicle.menu.VehicleMenu;
import io.github.capttracker.ancientwarfarevehicle.menu.VehicleMenuPayloads;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-only setup of the vehicle screens: the screen for each of the two menus, and the hook that makes the ammo selection key
 * ask the server to open its menu. The stats screen is not registered: it has no menu and is opened by the inventory screen.
 * Nothing outside the client package may reference this class.
 */
@EventBusSubscriber(modid = AncientWarfareVehicles.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class VehicleScreensClient {
    private VehicleScreensClient() {}

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.<VehicleMenu, GuiVehicleAmmoSelection>register(AwVehicleMenus.AMMO_SELECTION_MENU.get(),
                (menu, inventory, title) -> new GuiVehicleAmmoSelection(menu));
        event.<VehicleInventoryMenu, GuiVehicleInventory>register(AwVehicleMenus.INVENTORY_MENU.get(),
                (menu, inventory, title) -> new GuiVehicleInventory(menu));
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 1.12.2: the V key called NetworkHandler.openGui, which asked the server to open GUI 50 with the entity id
        VehicleInputClient.setAmmoSelectionOpener(vehicle ->
                PacketDistributor.sendToServer(new VehicleMenuPayloads.OpenAmmoSelection(vehicle.getId())));
    }
}
