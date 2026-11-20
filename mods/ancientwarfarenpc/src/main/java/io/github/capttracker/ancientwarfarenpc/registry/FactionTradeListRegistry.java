/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/registry/FactionTradeListRegistry.
 */
package io.github.capttracker.ancientwarfarenpc.registry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfare.config.AWCoreStatics;
import io.github.capttracker.ancientwarfare.registry.IRegistryDataParser;
import net.minecraft.util.GsonHelper;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The trade list templates of {@code faction_trade_lists.json}: {@code defaults} (usable by every faction trader) and
 * {@code faction_defaults} (extra lists of one faction; only nogg and buffloka have one).
 *
 * <p>1.12.2 saved lists from the trade setup screen by rewriting the file in the config folder, which then replaced the bundled copy.
 * The same happens here: {@link #saveTradeList} writes {@code config/ancientwarfare/registry/npc/faction_trade_lists.json}
 * (Core's {@code AWCoreStatics.registryOverridePath()}) with every list, and {@link FactionTradeListParser} reads that file instead
 * of the data pack file when it exists.
 */
public final class FactionTradeListRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String OVERRIDE_FILE = "npc/faction_trade_lists.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile Map<String, FactionTradeListTemplate> defaultTemplates = new LinkedHashMap<>();
    private static volatile Map<String, Map<String, FactionTradeListTemplate>> factionTemplates = new HashMap<>();

    private FactionTradeListRegistry() {}

    public static Map<String, FactionTradeListTemplate> getFactionDefaults(String factionName) {
        return factionTemplates.getOrDefault(factionName, new HashMap<>());
    }

    public static Map<String, FactionTradeListTemplate> getDefaults() {
        return defaultTemplates;
    }

    public static void saveTradeList(FactionTradeListTemplate list) {
        Map<String, FactionTradeListTemplate> updated = new LinkedHashMap<>(defaultTemplates);
        updated.put(list.getName(), list);
        defaultTemplates = updated;
        saveTradeLists();
    }

    public static void saveFactionTradeList(FactionTradeListTemplate list, String faction) {
        Map<String, Map<String, FactionTradeListTemplate>> updated = new HashMap<>(factionTemplates);
        Map<String, FactionTradeListTemplate> forFaction = new LinkedHashMap<>(updated.getOrDefault(faction, new HashMap<>()));
        forFaction.put(list.getName(), list);
        updated.put(faction, forFaction);
        factionTemplates = updated;
        saveTradeLists();
    }

    private static Path overrideFile() {
        return AWCoreStatics.registryOverridePath().resolve(OVERRIDE_FILE);
    }

    private static void saveTradeLists() {
        JsonObject parent = new JsonObject();
        parent.add("defaults", serializeTradeLists(defaultTemplates.values()));
        JsonObject factionTradeLists = new JsonObject();
        for (Map.Entry<String, Map<String, FactionTradeListTemplate>> entry : factionTemplates.entrySet()) {
            factionTradeLists.add(entry.getKey(), serializeTradeLists(entry.getValue().values()));
        }
        parent.add("faction_defaults", factionTradeLists);

        Path file = overrideFile();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(parent), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Couldn't save trade lists to {}", file, e);
        }
    }

    private static JsonArray serializeTradeLists(Collection<FactionTradeListTemplate> templateLists) {
        JsonArray tradeLists = new JsonArray();
        for (FactionTradeListTemplate templateList : templateLists) {
            JsonObject tradeList = new JsonObject();
            tradeList.addProperty("name", templateList.getName());
            JsonArray trades = new JsonArray();
            for (FactionTradeTemplate template : templateList.getTrades()) {
                JsonObject trade = new JsonObject();
                trade.add("input", toArray(template.getInputJson()));
                trade.add("output", toArray(template.getOutputJson()));
                trade.addProperty("refill_frequency", template.getRefillFrequency());
                trade.addProperty("max_trades", template.getMaxTrades());
                trades.add(trade);
            }
            tradeList.add("trades", trades);
            tradeLists.add(tradeList);
        }
        return tradeLists;
    }

    private static JsonArray toArray(List<JsonElement> stacks) {
        JsonArray array = new JsonArray();
        stacks.forEach(array::add);
        return array;
    }

    /** Parser of {@code faction_trade_lists.json}. */
    public static class FactionTradeListParser implements IRegistryDataParser {
        @Override
        public String getName() {
            return "faction_trade_lists";
        }

        @Override
        public void beginLoad() {
            defaultTemplates = new LinkedHashMap<>();
            factionTemplates = new HashMap<>();
        }

        @Override
        public void parse(JsonObject json) {
            JsonObject source = json;
            Path override = overrideFile();
            if (Files.isRegularFile(override)) {
                try (Reader reader = Files.newBufferedReader(override, StandardCharsets.UTF_8)) {
                    source = GsonHelper.parse(reader);
                    LOGGER.info("Using the saved trade lists of {}", override);
                } catch (IOException | RuntimeException e) {
                    LOGGER.error("Couldn't read {}, using the bundled trade lists", override, e);
                }
            }
            defaultTemplates = parseTradeLists(GsonHelper.getAsJsonArray(source, "defaults"));
            Map<String, Map<String, FactionTradeListTemplate>> factions = new HashMap<>();
            if (source.has("faction_defaults")) {
                for (Map.Entry<String, JsonElement> e : GsonHelper.getAsJsonObject(source, "faction_defaults").entrySet()) {
                    factions.put(e.getKey(), parseTradeLists(GsonHelper.convertToJsonArray(e.getValue(), "trade_list")));
                }
            }
            factionTemplates = factions;
        }

        private Map<String, FactionTradeListTemplate> parseTradeLists(JsonArray tradeLists) {
            Map<String, FactionTradeListTemplate> ret = new LinkedHashMap<>();
            for (JsonElement tradeListElement : tradeLists) {
                JsonObject tradeList = GsonHelper.convertToJsonObject(tradeListElement, "trade_list");
                String name = GsonHelper.getAsString(tradeList, "name");
                ret.put(name, new FactionTradeListTemplate(name, parseTrades(GsonHelper.getAsJsonArray(tradeList, "trades"))));
            }
            return ret;
        }

        private List<FactionTradeTemplate> parseTrades(JsonArray trades) {
            List<FactionTradeTemplate> ret = new ArrayList<>();
            for (JsonElement tradeElement : trades) {
                JsonObject trade = GsonHelper.convertToJsonObject(tradeElement, "trade");
                ret.add(new FactionTradeTemplate(stacks(GsonHelper.getAsJsonArray(trade, "input")), stacks(GsonHelper.getAsJsonArray(trade, "output")),
                        GsonHelper.getAsInt(trade, "refill_frequency"), GsonHelper.getAsInt(trade, "max_trades")));
            }
            return ret;
        }

        private List<JsonElement> stacks(JsonArray array) {
            List<JsonElement> ret = new ArrayList<>(array.size());
            array.forEach(ret::add);
            return ret;
        }
    }
}
