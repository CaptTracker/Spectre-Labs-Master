/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5).
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfare.api.IMarketStall;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.MarketEntry;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.MarketStallBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.WorksiteMenus;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * The offers screen of a market stall: no slots. The server sends the sell offers and buy orders ({@code sell}, {@code buy}: lists of
 * {@link MarketEntry} compounds) on open; the client sends them back changed when the screen closes, with {@code return} to get the stall's main screen.
 * The server sanitises every entry ({@link MarketEntry#sanitize()}) and cuts the lists to their maximum.
 */
public class MarketOffersMenu extends AutomationMenu<MarketStallBlockEntity> {
    private static final String SELL = "sell";
    private static final String BUY = "buy";
    private static final String RETURN = "return";

    /** The entries as the client screen edits them (a copy on both sides). */
    public final List<MarketEntry> sell = new ArrayList<>();
    public final List<MarketEntry> buy = new ArrayList<>();

    /** Client side. */
    public MarketOffersMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, MarketStallBlockEntity.class));
    }

    public MarketOffersMenu(int containerId, Inventory inventory, MarketStallBlockEntity stall) {
        super(AwAutomationMenus.MARKET_OFFERS.get(), containerId, inventory, stall);
        pad();
    }

    private void pad() {
        while (sell.size() < IMarketStall.MAX_SELL_OFFERS) {
            sell.add(new MarketEntry());
        }
        while (buy.size() < IMarketStall.MAX_BUY_ORDERS) {
            buy.add(new MarketEntry());
        }
    }

    private CompoundTag stateTag(List<MarketEntry> sellList, List<MarketEntry> buyList) {
        CompoundTag tag = new CompoundTag();
        tag.put(SELL, MarketEntry.saveList(sellList, registries()));
        tag.put(BUY, MarketEntry.saveList(buyList, registries()));
        return tag;
    }

    @Override
    protected void sendInitialState() {
        sendDataToClient(stateTag(blockEntity.getSellEntries(), blockEntity.getBuyEntries()));
    }

    @Override
    protected void handlePacketData(CompoundTag tag) {
        if (!tag.contains(SELL, Tag.TAG_LIST) || !tag.contains(BUY, Tag.TAG_LIST)) {
            if (isClient()) {
                refreshGui();
            }
            return;
        }
        List<MarketEntry> newSell = MarketEntry.loadList(tag.getList(SELL, Tag.TAG_COMPOUND), registries(), IMarketStall.MAX_SELL_OFFERS);
        List<MarketEntry> newBuy = MarketEntry.loadList(tag.getList(BUY, Tag.TAG_COMPOUND), registries(), IMarketStall.MAX_BUY_ORDERS);
        if (isClient()) {
            sell.clear();
            buy.clear();
            sell.addAll(newSell);
            buy.addAll(newBuy);
            pad();
            refreshGui();
            return;
        }
        if (!isRequestAllowed()) {
            return;
        }
        blockEntity.setOffers(newSell, newBuy);
        if (tag.getBoolean(RETURN) && player instanceof ServerPlayer serverPlayer) {
            WorksiteMenus.open(serverPlayer, WorksiteMenus.Kind.MARKET_STALL, blockEntity);
        }
    }

    /** Client: sends the edited entries; asks for the stall's main screen when {@code returnToStall}. */
    public void sendOffersToServer(boolean returnToStall) {
        CompoundTag tag = stateTag(sell, buy);
        if (returnToStall) {
            tag.putBoolean(RETURN, true);
        }
        sendDataToServer(tag);
    }
}
