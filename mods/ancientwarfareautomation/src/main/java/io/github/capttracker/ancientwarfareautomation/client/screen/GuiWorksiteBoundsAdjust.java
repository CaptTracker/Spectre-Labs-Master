/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/gui/GuiWorksiteBoundsAdjust.
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.Listener;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.GuiElement;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.Rectangle;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.BoundedWorksiteBlockEntity;
import io.github.capttracker.ancientwarfareautomation.menu.BoundsAdjustMenu;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * The work area screen: buttons to move and resize the area and a map of its columns. Farms that let the player choose
 * their blocks show the map as toggles. Everything is edited on the client and sent when the screen closes.
 */
public class GuiWorksiteBoundsAdjust extends GuiContainerBase<BoundsAdjustMenu> {

    private final boolean targetMode;

    private boolean boundsAdjusted = false;
    private boolean targetsAdjusted = false;
    private byte[] checkedMap = new byte[BoundsAdjustMenu.MAP_SIZE];

    public GuiWorksiteBoundsAdjust(BoundsAdjustMenu menu) {
        super(menu);
        this.shouldCloseOnVanillaKeys = true;
        targetMode = getContainer().getWorksite().userAdjustableBlocks();
    }

    private void setChecked(int x, int y, boolean checked) {
        if (targetMode) {
            checkedMap[y * 16 + x] = checked ? (byte) 1 : (byte) 0;
        }
    }

    private boolean isChecked(int x, int y) {
        return targetMode && checkedMap[y * 16 + x] == 1;
    }

    @Override
    public void initElements() {
        // the initial checked map arrives from the server (handlePacketData)
    }

    @Override
    public void setupElements() {
        this.clearElements();
        BoundsAdjustMenu menu = getContainer();

        addGuiElement(new Button(48, 12, 40, 12, "guistrings.inventory.direction.north") {
            @Override
            protected void onPressed() {
                if (menu.max.getZ() >= menu.getZ() && (menu.min.getX() > menu.getX() || menu.max.getX() < menu.getX())) {
                    menu.min = menu.min.relative(Direction.NORTH);
                    menu.max = menu.max.relative(Direction.NORTH);
                    boundsAdjusted = true;
                    refreshGui();
                }
            }
        });

        addGuiElement(new Button(48 + 40, 12, 40, 12, "guistrings.inventory.direction.south") {
            @Override
            protected void onPressed() {
                if (menu.min.getZ() <= menu.getZ() && (menu.min.getX() > menu.getX() || menu.max.getX() < menu.getX())) {
                    menu.min = menu.min.relative(Direction.SOUTH);
                    menu.max = menu.max.relative(Direction.SOUTH);
                    boundsAdjusted = true;
                    refreshGui();
                }
            }
        });

        addGuiElement(new Button(48 + 80, 12, 40, 12, "guistrings.inventory.direction.west") {
            @Override
            protected void onPressed() {
                if (menu.max.getX() >= menu.getX() && (menu.min.getZ() > menu.getZ() || menu.max.getZ() < menu.getZ())) {
                    menu.min = menu.min.relative(Direction.WEST);
                    menu.max = menu.max.relative(Direction.WEST);
                    boundsAdjusted = true;
                    refreshGui();
                }
            }
        });

        addGuiElement(new Button(48 + 120, 12, 40, 12, "guistrings.inventory.direction.east") {
            @Override
            protected void onPressed() {
                if (menu.min.getX() <= menu.getX() && (menu.min.getZ() > menu.getZ() || menu.max.getZ() < menu.getZ())) {
                    menu.min = menu.min.relative(Direction.EAST);
                    menu.max = menu.max.relative(Direction.EAST);
                    boundsAdjusted = true;
                    refreshGui();
                }
            }
        });

        addGuiElement(new Button(48, 24, 40, 12, "XSIZE-") {
            @Override
            protected void onPressed() {
                if (menu.max.getX() <= menu.min.getX()) {
                    return;
                }
                if (menu.min.getX() < menu.getX()) {
                    menu.min = menu.min.relative(Direction.EAST);
                } else {
                    menu.max = menu.max.relative(Direction.WEST);
                }
                boundsAdjusted = true;
                refreshGui();
            }
        });

        addGuiElement(new Button(48 + 40, 24, 40, 12, "XSIZE+") {
            @Override
            protected void onPressed() {
                int offset = menu.getWorksite().getBoundsMaxWidth() - menu.max.getX() + menu.min.getX() - 1;
                if (0 >= offset) {
                    return;
                }
                if (!isShiftKeyDown()) {
                    offset = 1;
                }
                if (menu.min.getX() < menu.getX()) {
                    menu.min = menu.min.relative(Direction.WEST, offset);
                } else {
                    menu.max = menu.max.relative(Direction.EAST, offset);
                }
                boundsAdjusted = true;
                refreshGui();
            }
        });

        addGuiElement(new Button(48 + 80, 24, 40, 12, "ZSIZE-") {
            @Override
            protected void onPressed() {
                if (menu.max.getZ() <= menu.min.getZ()) {
                    return;
                }
                if (menu.min.getZ() < menu.getZ()) {
                    menu.min = menu.min.relative(Direction.SOUTH);
                } else {
                    menu.max = menu.max.relative(Direction.NORTH);
                }
                boundsAdjusted = true;
                refreshGui();
            }
        });

        addGuiElement(new Button(48 + 120, 24, 40, 12, "ZSIZE+") {
            @Override
            protected void onPressed() {
                int offset = menu.getWorksite().getBoundsMaxWidth() - menu.max.getZ() + menu.min.getZ() - 1;
                if (0 >= offset) {
                    return;
                }
                if (!isShiftKeyDown()) {
                    offset = 1;
                }
                if (menu.min.getZ() < menu.getZ()) {
                    menu.min = menu.min.relative(Direction.NORTH, offset);
                } else {
                    menu.max = menu.max.relative(Direction.SOUTH, offset);
                }
                boundsAdjusted = true;
                refreshGui();
            }
        });

        if (menu.hasVerticalRange()) {
            // rework phase 3: the farms work 0 to 4 blocks above and below the site
            addGuiElement(new Button(48, 36, 40, 12, "Y-") {
                @Override
                protected void onPressed() {
                    if (menu.verticalRange > 0) {
                        menu.verticalRange--;
                        menu.rangeAdjusted = true;
                        refreshGui();
                    }
                }
            });
            addGuiElement(new Button(48 + 40, 36, 40, 12, "Y+") {
                @Override
                protected void onPressed() {
                    if (menu.verticalRange < BoundedWorksiteBlockEntity.MAX_VERTICAL_RANGE) {
                        menu.verticalRange++;
                        menu.rangeAdjusted = true;
                        refreshGui();
                    }
                }
            });
            addGuiElement(new Label(48 + 86, 38, I18n.get("guistrings.automation.vertical_range") + " +/-" + menu.verticalRange));
        }

        addLayout();
    }

