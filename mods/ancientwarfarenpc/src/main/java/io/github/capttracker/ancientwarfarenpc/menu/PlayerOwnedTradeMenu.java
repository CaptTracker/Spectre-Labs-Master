/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/container/ContainerNpcPlayerOwnedTrade.
 */
package io.github.capttracker.ancientwarfarenpc.menu;

import io.github.capttracker.ancientwarfarenpc.entity.NpcTrader;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcMenus;
import io.github.capttracker.ancientwarfarenpc.trade.POTradeList;
import io.github.capttracker.ancientwarfarenpc.trade.POTradeStock;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The shop of a player-owned trader (1.12.2: {@code GUI_NPC_PLAYER_OWNED_TRADE}, 47): the customer buys what the trade order of the trader lists,
 * and the stock is the backpack the trader holds ({@link POTradeStock}). One customer at a time: the NPC is told when the menu opens and
 * closes. The slots of the backpack are part of the menu but off screen and inactive, so that the contents on the server are synchronised to the
 * client, which uses them to show only the trades that are in stock (the original put them at x = 100000 for the same purpose).
 *
 * <p>Changes from 1.12.2: as in {@link FactionTradeViewMenu} the client no longer does the trade itself before asking; the screen updates when the
 * inventory sync arrives. A trade number out of range does nothing.
 *
 * <p>Messages: server to client {@code tradeData}; client to server {@code doTrade} (index).
 */
public class PlayerOwnedTradeMenu extends NpcMenuBase<NpcTrader> {
    /** The trades: the list of the NPC on the server, a list filled from the data of the server on the client. */
    @Nullable
    public POTradeList tradeList;
    /** The backpack the trader holds, null if it holds none. */
    @Nullable
    public final IItemHandler storage;
    private final int firstStorageSlot;

    /** Client side: the entity id of the NPC comes from the buffer written by {@link NpcMenuOpener}. */
    public PlayerOwnedTradeMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, find(inventory.player, buf.readVarInt(), NpcTrader.class));
    }

    public PlayerOwnedTradeMenu(int containerId, Inventory inventory, @Nullable NpcTrader npc) {
        super(AwNpcMenus.PLAYER_OWNED_TRADE_MENU.get(), containerId, inventory, npc);
        boolean server = !inventory.player.level().isClientSide;
        if (npc != null && server) {
            tradeList = npc.getTradeList();
            npc.startTrade(inventory.player);
        } else {
            tradeList = new POTradeList();
        }
        addPlayerSlots(inventory, 8, 240 - 4 - 8 - 4 * 18, 4);
        firstStorageSlot = slots.size();
        storage = npc == null ? null : POTradeStock.getStorage(npc).orElse(null);
        if (storage != null) {
            // The backpack slots are in the menu so that they are synchronised to the client; the client checks trades against them.
            for (int i = 0; i < storage.getSlots(); i++) {
                addSlot(new HiddenSlot(storage, i));
            }
        }
    }

    /** Anyone may trade. */
    @Override
    protected boolean mayUse(Player who) {
        return true;
    }

    @Override
    protected void sendInitialState() {
        if (tradeList != null) {
            CompoundTag tag = new CompoundTag();
            tag.put("tradeData", tradeList.serializeNBT(registries()));
            sendDataToClient(tag);
        }
    }

    @Override
    protected void handlePacketData(CompoundTag tag) {
        if (isClient()) {
            if (tag.contains("tradeData", Tag.TAG_COMPOUND)) {
                tradeList = new POTradeList();
                tradeList.deserializeNBT(registries(), tag.getCompound("tradeData"));
            }
            refreshGui();
            return;
        }
        if (!isRequestAllowed() || tradeList == null || storage == null || !tag.contains("doTrade", Tag.TAG_INT)) {
            return;
        }
        tradeList.performTrade(player, storage, tag.getInt("doTrade"));
    }

    /** Client side: asks the server to do trade number {@code tradeIndex}. */
    public void doTrade(int tradeIndex) {
        sendDataToServer("doTrade", IntTag.valueOf(tradeIndex));
    }

    // The screen shows what is in stock, so it is rebuilt when the stock changes.

    @Override
    public void setItem(int slotId, int stateId, ItemStack stack) {
        super.setItem(slotId, stateId, stack);
        if (slotId >= firstStorageSlot) {
            refreshGui();
        }
    }

    @Override
    public void initializeContents(int stateId, List<ItemStack> items, ItemStack carried) {
        super.initializeContents(stateId, items, carried);
        refreshGui();
    }

    @Override
    public void removed(Player who) {
        if (!isClient() && npc != null) {
            npc.closeTrade();
        }
        super.removed(who);
    }

    /** A backpack slot that is never drawn or clicked, only synchronised. */
    private static final class HiddenSlot extends SlotItemHandler {
        private HiddenSlot(IItemHandler handler, int index) {
            super(handler, index, 100000, 100000);
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
