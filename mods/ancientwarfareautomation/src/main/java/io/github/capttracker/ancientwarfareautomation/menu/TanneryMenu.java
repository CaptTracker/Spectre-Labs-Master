/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 8).
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.TanneryBlockEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

/** The Tannery: products and the input. */
public class TanneryMenu extends ProcessingMenu {

    /** Client side: the block position comes from the buffer written when the server opened the menu. */
    public TanneryMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, TanneryBlockEntity.class));
    }

    public TanneryMenu(int containerId, Inventory inventory, TanneryBlockEntity worksite) {
        super(AwAutomationMenus.TANNERY.get(), containerId, inventory, worksite);
    }
}
