/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 8).
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.Checkbox;
import io.github.capttracker.ancientwarfare.client.gui.elements.ItemSlot;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.NumberInput;
import io.github.capttracker.ancientwarfareautomation.menu.ProcessingRecipeMenu;
import net.minecraft.client.resources.language.I18n;

/**
 * The recipe screen of a production site: a row per recipe with its switch, the item it makes, the target stock (keep this many in storage, 0 = no
 * limit) and the stock now; under them the status of the site. Closing it returns to the main screen of the site. A site with more than
 * {@link #PAGE_SIZE} recipes (the smithy) shows them in pages, with buttons to turn the page.
 */
public class GuiWorksiteProcessingRecipes extends GuiContainerBase<ProcessingRecipeMenu> {
    private static final int ROW = 20;
    private static final int TOP = 30;
    /** The recipes shown at once. */
    private static final int PAGE_SIZE = 10;

    private Checkbox[] boxes;
    private NumberInput[] inputs;
    private Label[] stockLabels;
    private Label[] needLabels;
    private Label statusLabel;
    private Label pageLabel;
    private ItemSlot[] icons;
    private int page = 0;
    private boolean updating = false;

    public GuiWorksiteProcessingRecipes(ProcessingRecipeMenu menu) {
        super(menu, 240, TOP + Math.min(menu.enabled.length, PAGE_SIZE) * ROW + (menu.enabled.length > PAGE_SIZE ? 38 : 24));
    }

    @Override
    public void initElements() {
        ProcessingRecipeMenu menu = getContainer();
        int count = menu.enabled.length;
        boxes = new Checkbox[count];
        inputs = new NumberInput[count];
        stockLabels = new Label[count];
        needLabels = new Label[count];
        icons = new ItemSlot[count];
        addGuiElement(new Label(8, 8, "guistrings.automation.processing.make"));
        addGuiElement(new Label(128, 8, "guistrings.automation.processing.keep"));
        addGuiElement(new Label(170, 8, "guistrings.automation.processing.stock"));
        addGuiElement(new Label(128, 18, "guistrings.automation.processing.keep_hint").setColor(0xff999999));
        for (int i = 0; i < count; i++) {
            final int index = i;
            int y = TOP + (i % PAGE_SIZE) * ROW;
            ItemSlot icon = new ItemSlot(8, y, menu.blockEntity().recipe(i).icon(), this);
            icon.setRenderItemQuantity(false);
            icon.setHighlightOnMouseOver(false);
            addGuiElement(icon);
            icons[i] = icon;
            boxes[i] = new Checkbox(30, y + 2, 12, 12, menu.blockEntity().recipe(i).nameKey()) {
                @Override
                public void onToggled() {
                    if (!updating) {
                        getContainer().sendEnabled(index, checked());
                    }
                }
            };
            addGuiElement(boxes[i]);
            inputs[i] = new NumberInput(128, y + 3, 36, 0, this) {
                @Override
                public void onValueUpdated(float value) {
                    if (!updating) {
                        getContainer().sendTarget(index, (int) value);
                    }
                }
            };
            inputs[i].setIntegerValue();
            addGuiElement(inputs[i]);
            stockLabels[i] = new Label(170, y + 4, "0");
            addGuiElement(stockLabels[i]);
            needLabels[i] = new Label(196, y + 4, "");
            needLabels[i].setColor(0xffffaa33);
            addGuiElement(needLabels[i]);
        }
        int shown = Math.min(count, PAGE_SIZE);
        statusLabel = new Label(8, TOP + shown * ROW + 4, "");
        addGuiElement(statusLabel);
        if (count > PAGE_SIZE) {
            int y = TOP + shown * ROW + 18;
            addGuiElement(new Button(8, y, 20, 12, "<") {
                @Override
                protected void onPressed() {
                    turnPage(-1);
                }
            });
            pageLabel = new Label(36, y + 2, "");
            addGuiElement(pageLabel);
            addGuiElement(new Button(210, y, 20, 12, ">") {
                @Override
                protected void onPressed() {
                    turnPage(1);
                }
            });
        }
        showPage();
    }

    private int pageCount() {
        return (boxes.length + PAGE_SIZE - 1) / PAGE_SIZE;
    }

    private void turnPage(int by) {
        int pages = pageCount();
        page = ((page + by) % pages + pages) % pages;
        showPage();
    }

    /** Shows the rows of the current page and hides the others. */
    private void showPage() {
        for (int i = 0; i < boxes.length; i++) {
            boolean on = i / PAGE_SIZE == page;
            icons[i].setVisible(on);
            boxes[i].setVisible(on);
            inputs[i].setVisible(on);
            stockLabels[i].setVisible(on);
            needLabels[i].setVisible(on);
        }
        if (pageLabel != null) {
            pageLabel.setText(I18n.get("guistrings.automation.processing.page", page + 1, pageCount()));
        }
    }

    @Override
    public void setupElements() {
        ProcessingRecipeMenu menu = getContainer();
        updating = true;
        try {
            for (int i = 0; i < boxes.length; i++) {
                boxes[i].setChecked(menu.enabled[i]);
                if (inputs[i].getIntegerValue() != menu.targets[i]) {
                    inputs[i].setValue(menu.targets[i]);
                }
                stockLabels[i].setText(Integer.toString(menu.stocks[i]));
                needLabels[i].setText(menu.needed[i] > 0 ? I18n.get("guistrings.automation.processing.needed", menu.needed[i]) : "");
            }
            String key = "guistrings.automation.processing.status." + menu.status;
            statusLabel.setText(I18n.get("guistrings.automation.processing.status", I18n.exists(key) ? I18n.get(key) : menu.status));
        } finally {
            updating = false;
        }
    }

    /** Asks the server for the main screen of the site; this screen stays until that one opens. */
    @Override
    protected boolean onGuiCloseRequested() {
        getContainer().sendReturn();
        return false;
    }
}
