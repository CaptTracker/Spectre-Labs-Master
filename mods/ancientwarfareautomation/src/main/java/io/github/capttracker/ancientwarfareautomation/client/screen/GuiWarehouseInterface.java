/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/gui/GuiWarehouseInterface.
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.ITooltipRenderer;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.client.gui.elements.ItemSlot;
import io.github.capttracker.ancientwarfare.client.gui.elements.NumberInput;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseInterfaceFilter;
import io.github.capttracker.ancientwarfareautomation.menu.WarehouseInterfaceMenu;
import net.minecraft.world.item.ItemStack;

/**
 * The warehouse interface screen: the filter list (item and quantity to keep in the interface, up to
 * {@value WarehouseInterfaceMenu#MAX_FILTERS}) above the interface's nine slots.
 */
public class GuiWarehouseInterface extends GuiContainerBase<WarehouseInterfaceMenu> {

    private CompositeScrolled area;

    public GuiWarehouseInterface(WarehouseInterfaceMenu menu) {
        super(menu, 178, 240);
    }

    @Override
    public void initElements() {
        area = new CompositeScrolled(this, 0, 0, xSize, 88);
        addGuiElement(area);
    }

    @Override
    public void setupElements() {
        area.clearElements();

        int totalHeight = 8;

        for (WarehouseInterfaceFilter filter : getContainer().filters) {
            area.addGuiElement(new FilterItemSlot(8, totalHeight, filter, this));

            NumberInput input = new FilterQuantityInput(8 + 30, totalHeight + 3, filter);
            input.setIntegerValue();
            area.addGuiElement(input);

            area.addGuiElement(new FilterRemoveButton(xSize - 16 - 12, totalHeight + 3, filter));

            totalHeight += 18;
        }

        if (getContainer().filters.size() < WarehouseInterfaceMenu.MAX_FILTERS) {
            area.addGuiElement(new Button(8, totalHeight, 95, 12, "guistrings.automation.new_filter") {
                @Override
                protected void onPressed() {
                    WarehouseInterfaceFilter filter = new WarehouseInterfaceFilter();
                    filter.setFilterQuantity(64);
                    getContainer().filters.add(filter);
                    getContainer().sendFiltersToServer();
                    refreshGui();
                }
            });
            totalHeight += 12;
        }

        area.setAreaSize(totalHeight);
    }

    private class FilterRemoveButton extends Button {
        private final WarehouseInterfaceFilter filter;

        FilterRemoveButton(int topLeftX, int topLeftY, WarehouseInterfaceFilter filter) {
            super(topLeftX, topLeftY, 12, 12, "-");
            this.filter = filter;
        }

        @Override
        protected void onPressed() {
            getContainer().filters.remove(filter);
            getContainer().sendFiltersToServer();
            refreshGui();
        }
    }

    private class FilterQuantityInput extends NumberInput {
        private final WarehouseInterfaceFilter filter;

        FilterQuantityInput(int topLeftX, int topLeftY, WarehouseInterfaceFilter filter) {
            super(topLeftX, topLeftY, 40, filter.getFilterQuantity(), GuiWarehouseInterface.this);
            this.filter = filter;
        }

        @Override
        public void onValueUpdated(float value) {
            this.filter.setFilterQuantity((int) value);
            refreshGui();
            getContainer().sendFiltersToServer();
        }
    }

    private class FilterItemSlot extends ItemSlot {
        private final WarehouseInterfaceFilter filter;

        FilterItemSlot(int topLeftX, int topLeftY, WarehouseInterfaceFilter filter, ITooltipRenderer render) {
            super(topLeftX, topLeftY, filter.getFilterItem(), render);
            this.filter = filter;
            this.setRenderItemQuantity(false);
        }

        @Override
        public void onSlotClicked(ItemStack stack, boolean rightClicked) {
            ItemStack in = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
            this.setItem(in);
            filter.setFilterQuantity(0);
            filter.setFilterItem(in.isEmpty() ? ItemStack.EMPTY : in.copy());
            getContainer().sendFiltersToServer();
        }
    }
}
