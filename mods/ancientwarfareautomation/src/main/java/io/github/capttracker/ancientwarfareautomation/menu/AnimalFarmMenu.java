/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/container/ContainerWorksiteAnimalFarm.
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.AnimalFarmBlockEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

/**
 * The animal farm: main, food and tool inventories. Its "Other" button opens {@link AnimalControlMenu}.
 */
public class AnimalFarmMenu extends WorksiteMenuBase<AnimalFarmBlockEntity> {

    /** Client side: the block position comes from the buffer written when the server opened the menu. */
    public AnimalFarmMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, AnimalFarmBlockEntity.class));
    }

    public AnimalFarmMenu(int containerId, Inventory inventory, AnimalFarmBlockEntity worksite) {
        super(AwAutomationMenus.ANIMAL_FARM.get(), containerId, inventory, worksite);
        int layerY = 78;

        frontLabel = layerY;
        layerY += LABEL_GAP;
        layerY = addSlots(worksite.foodInventory, 8, layerY) + 4;
        bottomLabel = layerY;
        layerY += LABEL_GAP;
        layerY = addSlots(worksite.toolInventory, 8, layerY) + 4;
        playerLabel = layerY;
        layerY += LABEL_GAP;
        addPlayerArea(inventory, layerY);
    }
}
