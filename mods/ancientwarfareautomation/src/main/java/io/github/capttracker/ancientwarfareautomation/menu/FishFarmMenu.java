/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/container/ContainerWorksiteFishFarm.
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.FishFarmBlockEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

/**
 * The fish farm: only its main inventory. Its "Other" button opens {@link FishControlMenu}.
 */
public class FishFarmMenu extends WorksiteMenuBase<FishFarmBlockEntity> {

    /** Client side: the block position comes from the buffer written when the server opened the menu. */
    public FishFarmMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, FishFarmBlockEntity.class));
    }

    public FishFarmMenu(int containerId, Inventory inventory, FishFarmBlockEntity worksite) {
        super(AwAutomationMenus.FISH_FARM.get(), containerId, inventory, worksite);
        int layerY = 78;

        playerLabel = layerY;
        layerY += LABEL_GAP;
        addPlayerArea(inventory, layerY);
    }
}
