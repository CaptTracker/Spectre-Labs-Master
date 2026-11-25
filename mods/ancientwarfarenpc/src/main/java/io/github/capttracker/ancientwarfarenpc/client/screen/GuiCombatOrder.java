/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/gui/GuiCombatOrder.
 */
package io.github.capttracker.ancientwarfarenpc.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.util.StringTools;
import io.github.capttracker.ancientwarfarenpc.menu.CombatOrderMenu;
import net.minecraft.core.BlockPos;

/**
 * The combat order editor (1.12.2: {@code GuiCombatOrder}): the patrol points with buttons to move or remove each. The order is sent to the server
 * when the screen closes, if it was changed.
 */
public class GuiCombatOrder extends GuiContainerBase<CombatOrderMenu> {
    private boolean hasChanged = false;
    private CompositeScrolled area;

    public GuiCombatOrder(CombatOrderMenu menu) {
        super(menu, 256, 220);
    }

    @Override
    public void initElements() {
        area = new CompositeScrolled(this, 0, 0, xSize, ySize);
        addGuiElement(area);
    }

    @Override
    public void setupElements() {
        area.clearElements();
        int totalHeight = 8;
        for (int i = 0; i < getContainer().order.size(); i++) {
            final int index = i;
            BlockPos pos = getContainer().order.get(i);
            area.addGuiElement(new Label(8, totalHeight + 1, StringTools.formatPos(pos)));

            area.addGuiElement(new Button(120, totalHeight, 12, 12, "+") {
                @Override
                protected void onPressed() {
                    getContainer().order.increment(index);
                    hasChanged = true;
                    refreshGui();
                }
            });
            area.addGuiElement(new Button(120 + 12, totalHeight, 12, 12, "-") {
                @Override
                protected void onPressed() {
                    getContainer().order.decrement(index);
                    hasChanged = true;
                    refreshGui();
                }
            });
            area.addGuiElement(new Button(120 + 12 + 12, totalHeight, 60, 12, "guistrings.npc.remove_point") {
                @Override
                protected void onPressed() {
                    hasChanged = true;
                    getContainer().order.remove(index);
                    refreshGui();
                }
            });
            totalHeight += 12;
        }
        area.setAreaSize(totalHeight);
    }

    @Override
    protected boolean onGuiCloseRequested() {
        if (hasChanged) {
            getContainer().sendOrder();
        }
        return super.onGuiCloseRequested();
    }
}
