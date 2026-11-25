/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/container/ContainerWorksiteCropFarm.
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.cropfarm.CropFarmBlockEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

/**
 * The crop farm: main, plantable and misc inventories.
 */
public class CropFarmMenu extends WorksiteMenuBase<CropFarmBlockEntity> {

    /** Client side: the block position comes from the buffer written when the server opened the menu. */
    public CropFarmMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, CropFarmBlockEntity.class));
    }

    public CropFarmMenu(int containerId, Inventory inventory, CropFarmBlockEntity worksite) {
        super(AwAutomationMenus.CROP_FARM.get(), containerId, inventory, worksite);
        int layerY = 78;

        frontLabel = layerY;
        layerY += LABEL_GAP;
        layerY = addSlots(worksite.plantableInventory, 8, layerY) + 4;
        bottomLabel = layerY;
        layerY += LABEL_GAP;
        layerY = addSlots(worksite.miscInventory, 8, layerY) + 4;
        playerLabel = layerY;
        layerY += LABEL_GAP;
        addPlayerArea(inventory, layerY);
    }
}
