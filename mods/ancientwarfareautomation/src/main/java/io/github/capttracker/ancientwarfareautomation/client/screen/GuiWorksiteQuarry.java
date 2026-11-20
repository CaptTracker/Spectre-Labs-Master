/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/gui/GuiWorksiteQuarry.
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfareautomation.menu.QuarryMenu;

/** Screen of {@link QuarryMenu}: the worksite inventory screen with a third button for the quarry's depth. */
public class GuiWorksiteQuarry extends GuiWorksiteBase<QuarryMenu> {

    public GuiWorksiteQuarry(QuarryMenu menu) {
        super(menu);
    }

    protected void addQuarryHeightButton() {
        addGuiElement(new Button(108, ySize - 8 - 12, 50, 12, "guistrings.automation.mine_settings") {
            @Override
            protected void onPressed() {
                getContainer().requestOpenQuarryBounds();
            }
        });
    }

    @Override
    public void initElements() {
        addLabels();
        addSideSelectButton();
        addBoundsAdjustButton();
        addQuarryHeightButton();
    }

    @Override
    public void setupElements() {
        // noop
    }
}
