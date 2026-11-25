/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/gui/GuiWorksiteFishFarm.
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfareautomation.menu.FishFarmMenu;

/** Screen of {@link FishFarmMenu}: the worksite inventory screen with its buttons. */
public class GuiWorksiteFishFarm extends GuiWorksiteBase<FishFarmMenu> {

    public GuiWorksiteFishFarm(FishFarmMenu menu) {
        super(menu);
    }

    @Override
    public void initElements() {
        addLabels();
        addSideSelectButton();
        addBoundsAdjustButton();
        addAltControlsButton();
    }

    @Override
    public void setupElements() {
        // noop
    }
}
