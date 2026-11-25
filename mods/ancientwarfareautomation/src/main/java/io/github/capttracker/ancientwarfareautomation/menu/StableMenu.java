/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5).
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.StableBlockEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

/** The stable: the feed store (feed for the horses, donkeys and mules) and the player inventory. */
public class StableMenu extends WorksiteMenuBase<StableBlockEntity> {

    /** Client side: the block position comes from the buffer written when the server opened the menu. */
    public StableMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, StableBlockEntity.class));
    }

    public StableMenu(int containerId, Inventory inventory, StableBlockEntity worksite) {
        super(AwAutomationMenus.STABLE.get(), containerId, inventory, worksite);
        int layerY = 78;
        frontLabel = layerY;
        layerY += LABEL_GAP;
        playerLabel = layerY;
        layerY += LABEL_GAP;
        addPlayerArea(inventory, layerY);
    }
}
