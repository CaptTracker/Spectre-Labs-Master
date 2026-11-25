/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/gui/GuiWarehouseStockLinker.
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.GuiRender;
import io.github.capttracker.ancientwarfare.client.gui.ITooltipRenderer;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.client.gui.elements.ItemSlot;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.NumberInput;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseStockLinkerBlockEntity.WarehouseStockFilter;
import io.github.capttracker.ancientwarfareautomation.menu.WarehouseStockLinkerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * The stock linker's screen: up to {@value WarehouseStockLinkerMenu#MAX_FILTERS} comparisons, each an item, the count the
 * warehouse holds, a sign (click to cycle, right click to cycle back) and a value to compare to. The block gives a
 * redstone signal while any comparison is true (a value of 0 is always false).
 */
public class GuiWarehouseStockLinker extends GuiContainerBase<WarehouseStockLinkerMenu> {

    private CompositeScrolled area;

    public GuiWarehouseStockLinker(WarehouseStockLinkerMenu menu) {
        super(menu, 250, 172);
    }

    @Override
    public void initElements() {
        area = new CompositeScrolled(this, 0, 0, 250, 80); // 240-8-8-4-4*18
        addGuiElement(area);
    }

    @Override
    public void setupElements() {
        area.clearElements();
        int totalHeight = 8;

        for (WarehouseStockFilter filter : getContainer().filters) {
            // item slot
            area.addGuiElement(new FilterItemSlot(8, totalHeight, filter, this));

            // item name
            String name = filter.getFilterItem().isEmpty()
                    ? GuiRender.translate("guistrings.automation.empty_filter")
                    : filter.getFilterItem().getHoverName().getString();
            area.addGuiElement(new Label(8 + 20, totalHeight + 4, name));

            // remove button
            area.addGuiElement(new FilterRemoveButton(xSize - 20, totalHeight + 4, filter));

            // the count in the warehouse
            String quantity = String.valueOf(filter.getQuantity());
            area.addGuiElement(new Label(xSize - 120 - font.width(quantity), totalHeight + 4, quantity));

            // the sign
            area.addGuiElement(new Button(xSize - 80, totalHeight + 4, 10, 12, filter.equalitySignType.getTranslationKey()) {
                @Override
                protected void onPressed(int mButton) {
                    filter.changeEqualitySign(mButton == 1);
                    getContainer().sendFiltersToServer();
                    refreshGui();
                }
            });

            // the value compared to
            NumberInput compareValue = new NumberInput(xSize - 70, totalHeight + 4, 50, filter.compareValue, this) {
                @Override
                public void onValueUpdated(float value) {
                    filter.setCompareValue((int) value);
                    getContainer().sendFiltersToServer();
                    refreshGui();
                }
            };
            compareValue.setIntegerValue();
            area.addGuiElement(compareValue);
            compareValue.setValue(filter.compareValue);
            totalHeight += 16;
        }

        if (getContainer().filters.size() < WarehouseStockLinkerMenu.MAX_FILTERS) {
            area.addGuiElement(new Button(8, totalHeight + 4, 178 - 16 - 8, 12, "guistrings.automation.new_filter") {
                @Override
                protected void onPressed() {
                    getContainer().filters.add(new WarehouseStockFilter(ItemStack.EMPTY, 0, 0, 0));
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
