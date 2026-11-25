/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/container/ContainerManual (and the slot hiding of ContainerBase.removeSlots).
 */
package io.github.capttracker.ancientwarfare.menu;

import net.minecraft.world.entity.player.Inventory;

/**
 * A menu that exists only on the client, with no slots, for screens that are not backed by a server menu: the manual,
 * and the sub-screens (research queue picker) that cover another screen's menu.
 *
 * <p>The original hid the covered menu's slots by moving them 10000 pixels away ({@code removeSlots}). Slot positions
 * are final now, so a sub-screen gets one of these instead and reads the covered menu's data through a reference.
 * It is never the player's open container, so closing it must not send a close packet; the screens using it
 * override their close handling.
 */
public class ScreenOnlyMenu extends AwMenu {
    public ScreenOnlyMenu(Inventory inventory) {
        super(null, -1, inventory);
    }
}
