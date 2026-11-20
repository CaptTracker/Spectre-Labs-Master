/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/gui/GuiWarehouseStockViewer.
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.GuiRender;
import io.github.capttracker.ancientwarfare.client.gui.ITooltipRenderer;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.client.gui.elements.ItemSlot;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseStockViewerBlockEntity.WarehouseStockFilter;
import io.github.capttracker.ancientwarfareautomation.menu.WarehouseStockViewerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * The stock viewer sign's screen: up to {@value WarehouseStockViewerMenu#MAX_FILTERS} items with the count the warehouse
 * holds of each (the sign shows the same).
 */
public class GuiWarehouseStockViewer extends GuiContainerBase<WarehouseStockViewerMenu> {

    private CompositeScrolled area;

    public GuiWarehouseStockViewer(WarehouseStockViewerMenu menu) {
        super(menu, 178, 172);
    }

    @Override
    public void initElements() {
        area = new CompositeScrolled(this, 0, 0, 178, 80); // 240-8-8-4-4*18
        addGuiElement(area);
    }

    @Override
    public void setupElements() {
        area.clearElements();
        int totalHeight = 8;

        for (WarehouseStockFilter filter : getContainer().filters) {
            area.addGuiElement(new FilterItemSlot(8, totalHeight, filter, this));

            area.addGuiElement(new FilterRemoveButton(178 - 16 - 12, totalHeight + 3, filter));

            String quantity = String.valueOf(filter.getQuantity());
            area.addGuiElement(new Label(178 - 16 - 12 - 2 - font.width(quantity), totalHeight + 4, quantity));

            String name = filter.getFilterItem().isEmpty()
                    ? GuiRender.translate("guistrings.automation.empty_filter")
                    : filter.getFilterItem().getHoverName().getString();
            area.addGuiElement(new Label(8 + 20, totalHeight + 4, name));

            totalHeight += 16;
        }
        if (getContainer().filters.size() < WarehouseStockViewerMenu.MAX_FILTERS) {
            area.addGuiElement(new Button(8, totalHeight + 4, 178 - 16 - 8, 12, "guistrings.automation.new_filter") {
                @Override
                protected void onPressed() {
                    getContainer().filters.add(new WarehouseStockFilter(ItemStack.EMPTY, 0));
                    getContainer().sendFiltersToServer();
                    refreshGui();
                }
            });
            totalHeight += 16;
        }
        area.setAreaSize(totalHeight);
    }

    private class FilterRemoveButton extends Button {
        private final WarehouseStockFilter filter;

        FilterRemoveButton(int topLeftX, int topLeftY, WarehouseStockFilter filter) {
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

    private class FilterItemSlot extends ItemSlot {
        private final WarehouseStockFilter filter;

        FilterItemSlot(int topLeftX, int topLeftY, WarehouseStockFilter filter, ITooltipRenderer render) {
            super(topLeftX, topLeftY, filter.getFilterItem(), render);
            this.filter = filter;
            this.setRenderItemQuantity(false);
        }

        @Override
        public void onSlotClicked(ItemStack stack, boolean rightClicked) {
            ItemStack in = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
            this.setItem(in);
            filter.setItem(in.isEmpty() ? ItemStack.EMPTY : in.copy());
            getContainer().sendFiltersToServer();
        }
    }
}
