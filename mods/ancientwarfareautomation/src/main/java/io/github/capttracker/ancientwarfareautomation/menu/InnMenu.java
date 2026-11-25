/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5).
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.InnBlockEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

/** The inn: the pantry (food and drink for the guests, and the coins the inn earns) and the player inventory. */
public class InnMenu extends WorksiteMenuBase<InnBlockEntity> {

    /** Client side: the block position comes from the buffer written when the server opened the menu. */
    public InnMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, InnBlockEntity.class));
    }

    public InnMenu(int containerId, Inventory inventory, InnBlockEntity worksite) {
        super(AwAutomationMenus.INN.get(), containerId, inventory, worksite);
        int layerY = 78;
        frontLabel = layerY;
        layerY += LABEL_GAP;
        playerLabel = layerY;
        layerY += LABEL_GAP;
        addPlayerArea(inventory, layerY);
    }
}
