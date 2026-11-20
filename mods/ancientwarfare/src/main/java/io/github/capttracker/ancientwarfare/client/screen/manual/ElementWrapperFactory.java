/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/manual/ElementWrapperFactory.
 */
package io.github.capttracker.ancientwarfare.client.screen.manual;

import io.github.capttracker.ancientwarfare.client.screen.manual.elements.BaseElementWrapper;
import io.github.capttracker.ancientwarfare.client.screen.manual.elements.HeadingElementWrapper;
import io.github.capttracker.ancientwarfare.client.screen.manual.elements.ImageElementWrapper;
import io.github.capttracker.ancientwarfare.client.screen.manual.elements.ItemElementWrapper;
import io.github.capttracker.ancientwarfare.client.screen.manual.elements.TableOfContentsWrapper;
import io.github.capttracker.ancientwarfare.client.screen.manual.elements.TextElementWrapper;
import io.github.capttracker.ancientwarfare.manual.HeadingElement;
import io.github.capttracker.ancientwarfare.manual.IContentElement;
import io.github.capttracker.ancientwarfare.manual.ImageElement;
import io.github.capttracker.ancientwarfare.manual.ItemElement;
import io.github.capttracker.ancientwarfare.manual.TableOfContentsElement;
import io.github.capttracker.ancientwarfare.manual.TextElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the content of a manual category into wrappers and lays them out on pages. A wrapper whose top is 0 starts a new
 * page; headings are kept on the page of the element after them.
 */
public final class ElementWrapperFactory {
    private static final IElementWrapperCreator<TextElement> TEXT = new TextElementWrapper.Creator();
    private static final IElementWrapperCreator<HeadingElement> HEADING = new HeadingElementWrapper.Creator();
    private static final IElementWrapperCreator<TableOfContentsElement> TABLE_OF_CONTENTS = new TableOfContentsWrapper.Creator();
    private static final IElementWrapperCreator<ImageElement> IMAGE = new ImageElementWrapper.Creator();
    private static final IElementWrapperCreator<ItemElement> ITEM = new ItemElementWrapper.Creator();

    private ElementWrapperFactory() {}

    /** The elements already laid out together with the content element they came from (for the keep-with-next logic). */
    private record Kept(IContentElement element, List<BaseElementWrapper<?>> wrappers) {}

    /** 1.12.2 picked the creator from a class-to-creator table; an unknown element makes no wrappers. */
    public static List<BaseElementWrapper<?>> create(GuiManual gui, int topLeftY, int width, int remainingPageHeight, int emptyPageHeight, IContentElement element) {
        if (element instanceof TextElement text) {
            return TEXT.construct(gui, topLeftY, width, remainingPageHeight, emptyPageHeight, text);
        } else if (element instanceof HeadingElement heading) {
            return HEADING.construct(gui, topLeftY, width, remainingPageHeight, emptyPageHeight, heading);
        } else if (element instanceof TableOfContentsElement toc) {
            return TABLE_OF_CONTENTS.construct(gui, topLeftY, width, remainingPageHeight, emptyPageHeight, toc);
        } else if (element instanceof ImageElement image) {
            return IMAGE.construct(gui, topLeftY, width, remainingPageHeight, emptyPageHeight, image);
        } else if (element instanceof ItemElement item) {
            return ITEM.construct(gui, topLeftY, width, remainingPageHeight, emptyPageHeight, item);
        }
        return new ArrayList<>();
    }

    public static List<List<BaseElementWrapper<?>>> getPagedWrappedContent(GuiManual gui, List<IContentElement> elements, int width, int pageHeight) {
        List<List<BaseElementWrapper<?>>> pagedWrappers = new ArrayList<>();
        int currentY = 0;
        List<BaseElementWrapper<?>> currentPageWrappers = addNewPage(pagedWrappers);
        List<Kept> keepTogether = new ArrayList<>();
        for (IContentElement element : elements) {
            List<BaseElementWrapper<?>> pageElements = new ArrayList<>(create(gui, currentY, width, pageHeight - currentY, pageHeight, element));

            if (!pageElements.isEmpty() && pageElements.get(0).shouldKeepWithNext()) {
                keepTogether.add(new Kept(element, pageElements));
                currentY += pageElements.get(0).getHeight();
                continue;
            } else if (!keepTogether.isEmpty()) {
                keepTogether.add(new Kept(element, List.copyOf(pageElements)));
                pageElements = new ArrayList<>();
                currentY = addKeepToCurrentElements(gui, width, pageHeight, keepTogether, pageElements);
                keepTogether.clear();
            }

            for (BaseElementWrapper<?> wrapper : pageElements) {
                if (!currentPageWrappers.isEmpty() && wrapper.getTopLeftY() == 0) {
                    currentPageWrappers = addNewPage(pagedWrappers);
                    currentY = 0;
                }
                currentPageWrappers.add(wrapper);
                currentY += wrapper.getHeight();
            }
        }
        // A heading at the very end of the category (nothing after it) is still shown.
        if (!keepTogether.isEmpty()) {
            for (Kept kept : keepTogether) {
                for (BaseElementWrapper<?> wrapper : kept.wrappers()) {
                    if (!currentPageWrappers.isEmpty() && wrapper.getTopLeftY() == 0) {
                        currentPageWrappers = addNewPage(pagedWrappers);
                    }
                    currentPageWrappers.add(wrapper);
                }
            }
        }
        return pagedWrappers;
    }

    private static int addKeepToCurrentElements(GuiManual gui, int width, int pageHeight, List<Kept> keepTogether, List<BaseElementWrapper<?>> pageElements) {
        int currentY;
        boolean recalculateY = !areOnTheSamePage(keepTogether);
        if (recalculateY) {
            currentY = recalculateYPositions(gui, width, pageHeight, keepTogether, pageElements);
        } else {
            keepTogether.forEach(kept -> pageElements.addAll(kept.wrappers()));
            currentY = pageElements.isEmpty() ? 0 : pageElements.get(0).getTopLeftY(); //reset so that usual logic can again add up to this base value
        }
        return currentY;
    }

    private static int recalculateYPositions(GuiManual gui, int width, int pageHeight, List<Kept> keepTogether, List<BaseElementWrapper<?>> pageElements) {
        int updatedY = 0;
        for (Kept kept : keepTogether) {
            List<BaseElementWrapper<?>> els = create(gui, updatedY, width, pageHeight - updatedY, pageHeight, kept.element());
            if (!els.isEmpty()) {
                updatedY += els.get(els.size() - 1).getHeight();
            }
            pageElements.addAll(els);
        }
        return updatedY;
    }

    private static boolean areOnTheSamePage(List<Kept> keepTogether) {
        int currentY = 0;
        for (Kept kept : keepTogether) {
            List<BaseElementWrapper<?>> wrappers = kept.wrappers();

            if (wrappers.isEmpty()) {
                continue;
            }

            if (wrappers.get(0).getTopLeftY() < currentY) {
                return false;
            }

            if (!wrappers.get(0).shouldKeepWithNext()) {
                return true;
            }

            currentY = wrappers.get(0).getTopLeftY();
        }

        return true;
    }

    private static List<BaseElementWrapper<?>> addNewPage(List<List<BaseElementWrapper<?>>> pagedWrappers) {
        List<BaseElementWrapper<?>> currentPageWrappers = new ArrayList<>();
        pagedWrappers.add(currentPageWrappers);
        return currentPageWrappers;
    }
}
