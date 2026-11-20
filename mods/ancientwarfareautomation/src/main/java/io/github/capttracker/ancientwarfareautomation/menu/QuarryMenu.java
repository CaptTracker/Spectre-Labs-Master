/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/container/ContainerWorksiteQuarry.
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.QuarryBlockEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;

/**
 * The quarry: only its main inventory. Its "Height" button opens {@link QuarryBoundsMenu}.
 */
public class QuarryMenu extends WorksiteMenuBase<QuarryBlockEntity> {

    /** Client side: the block position comes from the buffer written when the server opened the menu. */
    public QuarryMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, QuarryBlockEntity.class));
    }

    public QuarryMenu(int containerId, Inventory inventory, QuarryBlockEntity worksite) {
        super(AwAutomationMenus.QUARRY.get(), containerId, inventory, worksite);
        int layerY = 78;

        playerLabel = layerY;
        layerY += LABEL_GAP;
        addPlayerArea(inventory, layerY);
    }

    public static final String OPEN_QUARRY_BOUNDS = "quarryBounds";

    public final void requestOpenQuarryBounds() {
        sendDataToServer(OPEN_KEY, StringTag.valueOf(OPEN_QUARRY_BOUNDS));
    }

    @Override
    protected void handleOpenRequest(ServerPlayer serverPlayer, String what) {
        if (OPEN_QUARRY_BOUNDS.equals(what)) {
            AwAutomationMenus.openQuarryBounds(serverPlayer, blockEntity);
        } else {
            super.handleOpenRequest(serverPlayer, what);
        }
    }
}
