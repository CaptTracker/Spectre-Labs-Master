/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 3).
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfareautomation.menu.ApiaryMenu;

/** Screen of {@link ApiaryMenu}: the worksite inventory screen with its buttons. */
public class GuiWorksiteApiary extends GuiWorksiteBase<ApiaryMenu> {

    public GuiWorksiteApiary(ApiaryMenu menu) {
        super(menu);
    }

    @Override
    public void initElements() {
        addLabels();
        addSideSelectButton();
        addBoundsAdjustButton();
    }

    @Override
    public void setupElements() {
        // noop
    }
}
