/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 3).
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.SmelterBlockEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

/** The smelter: main (output), input and fuel inventories. */
public class SmelterMenu extends WorksiteMenuBase<SmelterBlockEntity> {

    /** Client side: the block position comes from the buffer written when the server opened the menu. */
    public SmelterMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, SmelterBlockEntity.class));
    }

    public SmelterMenu(int containerId, Inventory inventory, SmelterBlockEntity worksite) {
        super(AwAutomationMenus.SMELTER.get(), containerId, inventory, worksite);
        int layerY = 78;

        frontLabel = layerY;
        layerY += LABEL_GAP;
        layerY = addSlots(worksite.inputInventory, 8, layerY) + 4;
        bottomLabel = layerY;
        layerY += LABEL_GAP;
        layerY = addSlots(worksite.fuelInventory, 8, layerY) + 4;
        playerLabel = layerY;
        layerY += LABEL_GAP;
        addPlayerArea(inventory, layerY);
    }
}
