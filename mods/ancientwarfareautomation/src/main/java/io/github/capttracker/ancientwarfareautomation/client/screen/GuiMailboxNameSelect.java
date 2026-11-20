/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/gui/GuiMailboxNameSelect.
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.client.gui.elements.Text;
import io.github.capttracker.ancientwarfareautomation.menu.MailboxMenu;

import java.util.List;

/**
 * The mailbox name picker: a text box, buttons to add and delete a name in the list, and the list of existing names to
 * pick from. Used for the box's own name and for its target. Shares the menu of {@link GuiMailboxInventory}.
 */
public class GuiMailboxNameSelect extends GuiContainerBase<MailboxMenu> {

    private final GuiMailboxInventory parent;
    private final boolean name; // true if setting mailbox name, false for setting target name

    private Text nameInputArea;
    private CompositeScrolled nameSelectArea;

    public GuiMailboxNameSelect(GuiMailboxInventory parent, boolean name) {
        super(parent.getContainer());
        this.parent = parent;
        this.name = name;
    }

    @Override
    public void initElements() {
        String current = name ? getContainer().mailboxName : getContainer().targetName;
        nameInputArea = new Text(8, 8, 120, current == null ? "" : current, this);
        addGuiElement(nameInputArea);

        addGuiElement(new Button(8, 22, 55, 12, "guistrings.automation.add_mailbox") {
            @Override
            protected void onPressed() {
                String typed = nameInputArea.getText();
                if (!typed.isEmpty()) {
                    getContainer().handleNameAdd(typed);
                }
            }
        });

        addGuiElement(new Button(256 - 8 - 55, 8, 55, 12, "guistrings.automation.select_mailbox") {
            @Override
            protected void onPressed() {
                String typed = nameInputArea.getText();
                String selected = typed.isEmpty() ? null : typed;
                if (name) {
                    getContainer().handleNameSelection(selected);
                } else {
                    getContainer().handleTargetSelection(selected);
                }
                closeGui();
            }
        });

        addGuiElement(new Button(8 + 55, 22, 55, 12, "guistrings.automation.delete_mailbox") {
            @Override
            protected void onPressed() {
                String typed = nameInputArea.getText();
                if (!typed.isEmpty()) {
                    getContainer().handleNameDelete(typed);
                }
            }
        });

        nameSelectArea = new CompositeScrolled(this, 0, 20 + 4 + 12, 256, 240 - 8 - 12 - 4 - 12);
        addGuiElement(nameSelectArea);
    }

    @Override
    public void setupElements() {
        getContainer().setSlotsVisible(false);
        nameSelectArea.clearElements();

        List<String> names = getContainer().privateBox ? getContainer().privateBoxNames : getContainer().publicBoxNames;
        int totalHeight = 8;
        for (String listed : names) {
            if (listed.equals(getContainer().mailboxName) || listed.equals(getContainer().targetName)) {
                continue;
            }
            nameSelectArea.addGuiElement(new Button(8, totalHeight, 240 - 24 - 14, 12, listed) {
                @Override
                protected void onPressed() {
                    nameInputArea.setText(listed);
                }
            });
            totalHeight += 12;
        }
        nameSelectArea.setAreaSize(totalHeight);
    }

    /** Back to the mailbox screen. */
    @Override
    protected boolean onGuiCloseRequested() {
        getContainer().setSlotsVisible(true);
        parent.bindMenu();
        showScreen(parent);
        return false;
    }
}
