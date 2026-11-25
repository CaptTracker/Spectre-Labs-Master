/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/container/ContainerWorksiteTreeFarm.
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.treefarm.TreeFarmBlockEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

/**
 * The tree farm: main, plantable (saplings) and misc (bone meal, shears) inventories.
 */
public class TreeFarmMenu extends WorksiteMenuBase<TreeFarmBlockEntity> {

    /** Client side: the block position comes from the buffer written when the server opened the menu. */
    public TreeFarmMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, TreeFarmBlockEntity.class));
    }

    public TreeFarmMenu(int containerId, Inventory inventory, TreeFarmBlockEntity worksite) {
        super(AwAutomationMenus.TREE_FARM.get(), containerId, inventory, worksite);
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
