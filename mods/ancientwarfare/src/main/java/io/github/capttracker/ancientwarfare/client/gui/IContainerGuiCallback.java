/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/interfaces/IContainerGuiCallback.
 */
package io.github.capttracker.ancientwarfare.client.gui;

import net.minecraft.nbt.CompoundTag;

/**
 * What a menu may ask of the screen showing it. {@link GuiContainerBase} implements this and
 * registers itself with its menu (see {@link GuiContainerBase#bindMenu()}).
 */
public interface IContainerGuiCallback {

    /** Re-runs the screen's setup on its next tick. */
    void refreshGui();

    /** Data the server sent straight to the screen (AwMenu.sendDataToScreen). */
    void handlePacketData(CompoundTag data);

}
