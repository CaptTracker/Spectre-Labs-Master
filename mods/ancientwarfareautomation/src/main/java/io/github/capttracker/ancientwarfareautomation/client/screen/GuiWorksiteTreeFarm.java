/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/gui/GuiWorksiteTreeFarm.
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfareautomation.menu.TreeFarmMenu;

/** Screen of {@link TreeFarmMenu}: the worksite inventory screen with its buttons. */
public class GuiWorksiteTreeFarm extends GuiWorksiteBase<TreeFarmMenu> {

    public GuiWorksiteTreeFarm(TreeFarmMenu menu) {
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
