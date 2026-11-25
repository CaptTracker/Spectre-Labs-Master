/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/gui/GuiWorksiteInventorySideSelection.
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfare.block.AwDirection;
import io.github.capttracker.ancientwarfare.block.BlockRotationHandler.RelativeSide;
import io.github.capttracker.ancientwarfare.block.BlockRotationHandler.RotationType;
import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfareautomation.menu.InventorySideMenu;
import net.minecraft.core.Direction;

import java.util.Set;

/**
 * The inventory side screen: one row per machine side with the direction it faces in the world and a button that cycles
 * which inventory that side reaches. Closing it asks the server for the worksite's main screen.
 */
public class GuiWorksiteInventorySideSelection extends GuiContainerBase<InventorySideMenu> {

    public GuiWorksiteInventorySideSelection(InventorySideMenu menu) {
        super(menu, 128 + 55 + 8, 106);
    }

    @Override
    public void initElements() {
        // everything is built in setupElements, it depends on the map the server sends
    }

    /** The server opens the worksite's main screen; this screen stays until that one opens. */
    @Override
    protected boolean onGuiCloseRequested() {
        getContainer().close();
        return false;
    }

    @Override
    public void setupElements() {
        this.clearElements();

        addGuiElement(new Label(8, 6, "guistrings.automation.block_side"));
        addGuiElement(new Label(74, 6, "guistrings.automation.direction"));
        addGuiElement(new Label(128, 6, "guistrings.automation.inventory_accessed"));

        int height = 18;
        for (RelativeSide side : getContainer().sideMap.keySet()) {
            addGuiElement(new Label(8, height, side.getTranslationKey()));

            Direction dir = RelativeSide.getMCSideToAccess(RotationType.FOUR_WAY, getContainer().blockEntity().getPrimaryFacing(), side);
            addGuiElement(new Label(74, height, AwDirection.getDirectionFor(dir).getTranslationKey()));

            RelativeSide accessed = getContainer().sideMap.get(side);
            addGuiElement(new SideButton(128, height, side, accessed));

            height += 14;
        }
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
            RelativeSide[] all = RelativeSide.values();
            int ordinal = selection.ordinal();
            Set<RelativeSide> validSides = getContainer().blockEntity().getSideInventories().keySet();
            for (int i = 0; i < all.length; i++) {
                ordinal++;
                if (ordinal >= all.length) {
                    ordinal = 0;
                }
                RelativeSide next = all[ordinal];
                if (validSides.contains(next) || next == RelativeSide.NONE) {
                    selection = next;
                    break;
                }
            }
            getContainer().sideMap.put(side, selection);
            setText(selection.getTranslationKey());
            getContainer().sendSlotChange(side, selection);
        }
    }
}
