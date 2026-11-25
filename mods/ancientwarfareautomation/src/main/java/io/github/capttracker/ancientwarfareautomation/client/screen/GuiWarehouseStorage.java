/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/gui/GuiWarehouseStorage.
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.ITooltipRenderer;
import io.github.capttracker.ancientwarfare.client.gui.Listener;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeItemSlots;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.client.gui.elements.GuiElement;
import io.github.capttracker.ancientwarfare.client.gui.elements.ItemSlot;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.inventory.ItemHashEntry;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseStorageFilter;
import io.github.capttracker.ancientwarfareautomation.menu.WarehouseStorageMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * One warehouse storage block's screen: the filter list on top (item slots you click with an item on the cursor, up to
 * {@value WarehouseStorageMenu#MAX_FILTERS}) and the items the block holds below. Click an item to take it out, click empty
 * space with an item on the cursor to put it in.
 */
public class GuiWarehouseStorage extends GuiContainerBase<WarehouseStorageMenu> {

    private CompositeScrolled area;
    private CompositeItemSlots area2;

    public GuiWarehouseStorage(WarehouseStorageMenu menu) {
        super(menu, 178, 240);
    }

    @Override
    public void initElements() {
        area = new CompositeScrolled(this, 0, 0, xSize, 74);
        addGuiElement(area);

        area2 = new CompositeItemSlots(this, 0, 74, xSize, 74, this);
        area2.addNewListener(new Listener(Listener.MOUSE_DOWN) {
            @Override
            public boolean onEvent(GuiElement widget, ActivationEvent evt) {
                if (evt.mButton == 0 && widget.isMouseOverElement(evt.mx, evt.my) && !area2.isMouseOverSubElement(evt.mx, evt.my)) {
                    getContainer().handleClientRequestSpecific(ItemStack.EMPTY, isShiftKeyDown(), false);
                }
                return true;
            }
        });
        addGuiElement(area2);
    }

    @Override
    public void setupElements() {
        area.clearElements();
        area2.clearElements();
        addInventoryViewElements();
        List<WarehouseStorageFilter> filters = getContainer().filters;

        int totalHeight = 8;

        for (WarehouseStorageFilter filter : filters) {
            area.addGuiElement(new FilterItemSlot(8, totalHeight, filter, this));

            String name = filter.getFilterItem().isEmpty() ? "" : filter.getFilterItem().getHoverName().getString();
            area.addGuiElement(new Label(20 + 8, totalHeight + 4, name));

            area.addGuiElement(new FilterRemoveButton(xSize - 16 - 12, totalHeight + 3, filter));

            totalHeight += 18;
        }

        if (filters.size() < WarehouseStorageMenu.MAX_FILTERS) {
            area.addGuiElement(new Button(8, totalHeight, 95, 12, "guistrings.automation.new_filter") {
                @Override
                protected void onPressed() {
                    getContainer().filters.add(new WarehouseStorageFilter(ItemStack.EMPTY));
                    getContainer().sendFiltersToServer();
                    refreshGui();
                }
            });
            totalHeight += 12;
        }
        area.setAreaSize(totalHeight);
    }

    private void addInventoryViewElements() {
        int x = 0;
        int y = 0;
        int totalSize = 22;
        for (ItemHashEntry entry : getContainer().itemMap.keySet()) {
            int qty = getContainer().itemMap.getCount(entry);
            ItemStack displayStack = entry.getItemStack().copy();
            displayStack.setCount(qty);

            if (x >= 9) {
                x = 0;
                y++;
                totalSize += 18;
            }
            area2.addGuiElement(new ItemSlot(4 + x * 18, 4 + y * 18, displayStack, this) {
                @Override
                public void onSlotClicked(ItemStack stack, boolean rightClicked) {
                    ItemStack reqStack = getStack();
                    // an item of the same kind on the cursor is put in (an empty request), anything else takes this item out
                    if (!(rightClicked && isShiftKeyDown()) && !reqStack.isEmpty() && ItemStack.isSameItemSameComponents(stack, reqStack)) {
                        reqStack = ItemStack.EMPTY;
                    }
                    getContainer().handleClientRequestSpecific(reqStack, isShiftKeyDown(), rightClicked);
                }
            });
            x++;
        }
        area2.setAreaSize(totalSize);
    }

    private class FilterRemoveButton extends Button {
        private final WarehouseStorageFilter filter;

        FilterRemoveButton(int topLeftX, int topLeftY, WarehouseStorageFilter filter) {
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
        private final WarehouseStorageFilter filter;

        FilterItemSlot(int topLeftX, int topLeftY, WarehouseStorageFilter filter, ITooltipRenderer render) {
            super(topLeftX, topLeftY, filter.getFilterItem(), render);
            this.filter = filter;
        }

        @Override
        public void onSlotClicked(ItemStack stack, boolean rightClicked) {
            ItemStack in = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
            this.setItem(in);
            filter.setFilterItem(in.isEmpty() ? ItemStack.EMPTY : in.copy());
            getContainer().sendFiltersToServer();
        }
    }
}
