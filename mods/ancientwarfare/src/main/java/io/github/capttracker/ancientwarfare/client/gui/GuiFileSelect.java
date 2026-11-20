/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/GuiFileSelect.
 */
package io.github.capttracker.ancientwarfare.client.gui;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase.ActivationEvent;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.client.gui.elements.GuiElement;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.Text;
import io.github.capttracker.ancientwarfare.menu.AwMenu;

import java.io.File;

/** File browser shown over another screen of the same menu. Override {@link #onFileSelected}. */
public class GuiFileSelect extends GuiContainerBase<AwMenu> {

    private final GuiContainerBase<?> parent;
    /*
     * the display name of the current parent directory (used to click to go back)
     */
    private String parentPathName = "";
    /*
     * current path, used to update all others relative to this
     */
    private String currentPath = "";

    private final boolean allowNewFiles;

    private CompositeScrolled area;
    private Text input;

    public GuiFileSelect(GuiContainerBase<?> parent, String basePath, boolean allowNewFiles) {
        super(parent.getContainer());
        this.parent = parent;
        this.currentPath = basePath;
        this.parentPathName = new File(basePath).getParent();
        this.allowNewFiles = allowNewFiles;
    }

    @Override
    public void initElements() {
        area = new CompositeScrolled(this, 0, 40, 256, 200);
        addGuiElement(area);

        Button button = new Button(256 - 8 - 55 - 55, 8 + 14, 55, 12, "Accept");
        button.addNewListener(new Listener(Listener.MOUSE_UP) {
            @Override
            public boolean onEvent(GuiElement widget, ActivationEvent evt) {
                if (widget.isMouseOverElement(evt.mx, evt.my)) {
                    onAcceptClicked();
                }
                return true;
            }
        });
        addGuiElement(button);

        button = new Button(256 - 8 - 55, 8 + 14, 55, 12, "Cancel");
        button.addNewListener(new Listener(Listener.MOUSE_UP) {
            @Override
            public boolean onEvent(GuiElement widget, ActivationEvent evt) {
                if (widget.isMouseOverElement(evt.mx, evt.my)) {
                    onCancelClicked();
                }
                return true;
            }
        });
        addGuiElement(button);

        button = new Button(8, 8 + 14, 55, 12, "Back");
        button.addNewListener(new Listener(Listener.MOUSE_UP) {
            @Override
            public boolean onEvent(GuiElement widget, ActivationEvent evt) {
                if (widget.isMouseOverElement(evt.mx, evt.my)) {
                    onBackClicked();
                }
                return true;
            }
        });
        addGuiElement(button);

        input = new Text(8, 8, 160, "", this);
        input.addAllowedChar('.');
        addGuiElement(input);
    }

    private void handleFileClicked(String fileName) {
        File file = new File(currentPath, fileName);
        if (file.isDirectory()) {
            handleDirectorySelection(file.getAbsolutePath());
            input.setText("");
        } else {
            input.setText(fileName);
        }
        refreshGui();
    }

    private void handleDirectorySelection(String newPath) {
        if (newPath == null) {
            // already at the root: nothing above it to go back to (the original printed a stack trace here)
            return;
        }
        File file = new File(newPath);
        this.parentPathName = file.getParent();
        this.currentPath = newPath;
        this.refreshGui();
    }

    private void onAcceptClicked() {
        String name = input.getText();
        File selectedFile = new File(currentPath, name);
        if (!allowNewFiles && !selectedFile.exists()) {
            //TODO throw error, pop up error gui, etc
        } else {
            onFileSelected(selectedFile);
        }
        closeWindow();
    }

    private void closeWindow() {
        parent.bindMenu();
        showScreen(parent);
    }

    private void onCancelClicked() {
        closeWindow();
    }

    private void onBackClicked() {
        handleDirectorySelection(parentPathName);
    }

    @Override
    public void setupElements() {
        area.clearElements();
        int totalHeight = 3;
        File file = new File(currentPath);
        File[] dirFiles = file.listFiles();
        Label label;
        if (dirFiles != null) {
            for (File f : dirFiles) {
                if (f.isDirectory() || f.isFile()) {
                    label = new Label(8, totalHeight, f.getName());
                    label.addNewListener(new Listener(Listener.MOUSE_UP) {
                        @Override
                        public boolean onEvent(GuiElement widget, ActivationEvent evt) {
                            if (widget.isMouseOverElement(evt.mx, evt.my)) {
                                handleFileClicked(((Label) widget).getText());
                            }
                            return true;
                        }
                    });
                    area.addGuiElement(label);
                    totalHeight += 12;
                }
            }
        }
        area.setAreaSize(totalHeight);
    }

    /*
     * should be overriden by implementing class to add callback mechanism
     */
    public void onFileSelected(File file) {

    }

}
