/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/gui/GuiWorksiteAutoCrafting.
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfareautomation.menu.AutoCraftingMenu;

/** The auto crafting table: the recipe template with its recipe arrows, and a "Craft" button. */
public class GuiWorksiteAutoCrafting extends GuiContainerBase<AutoCraftingMenu> {

    public GuiWorksiteAutoCrafting(AutoCraftingMenu menu) {
        super(menu, 176, 216);
    }

    @Override
    public void initElements() {
        addGuiElement(new ResearchCraftingElement(this, getContainer().getCraftingMemoryContainer(), 0, 0));

        addGuiElement(new Button(143 - 18, 44, 36, 12, "guistrings.automation.craft") {
            @Override
            protected void onPressed() {
                getContainer().craft();
            }
        });
    }

    @Override
    public void setupElements() {
        // noop
    }
}
