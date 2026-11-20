/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5).
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.MarketStallBlockEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

/** The market stall: main inventory (earnings and goods bought), the stock for the sell offers and the till for the buy orders. */
public class MarketStallMenu extends WorksiteMenuBase<MarketStallBlockEntity> {

    /** Client side: the block position comes from the buffer written when the server opened the menu. */
    public MarketStallMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, MarketStallBlockEntity.class));
    }

    public MarketStallMenu(int containerId, Inventory inventory, MarketStallBlockEntity worksite) {
        super(AwAutomationMenus.MARKET_STALL.get(), containerId, inventory, worksite);
        int layerY = 78;

        frontLabel = layerY;
        layerY += LABEL_GAP;
        layerY = addSlots(worksite.stockInventory, 8, layerY) + 4;
        bottomLabel = layerY;
        layerY += LABEL_GAP;
        layerY = addSlots(worksite.tillInventory, 8, layerY) + 4;
        playerLabel = layerY;
        layerY += LABEL_GAP;
        addPlayerArea(inventory, layerY);
    }
}
