/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 8).
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfareautomation.menu.ProcessingMenu;

/** Screen of the sawmill and the bakery: the inventories, and buttons for the sides and the recipe screen. */
public class GuiWorksiteProcessing<M extends ProcessingMenu> extends GuiWorksiteBase<M> {

    public GuiWorksiteProcessing(M menu) {
        super(menu);
    }

    @Override
    public void initElements() {
        M menu = getContainer();
        addLabels();
        addGuiElement(new Label(8, menu.inputLabel, "guistrings.automation.processing.input"));
        if (menu.fuelLabel > 0) {
            addGuiElement(new Label(8, menu.fuelLabel, "guistrings.automation.processing.fuel"));
        }
        addSideSelectButton();
        addGuiElement(new Button(58, ySize - 8 - 12, 50, 12, "guistrings.automation.processing.recipes") {
            @Override
            protected void onPressed() {
                getContainer().requestOpenAlt();
            }
        });
    }

    @Override
    public void setupElements() {
        // noop
    }
}
