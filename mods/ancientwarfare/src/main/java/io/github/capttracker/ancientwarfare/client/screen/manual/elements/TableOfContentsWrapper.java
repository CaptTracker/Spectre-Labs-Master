/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/manual/elements/TableOfContentsWrapper.
 */
package io.github.capttracker.ancientwarfare.client.screen.manual.elements;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.GuiRender;
import io.github.capttracker.ancientwarfare.client.gui.Listener;
import io.github.capttracker.ancientwarfare.client.gui.elements.GuiElement;
import io.github.capttracker.ancientwarfare.client.gui.elements.TextButton;
import io.github.capttracker.ancientwarfare.client.screen.manual.GuiManual;
import io.github.capttracker.ancientwarfare.client.screen.manual.IElementWrapperCreator;
import io.github.capttracker.ancientwarfare.manual.TableOfContentsElement;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** A list of links to other manual categories, one text button per entry. */
public class TableOfContentsWrapper extends BaseElementWrapper<TableOfContentsElement> {
    private static final int SPACING = 5;
    private final List<GuiElement> guiElements = new ArrayList<>();

    public TableOfContentsWrapper(GuiManual gui, int topLeftY, int width, int height, TableOfContentsElement element) {
        super(gui, 0, topLeftY, width, height, element);

        int currentY = topLeftY;
        int fontHeight = GuiRender.unicodeLineHeight();
        int itemHeight = fontHeight + SPACING;
        for (TableOfContentsElement.TableOfContentsItem item : element.getItems()) {
            addGuiElement(new TextButton(0, currentY, GuiRender.unicodeWidth(item.getText()), fontHeight, item.getText(), Color.BLACK, Color.CYAN) {
                @Override
                protected void onPressed(int mButton) {
                    getGui().setCurrentCategory(item.getCategory());
                }
            });
            currentY += itemHeight;
        }

        this.addNewListener(new Listener(Listener.MOUSE_TYPES) {
            @Override
            public boolean onEvent(GuiElement widget, GuiContainerBase.ActivationEvent evt) {
                if ((evt.type & Listener.MOUSE_TYPES) != 0) {
                    if (isMouseOverElement(evt.mx, evt.my)) {
                        // pass the event on to the buttons
                        int x = evt.mx;
                        int y = evt.my;
                        for (GuiElement button : guiElements) {
                            button.handleMouseInput(evt);
                        }
                        evt.mx = x;
                        evt.my = y;
                    } else if (evt.type == Listener.MOUSE_UP) {
                        for (GuiElement button : guiElements) {
                            button.setSelected(false);
                        }
                    }
                }
                return true;
            }
        });
    }

    private void addGuiElement(GuiElement guiElement) {
        guiElements.add(guiElement);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (GuiElement element : guiElements) {
            element.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void updateGuiPosition(int guiLeft, int guiTop) {
        super.updateGuiPosition(guiLeft, guiTop);
        guiElements.forEach(e -> e.updateGuiPosition(guiLeft, guiTop));
    }

    public static class Creator implements IElementWrapperCreator<TableOfContentsElement> {
        @Override
        public List<BaseElementWrapper<?>> construct(GuiManual gui, int topLeftY, int width, int remainingPageHeight, int emptyPageHeight, TableOfContentsElement element) {
            int fontHeight = GuiRender.unicodeLineHeight();
            int itemHeight = fontHeight + SPACING;
            int remainingCount = element.getItems().size();
            int remainingHeight = remainingCount * itemHeight;

            if (remainingHeight <= remainingPageHeight) {
                return List.of(new TableOfContentsWrapper(gui, topLeftY, width, remainingHeight, element));
            }

            int pageHeight = remainingPageHeight;
            List<BaseElementWrapper<?>> ret = new ArrayList<>();
            int yCoord = topLeftY;
            while (remainingCount > 0) {
                int renderCount = Math.min(remainingCount, pageHeight / itemHeight);

                TableOfContentsElement toc = new TableOfContentsElement(
                        new ArrayList<>(element.getItems().subList(element.getItems().size() - remainingCount, element.getItems().size() - remainingCount + renderCount)));
                ret.add(new TableOfContentsWrapper(gui, yCoord, width, renderCount * itemHeight, toc));

                remainingCount -= renderCount;
                pageHeight = emptyPageHeight;
                yCoord = 0;
            }

            return ret;
        }
    }
}
