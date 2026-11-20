/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/gui/GuiWorkOrder.
 */
package io.github.capttracker.ancientwarfarenpc.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.client.gui.elements.ItemSlot;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.NumberInput;
import io.github.capttracker.ancientwarfare.util.StringTools;
import io.github.capttracker.ancientwarfarenpc.menu.WorkOrderMenu;
import io.github.capttracker.ancientwarfarenpc.orders.WorkOrder.WorkEntry;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * The work order editor (1.12.2: {@code GuiWorkOrder}): how the worker picks the next worksite, the shift, and per worksite the block, position,
 * buttons to move or remove it and the work time in minutes. The order is sent to the server when the screen closes, if it was changed.
 */
public class GuiWorkOrder extends GuiContainerBase<WorkOrderMenu> {
    private boolean hasChanged = false;
    private CompositeScrolled area;
    private Button routeButton;
    private Button shiftButton;

    public GuiWorkOrder(WorkOrderMenu menu) {
        super(menu);
    }

    @Override
    public void initElements() {
        area = new CompositeScrolled(this, 0, 40, xSize, ySize - 40);
        addGuiElement(area);

        routeButton = new Button(8, 8, 100, 12, "") {
            @Override
            protected void onPressed() {
                getContainer().order.togglePriority();
                hasChanged = true;
                refreshGui();
            }
        };
        addGuiElement(routeButton);

        shiftButton = new Button(8, 24, 100, 12, "") {
            @Override
            protected void onPressed() {
                getContainer().order.toggleShift();
                hasChanged = true;
                refreshGui();
            }
        };
        addGuiElement(shiftButton);
    }

    @Override
    public void setupElements() {
        area.clearElements();
        String type = getContainer().order.getPriorityType().name().toLowerCase(Locale.ENGLISH);
        routeButton.setText("guistrings.npc.work_priority." + type);
        type = getContainer().order.isNightShift() ? "night" : "day";
        shiftButton.setText("guistrings.npc.work_shift." + type);

        List<WorkEntry> entries = getContainer().order.getEntries();
        int totalHeight = 8;
        int index = 0;
        for (WorkEntry entry : entries) {
            final int entryIndex = index;
            area.addGuiElement(new ItemSlot(8, totalHeight + 2, new ItemStack(entry.getBlock(player.level())), this));
            area.addGuiElement(new Label(8 + 20, totalHeight, StringTools.formatPos(entry.getPosition())));

            area.addGuiElement(new Button(8 + 20 + 20, totalHeight + 10, 12, 12, "+") {
                @Override
                protected void onPressed() {
                    getContainer().order.increment(entryIndex);
                    hasChanged = true;
                    refreshGui();
                }
            });
            area.addGuiElement(new Button(8 + 20 + 12 + 20, totalHeight + 10, 12, 12, "-") {
                @Override
                protected void onPressed() {
                    getContainer().order.decrement(entryIndex);
                    hasChanged = true;
                    refreshGui();
                }
            });
            area.addGuiElement(new Button(8 + 20 + 12 + 12 + 20, totalHeight + 10, 60, 12, "guistrings.npc.remove_work_point") {
                @Override
                protected void onPressed() {
                    getContainer().order.remove(entryIndex);
                    hasChanged = true;
                    refreshGui();
                }
            });

            area.addGuiElement(new Label(8 + 20 + 12 + 12 + 60 + 40 + 20, totalHeight, "guistrings.npc.work_length"));
            area.addGuiElement(new NumberInput(8 + 20 + 12 + 12 + 60 + 40 + 20, totalHeight + 10, 60, entry.getWorkLength() / 1200, this) {
                @Override
                public void onValueUpdated(float value) {
                    int ticks = (int) (value * 1200.f);
                    entry.setWorkLength(ticks);
                    hasChanged = true;
                }
            });
            totalHeight += 25;
            index++;
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
