/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/gui/GuiWorksiteBase.
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfareautomation.menu.WorksiteMenuBase;

/**
 * Base of the worksite inventory screens: the captions above each group of slots (positions come from the menu) and the
 * button row under them. The buttons ask the server for the side, bounds and alternative screens.
 */
public abstract class GuiWorksiteBase<M extends WorksiteMenuBase<?>> extends GuiContainerBase<M> {

    protected GuiWorksiteBase(M menu) {
        super(menu, 178, 240);
        this.ySize = getContainer().guiHeight + 12;
    }

    protected void addLabels() {
        M menu = getContainer();
        if (menu.topLabel > 0) {
            addGuiElement(new Label(8, menu.topLabel, "guistrings.inventory.side.top"));
        }
        if (menu.frontLabel > 0) {
            addGuiElement(new Label(8, menu.frontLabel, "guistrings.inventory.front"));
        }
        if (menu.bottomLabel > 0) {
            addGuiElement(new Label(8, menu.bottomLabel, "guistrings.inventory.bottom"));
        }
        if (menu.rearLabel > 0) {
            addGuiElement(new Label(8, menu.rearLabel, "guistrings.inventory.rear"));
        }
        if (menu.leftLabel > 0) {
            addGuiElement(new Label(8, menu.leftLabel, "guistrings.inventory.left"));
        }
        if (menu.rightLabel > 0) {
            addGuiElement(new Label(8, menu.rightLabel, "guistrings.inventory.right"));
        }
        if (menu.playerLabel > 0) {
            addGuiElement(new Label(8, menu.playerLabel, "guistrings.inventory.player"));
        }
    }

    protected void addSideSelectButton() {
        addGuiElement(new Button(8, ySize - 8 - 12, 50, 12, "guistrings.inventory.setsides") {
            @Override
            protected void onPressed() {
                getContainer().requestOpenSides();
            }
        });
    }

    protected void addBoundsAdjustButton() {
        addGuiElement(new Button(58, ySize - 8 - 12, 50, 12, "guistrings.automation.adjust_bounds") {
            @Override
            protected void onPressed() {
                getContainer().requestOpenBounds();
            }
        });
    }

    protected void addAltControlsButton() {
        addGuiElement(new Button(108, ySize - 8 - 12, 50, 12, "guistrings.automation.alt_controls") {
            @Override
            protected void onPressed() {
                getContainer().requestOpenAlt();
            }
        });
    }
}
