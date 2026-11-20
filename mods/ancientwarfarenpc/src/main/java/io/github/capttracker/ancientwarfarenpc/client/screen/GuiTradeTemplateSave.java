/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/gui/GuiTradeTemplateSave.
 */
package io.github.capttracker.ancientwarfarenpc.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.Checkbox;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.Text;
import io.github.capttracker.ancientwarfare.menu.ScreenOnlyMenu;

/**
 * Asks for a name and saves the trade list of the setup screen as a template (1.12.2: {@code GuiTradeTemplateSave}), for every faction or for the
 * faction of the trader only. Shown over the setup screen, which has the menu that does the saving (on the server).
 */
public class GuiTradeTemplateSave extends GuiContainerBase<ScreenOnlyMenu> {
    private final GuiNpcFactionTradeSetup parent;
    private Label statusMessage;
    private int statusTicks = 0;

    GuiTradeTemplateSave(GuiNpcFactionTradeSetup parent) {
        super(new ScreenOnlyMenu(parent.getContainer().player.getInventory()), 320, 240);
        this.parent = parent;
    }

    @Override
    public void initElements() {
        addGuiElement(new Label(6, 6, "guistrings.template_name"));
        Text templateName = new Text(80, 4, 60, "", this);
        addGuiElement(templateName);
        Checkbox factionSpecific = new Checkbox(145, 4, 12, 12, "guistrings.template_faction_specific");
        addGuiElement(factionSpecific);
        addGuiElement(new Button(6, 18, 100, 12, "guistrings.save_template") {
            @Override
            protected void onPressed() {
                parent.getContainer().saveTradeTemplate(templateName.getText(), factionSpecific.checked());
                statusMessage.setText("guistrings.template.saved");
                statusTicks = 60;
            }
        });
        statusMessage = new Label(110, 18, "");
        addGuiElement(statusMessage);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (statusTicks > 0) {
            statusTicks--;
        }
        statusMessage.setVisible(statusTicks > 0);
    }

    @Override
    public void setupElements() {
    }

    @Override
    protected boolean onGuiCloseRequested() {
        parent.bindMenu();
        showScreen(parent);
        return false;
    }
}
