/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/gui/GuiMailboxInventorySideSetup.
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfare.block.AwDirection;
import io.github.capttracker.ancientwarfare.block.BlockRotationHandler.RelativeSide;
import io.github.capttracker.ancientwarfare.block.BlockRotationHandler.RotationType;
import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfareautomation.menu.MailboxMenu;
import net.minecraft.core.Direction;

/**
 * The mailbox's side setup: for each side of the block, whether it receives (top), sends (bottom) or is off. Shares the
 * menu of {@link GuiMailboxInventory}.
 */
public class GuiMailboxInventorySideSetup extends GuiContainerBase<MailboxMenu> {

    private final GuiMailboxInventory parent;

    public GuiMailboxInventorySideSetup(GuiMailboxInventory parent) {
        super(parent.getContainer(), 240, 108);
        this.parent = parent;
    }

    @Override
    public void initElements() {
        // everything is built in setupElements, it depends on the lists the server sends
    }

    @Override
    public void setupElements() {
        this.clearElements();
        getContainer().setSlotsVisible(false);

        addGuiElement(new Label(8, 6, "guistrings.automation.block_side"));
        addGuiElement(new Label(74, 6, "guistrings.automation.direction"));
        addGuiElement(new Label(128, 6, "guistrings.automation.inventory_accessed"));

        int height = 18;
        for (RelativeSide side : RotationType.FOUR_WAY.getValidSides()) {
            addGuiElement(new Label(8, height, side.getTranslationKey()));

            Direction dir = RelativeSide.getMCSideToAccess(RotationType.FOUR_WAY, getContainer().blockEntity().getPrimaryFacing(), side);
            addGuiElement(new Label(74, height, AwDirection.getDirectionFor(dir).getTranslationKey()));

            RelativeSide accessed;
            if (dir != null && getContainer().receivedSides.contains(dir)) {
                accessed = RelativeSide.TOP;
            } else if (dir != null && getContainer().sendSides.contains(dir)) {
                accessed = RelativeSide.BOTTOM;
            } else {
                accessed = RelativeSide.NONE;
            }
            addGuiElement(new SideButton(128, height, side, accessed));

            height += 14;
        }
    }

    /** Back to the mailbox screen. */
    @Override
    protected boolean onGuiCloseRequested() {
        getContainer().setSlotsVisible(true);
        parent.bindMenu();
        showScreen(parent);
        return false;
    }

    private class SideButton extends Button {
        final RelativeSide side; // base side
        RelativeSide selection; // accessed side

        SideButton(int topLeftX, int topLeftY, RelativeSide side, RelativeSide selection) {
            super(topLeftX, topLeftY, 55, 12, selection.getTranslationKey());
            if (side == null) {
                throw new IllegalArgumentException("access side may not be null..");
            }
            this.side = side;
            this.selection = selection;
        }

        @Override
        protected void onPressed() {
            switch (selection) {
                case TOP -> selection = RelativeSide.BOTTOM;
                case BOTTOM -> selection = RelativeSide.NONE;
                default -> selection = RelativeSide.TOP;
            }
            setText(selection.getTranslationKey());
            getContainer().sendSlotChange(side, selection);
            getContainer().updateSides(side, selection);
        }
    }
}
