/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/gui/GuiTradeTemplateSelect (the original's setup screen no longer used it, it picks with GuiSelectFromList).
 */
package io.github.capttracker.ancientwarfarenpc.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.menu.ScreenOnlyMenu;

/**
 * A plain list of the trade list templates, one button each (1.12.2: {@code GuiTradeTemplateSelect}); picking one loads it into the list of the
 * setup screen. Nothing in the original opened it (the setup screen has its own picker), so nothing opens it here either.
 *
 * <p>Change from 1.12.2: the names come from the menu (the server's templates for the faction of the trader), so there is no faction name argument.
 */
public class GuiTradeTemplateSelect extends GuiContainerBase<ScreenOnlyMenu> {
    private final GuiNpcFactionTradeSetup parent;
    private CompositeScrolled area;

    protected GuiTradeTemplateSelect(GuiNpcFactionTradeSetup parent) {
        super(new ScreenOnlyMenu(parent.getContainer().player.getInventory()), 320, 240);
        this.parent = parent;
    }

    @Override
    public void initElements() {
        area = new CompositeScrolled(this, 0, 0, 320, 148);
        addGuiElement(area);
    }

    @Override
    public void setupElements() {
        area.clearElements();
        int totalHeight = 8;
        for (String templateName : parent.getContainer().getTemplateNames()) {
            area.addGuiElement(new Button(8, totalHeight, 320 - 8 - 16, 12, templateName) {
                @Override
                protected void onPressed() {
                    parent.getContainer().applyTradeTemplate(templateName);
                    parent.bindMenu();
                    showScreen(parent);
                    parent.refreshGui();
                }
            });
            totalHeight += 12;
        }
        area.setAreaSize(totalHeight);
    }

    @Override
    protected boolean onGuiCloseRequested() {
        parent.bindMenu();
        showScreen(parent);
        return false;
    }
}
