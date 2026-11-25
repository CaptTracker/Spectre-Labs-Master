/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/container/ContainerNpcFactionTradeView.
 */
package io.github.capttracker.ancientwarfarenpc.menu;

import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionTrader;
import io.github.capttracker.ancientwarfarenpc.faction.AwFactions;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcMenus;
import io.github.capttracker.ancientwarfarenpc.trade.MarketApi;
import io.github.capttracker.spectrefactions.api.Relation;
import net.minecraft.server.level.ServerLevel;
import io.github.capttracker.ancientwarfarenpc.trade.FactionTradeList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * The trade view of a faction trader (1.12.2: {@code GUI_NPC_FACTION_TRADE_VIEW}, 42): anyone may buy. The server sends the list with the
 * trades refilled for the current time; the client asks for trade number n and the server does it, changes the standing of the player with the
 * faction ({@code trade} standing change) and sends the list again.
 *
 * <p>Changes from 1.12.2: the client no longer does the trade itself first (the original ran {@code performTrade} on the client copy of the
 * inventory and the list, then asked the server); the answer of the server, the new list and the inventory sync, shows the result. A trade
 * number out of range does nothing.
 *
 * <p>Messages: server to client {@code tradeData}; client to server {@code doTrade} (index).
 */
public class FactionTradeViewMenu extends NpcMenuBase<NpcFactionTrader> {
    private static final String DO_TRADE_TAG = "doTrade";
    private static final String TRADE_DATA_TAG = "tradeData";

    /** The trades: the NPC list on the server, a list filled from the data of the server on the client. */
    public final FactionTradeList tradeList;
    /** Rework phase 5: the price factor of this player's relation to the faction (the client reads it from the server's data), and whether it refuses to trade. */
    public double priceMultiplier = 1.0D;
    public boolean refused = false;

    /** Client side: the entity id of the NPC comes from the buffer written by {@link NpcMenuOpener}. */
    public FactionTradeViewMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, find(inventory.player, buf.readVarInt(), NpcFactionTrader.class));
    }

    public FactionTradeViewMenu(int containerId, Inventory inventory, @Nullable NpcFactionTrader npc) {
        super(AwNpcMenus.FACTION_TRADE_VIEW_MENU.get(), containerId, inventory, npc);
        if (npc != null && !inventory.player.level().isClientSide) {
            tradeList = npc.getTradeList();
            npc.startTrade(inventory.player);
        } else {
            tradeList = new FactionTradeList();
        }
        addPlayerSlots(inventory, 8, 240 - 4 - 8 - 4 * 18, 4);
    }

    /** Anyone may trade. */
    @Override
    protected boolean mayUse(Player who) {
        return true;
    }

    @Override
    protected void sendInitialState() {
        sendTradeData();
    }

    private Relation relationOfPlayer() {
        if (npc == null) {
            return Relation.NEUTRAL;
        }
        return MarketApi.relationOf(player, AwFactions.actorOfFaction(npc.getFaction()));
    }

    private void sendTradeData() {
        tradeList.updateTradesForView();
        CompoundTag tag = new CompoundTag();
        Relation relation = relationOfPlayer();
        tag.putBoolean("refused", MarketApi.refuses(relation));
        tag.putDouble("multiplier", MarketApi.priceMultiplier(relation));
        tag.put(TRADE_DATA_TAG, tradeList.serializeNBT(registries()));
        sendDataToClient(tag);
    }

    @Override
    protected void handlePacketData(CompoundTag tag) {
        if (isClient()) {
            if (tag.contains("multiplier")) {
                priceMultiplier = tag.getDouble("multiplier");
                refused = tag.getBoolean("refused");
            }
            if (tag.contains(TRADE_DATA_TAG, Tag.TAG_COMPOUND)) {
                tradeList.deserializeNBT(registries(), tag.getCompound(TRADE_DATA_TAG));
            }
            refreshGui();
            return;
        }
        if (!isRequestAllowed() || npc == null || !tag.contains(DO_TRADE_TAG, Tag.TAG_INT)) {
            return;
        }
        tradeList.updateTradesForView();
        Relation relation = relationOfPlayer();
        if (!MarketApi.refuses(relation) && tradeList.performTrade(player, tag.getInt(DO_TRADE_TAG), MarketApi.priceMultiplier(relation))
                && player.level() instanceof ServerLevel level) {
            MarketApi.awardTradeReputation(level, player, AwFactions.actorOfFaction(npc.getFaction()));
        }
        sendTradeData();
    }

    /** Client side: asks the server to do trade number {@code tradeNum}. */
    public void doTrade(int tradeNum) {
        sendDataToServer(DO_TRADE_TAG, IntTag.valueOf(tradeNum));
    }

    public Level getWorld() {
        return player.level();
    }

    @Override
    public void removed(Player who) {
        if (!isClient() && npc != null) {
            npc.closeTrade();
        }
        super.removed(who);
    }
}
