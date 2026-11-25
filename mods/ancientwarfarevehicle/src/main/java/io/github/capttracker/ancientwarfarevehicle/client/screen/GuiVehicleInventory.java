/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/gui/GuiVehicleInventory.
 */
package io.github.capttracker.ancientwarfarevehicle.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.menu.VehicleInventoryMenu;
import io.github.capttracker.ancientwarfarevehicle.network.VehicleEntityPayloads;
import io.github.capttracker.ancientwarfarevehicle.network.VehicleNetwork;

/**
 * The vehicle inventory screen: labels over the storage, ammo, upgrade, armor and player slots, the "Stats" button (opens
 * {@link GuiVehicleStats} over this screen, client only) and the "Pack" button (asks the server to pack the vehicle into its
 * spawner item), plus "-" and "+" to page through the storage.
 *
 * <p>The window is 195 wide and as high as the menu's layout needs ({@code playerY + 4 * 18 + 8 + 4}); the slots are the menu's.
 * The "-" and "+" move the storage by 3 rows (see {@link VehicleInventoryMenu}).
 */
public class GuiVehicleInventory extends GuiContainerBase<VehicleInventoryMenu> {
    private static final int X_SIZE = 171 + 16 + 8;

    public GuiVehicleInventory(VehicleInventoryMenu menu) {
        super(menu, X_SIZE, menu.vehicle == null ? 240 : menu.playerY + 4 * 18 + 8 + 4);
        this.shouldCloseOnVanillaKeys = true;
    }

    @Override
    public void initElements() {
        VehicleInventoryMenu menu = getContainer();
        VehicleBase vehicle = menu.vehicle;
        if (vehicle == null) {
            // the vehicle is not known to this client: an empty window, the server closes the menu
            return;
        }
        boolean hasStorage = vehicle.inventory.storageInventory.getSlots() > 0;
        if (hasStorage) {
            addGuiElement(new Label(8, menu.storageY - 10, "gui.ancientwarfarevehicles.inventory.storage"));
        }
        addGuiElement(new Label(8, menu.extrasY - 10, "gui.ancientwarfarevehicles.inventory.ammo"));
        addGuiElement(new Label(8 + 3 * 18 + 4, menu.extrasY - 10, "gui.ancientwarfarevehicles.inventory.upgrades"));
        addGuiElement(new Label(8 + 6 * 18 + 8, menu.extrasY - 10, "gui.ancientwarfarevehicles.inventory.armor"));
        addGuiElement(new Label(8, menu.playerY - 10, "gui.ancientwarfarevehicles.inventory.player_inventory"));

        Button stats = new Button(8, 4, 45, 16, "gui.ancientwarfarevehicles.inventory.stats") {
            @Override
            protected void onPressed() {
                // client only: the stats screen has no menu of its own and shows the vehicle's client state
                showScreen(new GuiVehicleStats(GuiVehicleInventory.this, vehicle));
            }
        };
        addGuiElement(stats);

        Button pack = new Button(8 + 45 + 4, 4, 45, 16, "gui.ancientwarfarevehicles.inventory.pack") {
            @Override
            protected void onPressed() {
                // the server checks that the sender is within reach of the vehicle
                VehicleNetwork.sendToServer(new VehicleEntityPayloads.PackCommand(vehicle.getId()));
                closeGui();
            }
        };
        addGuiElement(pack);

        if (hasStorage) {
            Button minus = new Button(171, menu.storageY - 1, 16, 16, "-") {
                @Override
                protected void onPressed() {
                    getContainer().prevPage();
                }
            };
            addGuiElement(minus);
            Button plus = new Button(171, menu.storageY + 3 * 18 - 16 - 1, 16, 16, "+") {
                @Override
                protected void onPressed() {
                    getContainer().nextPage();
                }
            };
            addGuiElement(plus);
        }
    }

    @Override
    public void setupElements() {
    }
}
