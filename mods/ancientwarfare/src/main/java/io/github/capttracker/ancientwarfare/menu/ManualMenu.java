/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/container/ContainerManual.
 */
package io.github.capttracker.ancientwarfare.menu;

import net.minecraft.world.entity.player.Inventory;

/**
 * The manual's menu: no slots and no server counterpart (the manual item opens the screen on the client, see
 * {@code ManualItem.setClientOpener}). Only there because every Ancient Warfare screen is built on a menu.
 */
public class ManualMenu extends ScreenOnlyMenu {
    public ManualMenu(Inventory inventory) {
        super(inventory);
    }
}
