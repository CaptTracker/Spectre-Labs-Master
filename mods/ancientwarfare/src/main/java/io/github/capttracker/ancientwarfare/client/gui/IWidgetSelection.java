/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/interfaces/IWidgetSelection.
 */
package io.github.capttracker.ancientwarfare.client.gui;

import io.github.capttracker.ancientwarfare.client.gui.elements.GuiElement;

/** Lets a widget (a text box, mostly) tell its screen that it took or gave up keyboard focus. */
public interface IWidgetSelection {

    void onWidgetSelected(GuiElement element);

    void onWidgetDeselected(GuiElement element);

}