    private void addLayout() {
        BoundsAdjustMenu menu = getContainer();
        int bits = menu.getWorksite().getBoundsMaxWidth() + 2;
        int size = 184 / bits;

        int tlx = (256 - (size * bits)) / 2 + size;
        int tly = 44 + size;

        int a = menu.getX() - menu.min.getX();
        int b = menu.getZ() - menu.min.getZ();

        // the block itself
        addGuiElement(new Rectangle(tlx + a * size, tly + b * size, size, size, 0x0000ffff, 0x0000ffff));

        int w = menu.max.getX() - menu.min.getX();
        int l = menu.max.getZ() - menu.min.getZ();
        // the map holds 16 x 16 columns; a (corrupt) bigger area is not drawn past it
        for (int x = 0; x <= w && x < 16; x++) {
            final int x1 = x;
            for (int y = 0; y <= l && y < 16; y++) {
                final int y1 = y;
                addGuiElement(new ToggledRectangle(tlx + x * size, tly + y * size, size, isChecked(x, y)) {
                    @Override
                    public void clicked() {
                        if (targetMode) {
                            super.clicked();
                            setChecked(x1, y1, checked);
                            targetsAdjusted = true;
                        }
                    }
                });
            }
        }
    }

    /** The server's checked map for the farm (sent right after the screen opened). */
    @Override
    public void handlePacketData(CompoundTag data) {
        if (data.contains("checkedMap", Tag.TAG_BYTE_ARRAY)) {
            byte[] received = data.getByteArray("checkedMap");
            if (received.length == BoundsAdjustMenu.MAP_SIZE) {
                checkedMap = received;
                refreshGui();
            }
        }
    }

    @Override
    protected boolean onGuiCloseRequested() {
        getContainer().onClose(boundsAdjusted, targetsAdjusted && targetMode, checkedMap);
        return super.onGuiCloseRequested();
    }

    private class ToggledRectangle extends Rectangle {
        boolean checked;
        private static final int CHECKED_COLOR = 0xff0000ff;
        private static final int HOVER_CHECKED_COLOR = 0xff8080ff;

        ToggledRectangle(int topLeftX, int topLeftY, int size, boolean checked) {
            super(topLeftX, topLeftY, size, size, 0x000000ff, 0x808080ff);
            this.checked = checked;
            addNewListener(new Listener(Listener.MOUSE_DOWN) {
                @Override
                public boolean onEvent(GuiElement widget, ActivationEvent evt) {
                    if (widget.isMouseOverElement(evt.mx, evt.my)) {
                        clicked();
                    }
                    return true;
                }
            });
        }

        public void clicked() {
            checked = !checked;
        }

        @Override
        protected final int getColor(int mouseX, int mouseY) {
            if (checked) {
                return isMouseOverElement(mouseX, mouseY) ? HOVER_CHECKED_COLOR : CHECKED_COLOR;
            }
            return super.getColor(mouseX, mouseY);
        }
    }
}
