/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 8).
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.ProcessingWorksiteBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

/** A production site (sawmill, bakery): main (products), input and, if the site has one, fuel inventories. */
public abstract class ProcessingMenu extends WorksiteMenuBase<ProcessingWorksiteBlockEntity> {
    /** The y positions of the captions of the input and fuel slots (0 = none). */
    public int inputLabel;
    public int fuelLabel;

    protected ProcessingMenu(MenuType<?> type, int containerId, Inventory inventory, ProcessingWorksiteBlockEntity worksite) {
        super(type, containerId, inventory, worksite);
        int layerY = 78;

        inputLabel = layerY;
        layerY += LABEL_GAP;
        layerY = addSlots(worksite.inputInventory, 8, layerY) + 4;
        if (worksite.fuelInventory != null) {
            fuelLabel = layerY;
            layerY += LABEL_GAP;
            layerY = addSlots(worksite.fuelInventory, 8, layerY) + 4;
        }
        playerLabel = layerY;
        layerY += LABEL_GAP;
        addPlayerArea(inventory, layerY);
    }
}
