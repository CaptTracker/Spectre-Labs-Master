/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/gui/GuiVehicleAmmoSelection.
 */
package io.github.capttracker.ancientwarfarevehicle.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfarevehicle.client.screen.elements.ButtonAmmo;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.menu.VehicleMenu;
import io.github.capttracker.ancientwarfarevehicle.missiles.IAmmo;

import java.util.List;

/**
 * The ammo selection list of a vehicle (1.12.2: {@code GUI_VEHICLE_AMMO_SELECTION}, opened with the V key): one button per ammo
 * type the vehicle's type can fire and that it currently holds. Choosing one selects it and closes the screen.
 */
public class GuiVehicleAmmoSelection extends GuiContainerBase<VehicleMenu> {
    private CompositeScrolled area;

    public GuiVehicleAmmoSelection(VehicleMenu menu) {
        super(menu, 256, 240);
        this.shouldCloseOnVanillaKeys = true;
    }

    @Override
    public void initElements() {
        area = new CompositeScrolled(this, 10, 40, xSize - 20, ySize - 40 - 10);
        addGuiElement(area);

        Button done = new Button(xSize - 35 - 5, 5, 35, 12, "gui.ancientwarfarevehicles.ammo.done") {
            @Override
            protected void onPressed() {
                closeGui();
            }
        };
        addGuiElement(done);
    }

    @Override
    public void setupElements() {
        area.clearElements();
        VehicleBase vehicle = getContainer().vehicle;
        if (vehicle == null) {
            // the vehicle is not known to this client; nothing to select
            return;
        }
        List<IAmmo> ammos = vehicle.vehicleType.getValidAmmoTypes().stream().filter(a -> vehicle.ammoHelper.getCountOf(a) > 0).toList();

        int currentY = 10;
        for (IAmmo ammo : ammos) {
            ButtonAmmo button = new ButtonAmmo(5, currentY, xSize - 20 - 20 - 5, 22, ammo, vehicle) {
                @Override
                protected void onPressed() {
                    super.onPressed();
                    closeGui();
                }
            };
            area.addGuiElement(button);
            currentY += 25;
        }
        area.setAreaSize(currentY);
    }
}
