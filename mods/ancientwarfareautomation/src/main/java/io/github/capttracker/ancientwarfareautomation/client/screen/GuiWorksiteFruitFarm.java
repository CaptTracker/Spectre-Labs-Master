/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/gui/GuiWorksiteFruitFarm.
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfareautomation.menu.FruitFarmMenu;

/** Screen of {@link FruitFarmMenu}: the worksite inventory screen with its buttons. */
public class GuiWorksiteFruitFarm extends GuiWorksiteBase<FruitFarmMenu> {

    public GuiWorksiteFruitFarm(FruitFarmMenu menu) {
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
