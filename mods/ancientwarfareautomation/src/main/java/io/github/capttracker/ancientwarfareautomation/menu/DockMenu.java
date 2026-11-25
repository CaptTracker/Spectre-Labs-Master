/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): the fishing dock's inventory.
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.DockBlockEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

/** The dock: its main inventory, which takes the catch and the spare fishing boat items. */
public class DockMenu extends WorksiteMenuBase<DockBlockEntity> {

    /** Client side: the block position comes from the buffer written when the server opened the menu. */
    public DockMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, DockBlockEntity.class));
    }

    public DockMenu(int containerId, Inventory inventory, DockBlockEntity worksite) {
        super(AwAutomationMenus.DOCK.get(), containerId, inventory, worksite);
        int layerY = 78;

        playerLabel = layerY;
        layerY += LABEL_GAP;
        addPlayerArea(inventory, layerY);
    }
}
