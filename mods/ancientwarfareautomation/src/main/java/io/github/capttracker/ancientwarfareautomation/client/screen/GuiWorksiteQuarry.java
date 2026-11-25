/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/gui/GuiWorksiteQuarry.
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfareautomation.menu.QuarryMenu;

/** Screen of {@link QuarryMenu}: the worksite inventory screen with the ladder and torch slots, a hint when the mine waits for ladders and a third button for the mine settings. */
public class GuiWorksiteQuarry extends GuiWorksiteBase<QuarryMenu> {
    private Label waitingHint;

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
        addGuiElement(new Label(8, getContainer().ladderLabel, "guistrings.automation.quarry_ladders"));
        addGuiElement(new Label(98, getContainer().ladderLabel, "guistrings.automation.quarry_torches"));
        waitingHint = new Label(8, getContainer().hintLabel, "guistrings.automation.quarry_waiting_ladder").setColor(0xFFFF6A5A);
        waitingHint.setVisible(getContainer().waitingForLadder);
        addGuiElement(waitingHint);
        addSideSelectButton();
        addBoundsAdjustButton();
        addQuarryHeightButton();
    }

    @Override
    public void setupElements() {
        if (waitingHint != null) {
            waitingHint.setVisible(getContainer().waitingForLadder);
        }
    }
}
