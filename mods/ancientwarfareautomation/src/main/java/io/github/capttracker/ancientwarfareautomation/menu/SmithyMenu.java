/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 8).
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.SmithyBlockEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

/** The Smithy: products and the input. */
public class SmithyMenu extends ProcessingMenu {

    /** Client side: the block position comes from the buffer written when the server opened the menu. */
    public SmithyMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, SmithyBlockEntity.class));
    }

    public SmithyMenu(int containerId, Inventory inventory, SmithyBlockEntity worksite) {
        super(AwAutomationMenus.SMITHY.get(), containerId, inventory, worksite);
    }
}
