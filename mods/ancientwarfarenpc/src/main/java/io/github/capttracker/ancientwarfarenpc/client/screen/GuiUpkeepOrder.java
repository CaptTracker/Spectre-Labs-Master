/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/gui/GuiUpkeepOrder.
 */
package io.github.capttracker.ancientwarfarenpc.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.ItemSlot;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.NumberInput;
import io.github.capttracker.ancientwarfare.util.StringTools;
import io.github.capttracker.ancientwarfarenpc.menu.UpkeepOrderMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Optional;

/**
 * The upkeep order editor (1.12.2: {@code GuiUpkeepOrder}): the block at the upkeep point, its position, the side to use and the upkeep time in
 * minutes. The order is sent to the server when the screen closes, if it was changed.
 */
public class GuiUpkeepOrder extends GuiContainerBase<UpkeepOrderMenu> {
    private boolean hasChanged = false;

    public GuiUpkeepOrder(UpkeepOrderMenu menu) {
        super(menu, 246, 38);
    }

    @Override
    public void initElements() {
    }

    @Override
    public void setupElements() {
        clearElements();
        Optional<BlockPos> pos = getContainer().order.getUpkeepPosition();
        if (pos.isPresent()) {
            addGuiElement(new ItemSlot(8, 10, getContainer().upkeepBlock, this));
            addGuiElement(new Label(8 + 18 + 4, 8, StringTools.formatPos(pos.get())));

            addGuiElement(new Button(8 + 18 + 55 + 20, 8 + 10, 55, 12, getSideName(getContainer().order.getUpkeepBlockSide())) {
                @Override
                protected void onPressed() {
                    getContainer().order.changeBlockSide();
                    setText(getSideName(getContainer().order.getUpkeepBlockSide()));
                    hasChanged = true;
                    refreshGui();
                }
            });

            addGuiElement(new Label(8 + 18 + 55 + 55 + 30, 8, "guistrings.npc.upkeep_time"));
            addGuiElement(new NumberInput(8 + 18 + 55 + 55 + 30, 8 + 10, 60, (float) getContainer().order.getUpkeepAmount() / 1200.f, this) {
                @Override
                public void onValueUpdated(float value) {
                    float val = value * 1200.f;
                    getContainer().order.setUpkeepAmount((int) val);
                    hasChanged = true;
                }
            });
        } else {
            addGuiElement(new Label(8, 8, "guistrings.npc.assign_upkeep_point"));
        }
    }

    private static String getSideName(Direction side) {
        return side == null ? "" : "guistrings.inventory.direction." + side.getName();
    }

    @Override
    protected boolean onGuiCloseRequested() {
        if (hasChanged) {
            getContainer().sendOrder();
        }
        return super.onGuiCloseRequested();
    }
}
