/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/gui/GuiWorksiteFishControl.
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Checkbox;
import io.github.capttracker.ancientwarfareautomation.menu.FishControlMenu;

/** The fish farm's second screen: what to harvest. Closing it returns to the farm. */
public class GuiWorksiteFishControl extends GuiContainerBase<FishControlMenu> {

    private Checkbox fishBox;
    private Checkbox inkBox;

    public GuiWorksiteFishControl(FishControlMenu menu) {
        super(menu, 168, 48);
    }

    @Override
    public void initElements() {
        fishBox = new Checkbox(8, 8, 16, 16, "guistrings.automation.harvest_fish") {
            @Override
            public void onToggled() {
                getContainer().harvestFish = checked();
                getContainer().sendSettingsToServer(false);
            }
        };
        addGuiElement(fishBox);

        inkBox = new Checkbox(8, 8 + 16, 16, 16, "guistrings.automation.harvest_ink") {
            @Override
            public void onToggled() {
                getContainer().harvestInk = checked();
                getContainer().sendSettingsToServer(false);
            }
        };
        addGuiElement(inkBox);
    }

    @Override
    public void setupElements() {
        fishBox.setChecked(getContainer().harvestFish);
        inkBox.setChecked(getContainer().harvestInk);
    }

    /** Sends the settings and asks the server for the farm's main screen; this screen stays until that one opens. */
    @Override
    protected boolean onGuiCloseRequested() {
        getContainer().sendSettingsToServer(true);
        return false;
    }
}
