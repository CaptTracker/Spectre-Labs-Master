/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/container/ContainerNpcFactionTradeSetup.
 */
package io.github.capttracker.ancientwarfarenpc.menu;

import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionTrader;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcMenus;
import io.github.capttracker.ancientwarfarenpc.registry.FactionTradeListRegistry;
import io.github.capttracker.ancientwarfarenpc.registry.FactionTradeListTemplate;
import io.github.capttracker.ancientwarfarenpc.trade.FactionTrade;
import io.github.capttracker.ancientwarfarenpc.trade.FactionTradeConversions;
import io.github.capttracker.ancientwarfarenpc.trade.FactionTradeList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The trade setup of a faction trader (1.12.2: {@code GUI_NPC_FACTION_TRADE_SETUP}, 39), opened from the trade view by a creative player:
 * the trade list is edited in the screen and sent to the server when the screen closes. It can load a trade list template and save the
 * current list as a template.
 *
 * <p>Changes from 1.12.2: creative players only (the original trusted the open request and the trade list the client sent); the templates are
 * server data (data packs), so the server sends the template names with the first state, applies the chosen template itself and saves a
 * template itself (the original read and wrote the client's copy of the registry); a template of the faction wins over a default one of the same
 * name (the original screen's two code paths disagreed); the client edits its own copy of the list, not the NPC's; the server takes at most
 * {@value #MAX_TRADES} trades.
 *
 * <p>Messages: server to client {@code tradeData} (the list), {@code templates} (list of names); client to server {@code tradeData},
 * {@code applyTemplate} (name), {@code saveTemplate} ({@code name}, {@code factionSpecific}).
 */
public class FactionTradeSetupMenu extends NpcMenuBase<NpcFactionTrader> {
    public static final int MAX_TRADES = 256;

    /** The list being edited: the NPC's own list on the server, a copy filled from the server's data on the client. */
    public final FactionTradeList tradeList;
    public boolean tradesChanged = false;
    private final List<String> templateNames = new ArrayList<>();

    /** Client side: the NPC's entity id comes from the buffer written by {@link NpcMenuOpener}. */
    public FactionTradeSetupMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, find(inventory.player, buf.readVarInt(), NpcFactionTrader.class));
    }

    public FactionTradeSetupMenu(int containerId, Inventory inventory, @Nullable NpcFactionTrader npc) {
        super(AwNpcMenus.FACTION_TRADE_SETUP_MENU.get(), containerId, inventory, npc);
        if (npc != null && !inventory.player.level().isClientSide) {
            tradeList = npc.getTradeList();
            npc.startTrade(inventory.player);
        } else {
            tradeList = new FactionTradeList();
        }
        addPlayerSlots(inventory, 8, 240 - 4 - 8 - 4 * 18, 4);
    }

    @Override
    protected boolean mayUse(Player who) {
        return who.isCreative();
    }

    /** The names of the templates that can be loaded (default ones and those of the NPC's faction), sorted. */
    public List<String> getTemplateNames() {
        return templateNames;
    }

    // ---- sync ----

    @Override
    protected void sendInitialState() {
        if (npc == null) {
            return;
        }
        tradeList.updateTradesForView();
        CompoundTag tag = new CompoundTag();
        tag.put("tradeData", tradeList.serializeNBT(registries()));
        ListTag names = new ListTag();
        for (String name : templatesOf(npc.getFaction()).keySet()) {
            names.add(StringTag.valueOf(name));
        }
        tag.put("templates", names);
        sendDataToClient(tag);
    }

    /** The default templates with the templates of the faction over them, sorted by name. */
    private static Map<String, FactionTradeListTemplate> templatesOf(String faction) {
        Map<String, FactionTradeListTemplate> merged = new TreeMap<>(FactionTradeListRegistry.getDefaults());
        merged.putAll(FactionTradeListRegistry.getFactionDefaults(faction));
        return merged;
    }

    @Override
    protected void handlePacketData(CompoundTag tag) {
        if (isClient()) {
            if (tag.contains("tradeData", Tag.TAG_COMPOUND)) {
                tradeList.deserializeNBT(registries(), tag.getCompound("tradeData"));
            }
            if (tag.contains("templates", Tag.TAG_LIST)) {
                templateNames.clear();
                ListTag names = tag.getList("templates", Tag.TAG_STRING);
                for (int i = 0; i < names.size(); i++) {
                    templateNames.add(names.getString(i));
                }
            }
            refreshGui();
            return;
        }
        if (!isRequestAllowed() || npc == null) {
            return;
        }
        if (tag.contains("tradeData", Tag.TAG_COMPOUND)) {
            CompoundTag data = tag.getCompound("tradeData");
            if (data.getList("tradeList", Tag.TAG_COMPOUND).size() <= MAX_TRADES) {
                tradeList.deserializeNBT(registries(), data);
            }
        } else if (tag.contains("applyTemplate", Tag.TAG_STRING)) {
            applyTemplate(tag.getString("applyTemplate"));
        } else if (tag.contains("saveTemplate", Tag.TAG_COMPOUND)) {
            CompoundTag save = tag.getCompound("saveTemplate");
            saveTemplate(sanitizeName(save.getString("name")).trim(), save.getBoolean("factionSpecific"));
        }
    }

    private void applyTemplate(String templateName) {
        if (npc == null) {
            return;
        }
        FactionTradeListTemplate template = templatesOf(npc.getFaction()).getOrDefault(templateName, FactionTradeListTemplate.EMPTY);
        FactionTradeList fromTemplate = FactionTradeConversions.toTradeList(template, registries());
        tradeList.clear();
        for (FactionTrade trade : fromTemplate) {
            tradeList.add(trade);
        }
        CompoundTag tag = new CompoundTag();
        tag.put("tradeData", tradeList.serializeNBT(registries()));
        sendDataToClient(tag);
    }

    private void saveTemplate(String templateName, boolean factionSpecific) {
        if (npc == null || templateName.isEmpty()) {
            return;
        }
        FactionTradeListTemplate template = FactionTradeConversions.fromTradeList(templateName, tradeList, registries());
        if (factionSpecific) {
            FactionTradeListRegistry.saveFactionTradeList(template, npc.getFaction());
        } else {
            FactionTradeListRegistry.saveTradeList(template);
        }
    }

    // ---- client requests ----

    /** Client side, when the screen closes: sends the list if it was changed. */
    public void onGuiClosed() {
        if (isClient() && tradesChanged) {
            tradeList.removeEmptyTrades();
            sendTradeListToServer();
        }
    }

    private void sendTradeListToServer() {
        CompoundTag tag = new CompoundTag();
        tag.put("tradeData", tradeList.serializeNBT(registries()));
        sendDataToServer(tag);
    }

    /** Client side: asks the server to replace the list with the template. The server sends the new list back. */
    public void applyTradeTemplate(String templateName) {
        sendDataToServer("applyTemplate", StringTag.valueOf(templateName));
        tradesChanged = false;
    }

    /** Client side: saves the list as it is now as a template (the list is sent first, so the server saves what the screen shows). */
    public void saveTradeTemplate(String templateName, boolean factionSpecific) {
        sendTradeListToServer();
        CompoundTag save = new CompoundTag();
        save.putString("name", templateName);
        save.putBoolean("factionSpecific", factionSpecific);
        sendDataToServer("saveTemplate", save);
    }

    @Override
    public void removed(Player who) {
        if (!isClient() && npc != null) {
            npc.closeTrade();
        }
        super.removed(who);
    }
}
