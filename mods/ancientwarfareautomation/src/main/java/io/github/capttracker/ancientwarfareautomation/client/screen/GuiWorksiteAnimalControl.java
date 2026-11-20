/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/gui/GuiWorksiteAnimalControl.
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.NumberInput;
import io.github.capttracker.ancientwarfareautomation.menu.AnimalControlMenu;

/** The animal farm's second screen: four number boxes for the animal limits. Closing it sends them and returns to the farm. */
public class GuiWorksiteAnimalControl extends GuiContainerBase<AnimalControlMenu> {

    private NumberInput pigCount;
    private NumberInput sheepCount;
    private NumberInput cowCount;
    private NumberInput chickenCount;

    public GuiWorksiteAnimalControl(AnimalControlMenu menu) {
        super(menu, 168, 64);
    }

    @Override
    public void initElements() {
        addGuiElement(new Label(8, 8, "guistrings.automation.max_pigs"));
        pigCount = new NumberInput(130, 8, 30, getContainer().maxPigs, this) {
            @Override
            public void onValueUpdated(float value) {
                getContainer().maxPigs = (int) value;
            }
        };
        pigCount.setIntegerValue();
        addGuiElement(pigCount);

        addGuiElement(new Label(8, 20, "guistrings.automation.max_sheep"));
        sheepCount = new NumberInput(130, 20, 30, getContainer().maxSheep, this) {
            @Override
            public void onValueUpdated(float value) {
                getContainer().maxSheep = (int) value;
            }
        };
        sheepCount.setIntegerValue();
        addGuiElement(sheepCount);

        addGuiElement(new Label(8, 32, "guistrings.automation.max_cows"));
        cowCount = new NumberInput(130, 32, 30, getContainer().maxCows, this) {
            @Override
            public void onValueUpdated(float value) {
                getContainer().maxCows = (int) value;
            }
        };
        cowCount.setIntegerValue();
        addGuiElement(cowCount);

        addGuiElement(new Label(8, 44, "guistrings.automation.max_chickens"));
        chickenCount = new NumberInput(130, 44, 30, getContainer().maxChickens, this) {
            @Override
            public void onValueUpdated(float value) {
                getContainer().maxChickens = (int) value;
            }
        };
        chickenCount.setIntegerValue();
        addGuiElement(chickenCount);
    }

    @Override
    public void setupElements() {
        pigCount.setValue(getContainer().maxPigs);
        sheepCount.setValue(getContainer().maxSheep);
        cowCount.setValue(getContainer().maxCows);
        chickenCount.setValue(getContainer().maxChickens);
    }

    /** Sends the limits and asks the server for the farm's main screen; this screen stays until that one opens. */
    @Override
    protected boolean onGuiCloseRequested() {
        getContainer().sendSettingsToServer(true);
        return false;
    }
}
