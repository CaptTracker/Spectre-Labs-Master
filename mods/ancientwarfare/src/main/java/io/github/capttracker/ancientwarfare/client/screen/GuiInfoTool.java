/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/GuiInfoTool.
 */
package io.github.capttracker.ancientwarfare.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.menu.InfoToolMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

/** The info tool's item picker: click an inventory slot to print that stack's info (the menu asks the server to). */
public class GuiInfoTool extends GuiContainerBase<InfoToolMenu> {
    public GuiInfoTool(InfoToolMenu menu) {
        super(menu, 176, 90);
    }

    @Override
    public void initElements() {
        addGuiElement(new Label(2, -10, "guistrings.info_tool.click_to_print_stack_info"));
    }

    @Override
    public void setupElements() {
        //noop
    }

    /** Every slot click, drag, number key and drop key arrives here; none of them may move items. */
    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        getContainer().printItemInfo(slotId);
    }
}
