/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/registry/TargetRegistry.
 */
package io.github.capttracker.ancientwarfarenpc.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfare.registry.IRegistryDataParser;
import net.minecraft.util.GsonHelper;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Named lists of entity type ids ({@code target_lists.json}: {@code hostile}, {@code friendly}) and the parsing of the
 * {@code targets} block ({@code lists}, {@code include}, {@code exclude}) that factions and NPC defaults use to choose what they attack.
 * Ids are 1.21 entity type ids (the data converter renamed the 1.12.2 ones); ids of mods that are not installed simply never match.
 */
public final class TargetRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();

    static final String TARGETS = "targets";
    private static final String LISTS = "lists";
    private static final String INCLUDE = "include";
    private static final String EXCLUDE = "exclude";

    private static volatile Map<String, Set<String>> targetLists = new HashMap<>();

    private TargetRegistry() {}

    public static Optional<Set<String>> parseTargets(JsonObject json) {
        if (!json.has(TARGETS)) {
            return Optional.empty();
        }

        JsonObject targets = GsonHelper.getAsJsonObject(json, TARGETS);
        Set<String> entitiesToTarget = new HashSet<>();
        if (targets.has(LISTS)) {
            for (String list : strings(targets.get(LISTS))) {
                Set<String> entities = targetLists.get(list);
                if (entities != null) {
                    entitiesToTarget.addAll(entities);
                } else {
                    LOGGER.error("Skipping unknown target list - {}", list);
                }
            }
        }
        if (targets.has(INCLUDE)) {
            entitiesToTarget.addAll(strings(targets.get(INCLUDE)));
        }
        if (targets.has(EXCLUDE)) {
            entitiesToTarget.removeAll(strings(targets.get(EXCLUDE)));
        }
        return Optional.of(entitiesToTarget);
    }

    static Set<String> strings(JsonElement element) {
        Set<String> ret = new HashSet<>();
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement e : array) {
                ret.add(GsonHelper.convertToString(e, "entry"));
            }
        } else {
            ret.add(GsonHelper.convertToString(element, "entry"));
        }
        return ret;
    }

    /** Parser of {@code target_lists.json}. Must load before the factions and NPC defaults ({@code load_after}). */
    public static class TargetListParser implements IRegistryDataParser {
        @Override
        public String getName() {
            return "target_lists";
        }

        @Override
        public void beginLoad() {
            targetLists = new HashMap<>();
        }

        @Override
        public void parse(JsonObject json) {
            Map<String, Set<String>> lists = new HashMap<>(targetLists);
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                // "type" and "load_after" style keys are not lists
                if (entry.getValue().isJsonArray()) {
                    lists.put(entry.getKey(), strings(entry.getValue()));
                }
            }
            targetLists = lists;
        }
    }
}
