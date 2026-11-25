/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/gui/GuiMailboxInventory.
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.Checkbox;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfareautomation.menu.MailboxMenu;

/**
 * The mailbox screen: the receive and send slots, the mailbox's name and target name with buttons that open the name
 * picker ({@link GuiMailboxNameSelect}), the side setup ({@link GuiMailboxInventorySideSetup}) and the two flags.
 * The sub screens share the menu; the slots are hidden while one of them is shown.
 *
 * <p>Not ported: the original put the mouse pointer back where it was when switching screens (LWJGL2
 * {@code Mouse.setCursorPosition}); the pointer keeps its screen position anyway in 1.21.
 */
public class GuiMailboxInventory extends GuiContainerBase<MailboxMenu> {

    private Label inputName;
    private Label outputName;
    private Checkbox autoExport;
    private Checkbox privateBox;

    public GuiMailboxInventory(MailboxMenu menu) {
        super(menu, 178, 240);
        ySize = getContainer().guiHeight;
    }

    @Override
    public void initElements() {
        addGuiElement(new Button(178 - 8 - 75, 8, 75, 12, "guistrings.automation.mailbox_name_select") {
            @Override
            protected void onPressed() {
                getContainer().setSlotsVisible(false);
                showScreen(new GuiMailboxNameSelect(GuiMailboxInventory.this, true));
            }
        });

        addGuiElement(new Button(178 - 8 - 75, 8 + 12 + 2 * 18, 75, 12, "guistrings.automation.mailbox_target_select") {
            @Override
            protected void onPressed() {
                getContainer().setSlotsVisible(false);
                showScreen(new GuiMailboxNameSelect(GuiMailboxInventory.this, false));
            }
        });

        addGuiElement(new Button(178 - 8 - 75, ySize - 8 - 12 - 12, 75, 12, "guistrings.inventory.setsides") {
            @Override
            protected void onPressed() {
                getContainer().setSlotsVisible(false);
                showScreen(new GuiMailboxInventorySideSetup(GuiMailboxInventory.this));
            }
        });

        autoExport = new Checkbox(8, ySize - 12 - 12 - 8, 12, 12, "guistrings.automation.auto_output") {
            @Override
            public void onToggled() {
                getContainer().handleAutoExportToggle(checked());
            }
        };
        autoExport.setChecked(getContainer().autoExport);
        addGuiElement(autoExport);

        privateBox = new Checkbox(8, ySize - 12 - 8, 12, 12, "guistrings.automation.private_mailbox") {
            @Override
            public void onToggled() {
                getContainer().handlePrivateBoxToggle(checked());
            }
        };
        privateBox.setChecked(getContainer().privateBox);
        addGuiElement(privateBox);

        inputName = new Label(8, 8, "");
        addGuiElement(inputName);

        outputName = new Label(8, 8 + 12 + 2 * 18, "");
        addGuiElement(outputName);
    }

    @Override
    public void setupElements() {
        getContainer().setSlotsVisible(true);
        inputName.setText(getContainer().mailboxName);
        outputName.setText(getContainer().targetName);
        autoExport.setChecked(getContainer().autoExport);
        privateBox.setChecked(getContainer().privateBox);
    }
}
