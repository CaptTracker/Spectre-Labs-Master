/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/GuiSelectFromList.
 */
package io.github.capttracker.ancientwarfare.client.gui;

import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.client.gui.elements.Text;
import io.github.capttracker.ancientwarfare.menu.AwMenu;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Filterable list picker shown over another screen of the same menu. Picking an entry hands it to
 * {@code setEntry}, returns to the parent screen and refreshes it.
 */
public class GuiSelectFromList<T> extends GuiContainerBase<AwMenu> {
    private final GuiContainerBase<?> parent;
    private T entry;
    private final Function<T, String> getDisplayName;
    private final Supplier<List<T>> getList;
    private final Consumer<T> setEntry;
    private CompositeScrolled area;
    private Text selectionLabel;
    private final boolean showFilter;

    public GuiSelectFromList(GuiContainerBase<?> parent, @Nullable T entry, Function<T, String> getDisplayName, Supplier<List<T>> getList, Consumer<T> setEntry) {
        this(parent, entry, getDisplayName, getList, setEntry, true);
    }

    public GuiSelectFromList(GuiContainerBase<?> parent, @Nullable T entry, Function<T, String> getDisplayName, Supplier<List<T>> getList, Consumer<T> setEntry, boolean showFilter) {
        this(parent, entry, getDisplayName, getList, setEntry, showFilter, parent.getContainer());
    }

    public GuiSelectFromList(GuiContainerBase<?> parent, @Nullable T entry, Function<T, String> getDisplayName, Supplier<List<T>> getList, Consumer<T> setEntry, boolean showFilter, AwMenu container) {
        super(container, 350, 240);
        this.parent = parent;
        this.entry = entry;
        this.getDisplayName = getDisplayName;
        this.getList = getList;
        this.setEntry = setEntry;
        this.showFilter = showFilter;
    }

    @Override
    public void initElements() {
        if (showFilter) {
            selectionLabel = new Text(8, 30, 350 - 8 - 16, entry == null ? "" : getDisplayName.apply(entry), this) {
                @Override
                protected void handleKeyInput(int keyCode, char ch) {
                    String old = getText();
                    super.handleKeyInput(keyCode, ch);
                    String text = getText();
                    if (!text.equals(old)) {
                        refreshGui();
                    }
                }

                @Override
                public void onTextUpdated(String oldText, String newText) {
                    refreshGui();
                }
            };
            addGuiElement(selectionLabel);
        }

        area = new CompositeScrolled(this, 0, 40, 350, 200);
        addGuiElement(area);
    }

    @Override
    public void setupElements() {
        area.clearElements();
        int totalHeight = 8;
        Button button;
        for (T listEntry : getFilteredList()) {
            button = new Button(8, totalHeight, 350 - 8 - 16, 12, getDisplayName.apply(listEntry)) {
                @Override
                protected void onPressed() {
                    setEntry.accept(listEntry);
                    showScreen(parent);
                    parent.refreshGui();
                }
            };
            area.addGuiElement(button);
            totalHeight += 12;
        }
        area.setAreaSize(totalHeight);
    }

    private List<T> getFilteredList() {
        if (!showFilter) {
            return getList.get();
        }

        return getList.get().stream().filter(input -> input.toString().contains(selectionLabel.getText())).collect(Collectors.toList());
    }

    @Override
    protected boolean onGuiCloseRequested() {
        parent.bindMenu();
        showScreen(parent);
        return false;
    }
}
