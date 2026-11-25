/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/registry/FactionRegistry.
 */
package io.github.capttracker.ancientwarfarenpc.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import io.github.capttracker.ancientwarfare.registry.IRegistryDataParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.GsonHelper;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The factions of {@code factions.json} (44 in the bundled data), keyed by name. Loaded by {@link FactionParser} (parser name
 * {@code factions}); a faction starts as a copy of the file's {@code defaults} and applies its own overrides.
 */
public final class FactionRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile Map<String, FactionDefinition> factions = new HashMap<>();

    private static final FactionDefinition EMPTY_FACTION =
            new FactionDefinition(new FactionDefinition.StandingSettings(0, false, new HashMap<>()), new HashSet<>(), new HashSet<>()).copy("", -1).build();

    private FactionRegistry() {}

    public static Set<String> getFactionNames() {
        return factions.keySet();
    }

    /** The faction of that name, or an empty faction (no hostility, standing fixed at 0) if there is none. */
    public static FactionDefinition getFaction(String name) {
        return factions.getOrDefault(name, EMPTY_FACTION);
    }

    public static Collection<FactionDefinition> getFactionDefinitions() {
        return factions.values();
    }

    public static class FactionParser implements IRegistryDataParser {
        private static final String PLAYER_DEFAULT_STANDING = "player_default_standing";
        private static final String STANDING_CAN_CHANGE = "standing_can_change";
        private static final String HOSTILE_TOWARDS_FACTIONS = "hostile_towards_factions";
        private static final String THEMED_BLOCKS = "themed_blocks";
        private static final String STANDING_CHANGES = "standing_changes";
        private static final String STANDING_SETTINGS = "standing_settings";

        @Override
        public String getName() {
            return "factions";
        }

        @Override
        public void beginLoad() {
            factions = new HashMap<>();
        }

        @Override
        public void parse(JsonObject json) {
            Map<String, FactionDefinition> parsed = new HashMap<>(factions);

            JsonObject defaults = GsonHelper.getAsJsonObject(json, "defaults");
            FactionDefinition defaultDefinition = new FactionDefinition(
                    parseStandingSettings(defaults),
                    parseHostileTowards(defaults).entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).collect(Collectors.toCollection(HashSet::new)),
                    TargetRegistry.parseTargets(defaults).orElse(new HashSet<>()));

            JsonArray factionsArray = GsonHelper.getAsJsonArray(json, "factions");
            for (JsonElement e : factionsArray) {
                JsonObject faction = GsonHelper.convertToJsonObject(e, "faction");
                String factionName = GsonHelper.getAsString(faction, "name");
                FactionDefinition.CopyBuilder builder = defaultDefinition.copy(factionName, Integer.parseInt(GsonHelper.getAsString(faction, "color"), 16));
                if (faction.has(STANDING_SETTINGS)) {
                    overrideStandingSettings(faction, builder);
                }
                // a faction is never hostile to itself
                builder.removeHostileTowards(factionName);
                if (faction.has(HOSTILE_TOWARDS_FACTIONS)) {
                    Map<String, Boolean> hostileTowards = parseHostileTowards(faction);
                    hostileTowards.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).forEach(builder::addHostileTowards);
                    hostileTowards.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey).forEach(builder::removeHostileTowards);
                }

                TargetRegistry.parseTargets(faction).ifPresent(builder::overrideTargetList);

                if (faction.has(THEMED_BLOCKS)) {
                    builder.overrideThemedBlocksTags(parseThemedBlocks(faction, factionName));
                }

                parsed.put(factionName, builder.build());
            }
            factions = parsed;
        }

        private void overrideStandingSettings(JsonObject faction, FactionDefinition.CopyBuilder builder) {
            JsonObject standingSettingsJson = GsonHelper.getAsJsonObject(faction, STANDING_SETTINGS);
            builder.setStandingSettings(standingSettings -> {
                if (standingSettingsJson.has(PLAYER_DEFAULT_STANDING)) {
                    standingSettings.setPlayerDefaultStanding(GsonHelper.getAsInt(standingSettingsJson, PLAYER_DEFAULT_STANDING));
                }
                if (standingSettingsJson.has(STANDING_CAN_CHANGE)) {
                    standingSettings.setStandingCanChange(GsonHelper.getAsBoolean(standingSettingsJson, STANDING_CAN_CHANGE));
                }
                if (standingSettingsJson.has(STANDING_CHANGES)) {
                    standingSettings.overrideStandingChanges(parseStandingChanges(standingSettingsJson));
                }
            });
        }

        private FactionDefinition.StandingSettings parseStandingSettings(JsonObject factionSettings) {
            JsonObject standingSettings = GsonHelper.getAsJsonObject(factionSettings, STANDING_SETTINGS);
            return new FactionDefinition.StandingSettings(GsonHelper.getAsInt(standingSettings, PLAYER_DEFAULT_STANDING),
                    GsonHelper.getAsBoolean(standingSettings, STANDING_CAN_CHANGE), parseStandingChanges(standingSettings));
        }

        private Map<String, Integer> parseStandingChanges(JsonObject json) {
            Map<String, Integer> ret = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : GsonHelper.getAsJsonObject(json, STANDING_CHANGES).entrySet()) {
                ret.put(entry.getKey(), GsonHelper.convertToInt(entry.getValue(), entry.getKey()));
            }
            return ret;
        }

        private Map<String, CompoundTag> parseThemedBlocks(JsonObject json, String factionName) {
            Map<String, CompoundTag> ret = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : GsonHelper.getAsJsonObject(json, THEMED_BLOCKS).entrySet()) {
                ret.put(entry.getKey(), getThemedBlocksTag(entry.getValue(), factionName));
            }
            return ret;
        }

        private CompoundTag getThemedBlocksTag(JsonElement json, String factionName) {
            Tag tag = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, json);
            if (tag instanceof CompoundTag compound) {
                compound.putString("customData", factionName);
                return compound;
            }
            LOGGER.error("Error parsing themed blocks nbt of faction {}", factionName);
            return new CompoundTag();
        }

        private Map<String, Boolean> parseHostileTowards(JsonObject json) {
            Map<String, Boolean> ret = new HashMap<>();
            if (json.has(HOSTILE_TOWARDS_FACTIONS)) {
                for (Map.Entry<String, JsonElement> entry : GsonHelper.getAsJsonObject(json, HOSTILE_TOWARDS_FACTIONS).entrySet()) {
                    ret.put(entry.getKey(), GsonHelper.convertToBoolean(entry.getValue(), entry.getKey()));
                }
            }
            return ret;
        }
    }
}
