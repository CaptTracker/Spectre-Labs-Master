/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5).
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfareautomation.menu.StableMenu;

/** Screen of {@link StableMenu}: the inventory with its caption, a hint on what couriers keep in it, and the side button. */
public class GuiWorksiteStable extends GuiWorksiteBase<StableMenu> {

    public GuiWorksiteStable(StableMenu menu) {
        super(menu);
    }

    @Override
    public void initElements() {
        StableMenu menu = getContainer();
        addGuiElement(new Label(8, menu.topLabel, "guistrings.automation.stable_feed"));
        addGuiElement(new Label(8, menu.frontLabel, "guistrings.automation.stable_needs"));
        addGuiElement(new Label(8, menu.playerLabel, "guistrings.inventory.player"));
        addSideSelectButton();
    }

    @Override
    public void setupElements() {
        // noop
    }
}
