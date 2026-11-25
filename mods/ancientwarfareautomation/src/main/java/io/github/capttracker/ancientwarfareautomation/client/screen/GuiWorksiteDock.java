/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4).
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfareautomation.menu.DockMenu;

/** Screen of {@link DockMenu}: the worksite inventory screen with the side button. */
public class GuiWorksiteDock extends GuiWorksiteBase<DockMenu> {

    public GuiWorksiteDock(DockMenu menu) {
        super(menu);
    }

    @Override
    public void initElements() {
        addLabels();
        addSideSelectButton();
    }

    @Override
    public void setupElements() {
        // noop
    }
}
