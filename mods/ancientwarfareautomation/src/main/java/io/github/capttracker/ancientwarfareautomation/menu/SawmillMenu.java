/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 8).
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.SawmillBlockEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

/** The sawmill: products and the log input. */
public class SawmillMenu extends ProcessingMenu {

    /** Client side: the block position comes from the buffer written when the server opened the menu. */
    public SawmillMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, SawmillBlockEntity.class));
    }

    public SawmillMenu(int containerId, Inventory inventory, SawmillBlockEntity worksite) {
        super(AwAutomationMenus.SAWMILL.get(), containerId, inventory, worksite);
    }
}
