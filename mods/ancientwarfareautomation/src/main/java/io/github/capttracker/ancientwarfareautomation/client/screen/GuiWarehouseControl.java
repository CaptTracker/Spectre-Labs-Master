/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/gui/GuiWarehouseControl.
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.GuiRender;
import io.github.capttracker.ancientwarfare.client.gui.Listener;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.Checkbox;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeItemSlots;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.client.gui.elements.GuiElement;
import io.github.capttracker.ancientwarfare.client.gui.elements.ItemSlot;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.Text;
import io.github.capttracker.ancientwarfare.inventory.ItemHashEntry;
import io.github.capttracker.ancientwarfare.util.InventoryTools.ComparatorItemHashEntry;
import io.github.capttracker.ancientwarfare.util.InventoryTools.ComparatorItemHashEntry.SortOrder;
import io.github.capttracker.ancientwarfareautomation.menu.WarehouseControlMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The warehouse control screen: sort button and order box, a search box, the stored items as a scrollable grid and the
 * stored / capacity line. A click on an item takes it out, a click on empty space with an item on the cursor puts it in.
 * Search: plain text matches the name and tooltip, {@code @mod} limits it to a mod, {@code @mod text} both.
 */
public class GuiWarehouseControl extends GuiContainerBase<WarehouseControlMenu> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TRANSL_GUI_AUTOMATION = "guistrings.automation.";
    private static final int STORED_LABEL_COLOR = 0xFF404040;
    private static final int FULL_LABEL_COLOR = 0xFFFF0000;

    private CompositeScrolled area;
    private Text input;
    private final ComparatorItemHashEntry sorter;
    private Label storedLabel;

    public GuiWarehouseControl(WarehouseControlMenu menu) {
        super(menu, 178, 240);
        sorter = new ComparatorItemHashEntry(menu.itemMap, menu.getSortType(), menu.getSortOrder());
    }

    private String storedText() {
        return I18n.get("guistrings.warehouse.storage", getContainer().currentStored, getContainer().maxStorage);
    }

    private static String orderLabelKey(SortOrder order) {
        return TRANSL_GUI_AUTOMATION + order.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public void initElements() {
        addGuiElement(new Button(8, 8, 110, 12, TRANSL_GUI_AUTOMATION + getContainer().getSortType().toString()) {
            @Override
            protected void onPressed() {
                getContainer().setSortType(getContainer().getSortType().next());
                setText(TRANSL_GUI_AUTOMATION + getContainer().getSortType().toString());
                refreshGui();
            }
        });

        // The original always started with the "descending" label, whatever the order was.
        Checkbox sortOrderBox = new Checkbox(8 + 55 + 55 + 4, 6, 16, 16, orderLabelKey(getContainer().getSortOrder())) {
            @Override
            public void onToggled() {
                super.onToggled();
                getContainer().setSortOrder(checked() ? SortOrder.ASCENDING : SortOrder.DESCENDING);
                label = GuiRender.translate(orderLabelKey(getContainer().getSortOrder()));
                refreshGui();
            }
        };
        sortOrderBox.setChecked(getContainer().getSortOrder() == SortOrder.ASCENDING);
        addGuiElement(sortOrderBox);

        input = new Text(8, 8 + 12 + 4, 178 - 16, "", this) {
            @Override
            public void onTextUpdated(String oldText, String newText) {
                if (!oldText.equals(newText)) {
                    refreshGui();
                }
            }
        };
        addGuiElement(input);

        area = new CompositeItemSlots(this, 0, 8 + 12 + 4 + 12 + 2, 178, 96, this);

        area.addNewListener(new Listener(Listener.MOUSE_DOWN) {
            @Override
            public boolean onEvent(GuiElement widget, ActivationEvent evt) {
                if (evt.mButton == 0 && widget.isMouseOverElement(evt.mx, evt.my) && !area.isMouseOverSubElement(evt.mx, evt.my)) {
                    getContainer().handleClientRequestSpecific(ItemStack.EMPTY, 0, isShiftKeyDown(), false);
                }
                return true;
            }
        });
        addGuiElement(area);

        addGuiElement(new Button(8, 240 - 8 - 12, 40, 12, "guistrings.automation.adjust_bounds") {
            @Override
            protected void onPressed() {
                getContainer().requestOpenBounds();
            }
        });

        storedLabel = new Label(8 + 40 + 4, 240 - 8 - 11, storedText());
        addGuiElement(storedLabel);
    }

    @Override
    public void setupElements() {
        area.clearElements();
        addInventoryViewElements();
        storedLabel.setText(storedText());
        storedLabel.setColor(getContainer().isWarehouseFull() ? FULL_LABEL_COLOR : STORED_LABEL_COLOR);
    }

    private List<net.minecraft.network.chat.Component> tooltipOf(ItemStack stack) {
        return stack.getTooltipLines(Item.TooltipContext.of(Minecraft.getInstance().level), player, TooltipFlag.NORMAL);
    }

    private void addInventoryViewElements() {
        List<ItemHashEntry> displayEntries = new ArrayList<>();

        for (ItemHashEntry entry : getContainer().itemMap.keySet()) {
            ItemStack stack = entry.getItemStack();
            if (matchesSearch(stack, entry.getNameAndTooltip(this::tooltipOf))) {
                displayEntries.add(entry);
            }
        }

        sortItems(displayEntries);

        int x = 0;
        int y = 0;
        int totalSize = 22;
        for (ItemHashEntry entry : displayEntries) {
            if (x >= 9) {
                x = 0;
                y++;
                totalSize += 18;
            }

            ItemStack displayStack = entry.getItemStack().copy();
            int count = getContainer().itemMap.getCount(entry);
            displayStack.setCount(count);

            ItemSlot slot = new ItemSlot(4 + x * 18, 4 + y * 18, displayStack, this) {
                @Override
                public void onSlotClicked(ItemStack stack, boolean rightClicked) {
                    getContainer().handleClientRequestSpecific(entry.getItemStack(), count, isShiftKeyDown(), rightClicked);
                }
            };
            area.addGuiElement(slot);
            x++;
        }
        area.setAreaSize(totalSize);
    }

    private boolean matchesSearch(ItemStack stack, String nameAndTooltip) {
        String searchInput = input.getText().toLowerCase(Locale.ROOT);
        if (searchInput.isEmpty()) {
            return true;
        }

        if (searchInput.startsWith("@")) {
            String[] searchStrings = searchInput.split(" ");
            String modName = searchStrings[0].substring(1);
            ResourceLocation registryName = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!registryName.getNamespace().contains(modName)) {
                return false;
            } else if (searchStrings.length <= 1) {
                return true;
            }
            searchInput = String.join(" ", Arrays.copyOfRange(searchStrings, 1, searchStrings.length));
        }

        return nameAndTooltip.contains(searchInput);
    }

    private void sortItems(List<ItemHashEntry> items) {
        sorter.setSortType(getContainer().getSortType());
        sorter.setSortOrder(getContainer().getSortOrder());
        try {
            items.sort(sorter);
        } catch (IllegalArgumentException ex) {
            LOGGER.error("Error sorting warehouse items: ", ex);
        }
    }
}
