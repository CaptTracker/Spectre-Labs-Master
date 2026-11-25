/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: structure/gui/GuiGateControl and GuiGateControlCreative.
 */
package io.github.capttracker.ancientwarfarestructure.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.Text;
import io.github.capttracker.ancientwarfarestructure.menu.GateControlMenu;

/** A repack button and, in the creative variant, the owner name field. */
public class GuiGateControl extends GuiContainerBase<GateControlMenu> {
    private Text ownerInput;

    public GuiGateControl(GateControlMenu menu) {
        super(menu, menu.creative ? 178 : 71, menu.creative ? 60 : 36);
    }

    @Override
    public void initElements() {
        Button repack = new Button(8, 8, 55, 12, "guistrings.gate.repack") {
            @Override
            protected void onPressed() {
                getContainer().sendRepack();
                closeGui();
            }
        };
        addGuiElement(repack);
        if (getContainer().creative) {
            addGuiElement(new Label(8, 26, "guistrings.gate.owner"));
            ownerInput = new Text(8, 38, 162, getContainer().ownerName, this) {
                @Override
                public void onTextUpdated(String oldText, String newText) {
                    getContainer().sendOwner(newText);
                }
            };
            addGuiElement(ownerInput);
        }
    }

    @Override
    public void setupElements() {
        // the server sends the owner name just after the screen opens
        if (ownerInput != null && !ownerInput.getText().equals(getContainer().ownerName)) {
            ownerInput.setText(getContainer().ownerName);
        }
    }
}
