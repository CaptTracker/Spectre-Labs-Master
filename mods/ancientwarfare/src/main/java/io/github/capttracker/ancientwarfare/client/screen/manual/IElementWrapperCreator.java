/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/manual/IElementWrapperCreator.
 */
package io.github.capttracker.ancientwarfare.client.screen.manual;

import io.github.capttracker.ancientwarfare.client.screen.manual.elements.BaseElementWrapper;
import io.github.capttracker.ancientwarfare.manual.IContentElement;

import java.util.List;

/** Turns one content element into the wrappers that show it, splitting it across pages where needed. */
public interface IElementWrapperCreator<T extends IContentElement> {
    /**
     * @param topLeftY            where the element would start on the current page
     * @param remainingPageHeight the room left on the current page below {@code topLeftY}
     * @param emptyPageHeight     the height of a whole page. A wrapper whose {@code topLeftY} is 0 starts a new page
     */
    List<BaseElementWrapper<?>> construct(GuiManual gui, int topLeftY, int width, int remainingPageHeight, int emptyPageHeight, T element);
}
