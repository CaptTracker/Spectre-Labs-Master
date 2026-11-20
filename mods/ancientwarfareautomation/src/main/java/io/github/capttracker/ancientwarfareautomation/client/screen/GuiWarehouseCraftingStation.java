/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/gui/GuiWarehouseCraftingStation.
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfareautomation.menu.WarehouseCraftingStationMenu;

/** The warehouse crafting table: the recipe template with its recipe arrows, and a warning when there is no warehouse below. */
public class GuiWarehouseCraftingStation extends GuiContainerBase<WarehouseCraftingStationMenu> {
    private Label missingWarehouse;

    public GuiWarehouseCraftingStation(WarehouseCraftingStationMenu menu) {
        super(menu, 176, 154);
    }

    @Override
    public void initElements() {
        addGuiElement(new ResearchCraftingElement(this, getContainer().getCraftingMemoryContainer(), 0, 0));

        missingWarehouse = new Label(30, 61, "guistrings.automation.missing_warehouse").setColor(0xFFFF0000).setShadow(false);
        addGuiElement(missingWarehouse);
    }

    @Override
    public void setupElements() {
        missingWarehouse.setVisible(getContainer().blockEntity().getWarehouse() == null);
    }
}
