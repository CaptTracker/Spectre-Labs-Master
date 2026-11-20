/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/interfaces/IScrollableCallback.
 */
package io.github.capttracker.ancientwarfare.client.gui;

/**
 * Screens and composite widgets implement this and register with a scrollbar to be told when the
 * scrolled view changes.
 */
public interface IScrollableCallback {

    void onScrolled(int newTop);

}
