/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/registry/NpcDefaultsRegistry.
 */
package io.github.capttracker.ancientwarfarenpc.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfare.registry.IRegistryDataParser;
import io.github.capttracker.ancientwarfare.util.RegistryTools;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcs;
import io.github.capttracker.ancientwarfarenpc.registry.FactionNpcDefault.HeightRange;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.Set;

/**
 * The NPC stat defaults: {@code faction_npc_defaults.json} (per faction and per NPC subtype, 44 factions) and
 * {@code owned_npc_defaults.json} (per player-owned NPC type). A global default is overridden by the entry of a subtype, which is
 * overridden by the entry of a faction, which is overridden by the subtype entry inside the faction.
 *
 * <p>Subtype keys of faction NPCs are the short names ({@code soldier}, {@code archer.elite}, {@code civilian.male}), the registry
 * name of the entity type without the {@code faction.} prefix. The lookups take plain strings; 1.12.2 took the NPC instance.
 */
public final class NpcDefaultsRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NPC_SUBTYPES_ELEMENT = "npc_subtypes";
    private static final String FACTION_NPC_PREFIX = "faction.";

    private static volatile Map<String, Map<String, FactionNpcDefault>> factionNpcDefaults = new HashMap<>();
    private static volatile Map<String, OwnedNpcDefault> ownedNpcDefaults = new HashMap<>();

    /** 1.12.2 attribute names that data files may still use, to their 1.21 ids. */
    private static final Map<String, String> LEGACY_ATTRIBUTES = Map.of(
            "generic.maxHealth", "generic.max_health",
            "generic.movementSpeed", "generic.movement_speed",
            "generic.followRange", "generic.follow_range",
            "generic.attackDamage", "generic.attack_damage",
            "generic.knockbackResistance", "generic.knockback_resistance",
            "generic.attackSpeed", "generic.attack_speed",
            "generic.armorToughness", "generic.armor_toughness");

    private NpcDefaultsRegistry() {}

    /** @param npcType the short subtype name, for example {@code archer.elite}; null if the faction or type has no entry */
    @Nullable
    public static FactionNpcDefault getFactionNpcDefault(String faction, String npcType) {
        Map<String, FactionNpcDefault> forFaction = factionNpcDefaults.get(faction);
        return forFaction == null ? null : forFaction.get(npcType);
    }

    /** @param npcType {@code combat}, {@code worker}, {@code courier}, {@code trader}, {@code priest}, {@code bard} or {@code siege_engineer} */
    @Nullable
    public static OwnedNpcDefault getOwnedNpcDefault(String npcType) {
        return ownedNpcDefaults.get(npcType);
    }

    public static class OwnedNpcDefaultsParser extends NpcDefaultsParserBase {
        @Override
        public String getName() {
            return "owned_npc_defaults";
        }

        @Override
        public void beginLoad() {
            ownedNpcDefaults = new HashMap<>();
        }

        @Override
        public void parse(JsonObject json) {
            OwnedNpcDefault overallDefault = parseDefaults(json);
            Map<String, OwnedNpcDefault> parsed = new HashMap<>(ownedNpcDefaults);
            if (json.has(NPC_SUBTYPES_ELEMENT)) {
                for (Map.Entry<String, JsonElement> entry : GsonHelper.getAsJsonObject(json, NPC_SUBTYPES_ELEMENT).entrySet()) {
                    parsed.put(entry.getKey(), getSubtypeDefault(entry.getValue(), overallDefault));
                }
            }
            fillRemainingSubtypeDefaults(overallDefault, parsed);
            ownedNpcDefaults = parsed;
        }

        private OwnedNpcDefault parseDefaults(JsonObject json) {
            JsonObject defaults = GsonHelper.getAsJsonObject(json, "defaults");
            return new OwnedNpcDefault(TargetRegistry.parseTargets(defaults).orElse(new HashSet<>()), getAttributes(defaults), getExperienceDrop(defaults).orElse(0),
                    getCanSwim(defaults).orElse(true), getCanBreakDoors(defaults).orElse(true), getEquipment(defaults));
        }

        private OwnedNpcDefault getSubtypeDefault(JsonElement entry, OwnedNpcDefault base) {
            JsonObject data = GsonHelper.convertToJsonObject(entry, "npc subtype");
            OwnedNpcDefault npcSubtypeDefault = base.setAttributes(getAttributes(data));
            npcSubtypeDefault = getExperienceDrop(data).map(npcSubtypeDefault::setExperienceDrop).orElse(npcSubtypeDefault);
            npcSubtypeDefault = getCanSwim(data).map(npcSubtypeDefault::setCanSwim).orElse(npcSubtypeDefault);
            npcSubtypeDefault = getCanBreakDoors(data).map(npcSubtypeDefault::setCanBreakDoors).orElse(npcSubtypeDefault);
            npcSubtypeDefault = npcSubtypeDefault.setEquipment(getEquipment(data));
            Optional<Set<String>> targets = TargetRegistry.parseTargets(data);
            if (targets.isPresent()) {
                npcSubtypeDefault = npcSubtypeDefault.overrideTargets(targets.get());
            }
            return npcSubtypeDefault;
        }

        private void fillRemainingSubtypeDefaults(OwnedNpcDefault overallDefault, Map<String, OwnedNpcDefault> npcSubtypeDefaults) {
            for (String subtype : AwNpcs.getNpcMap().keySet()) {
                if (!subtype.startsWith(FACTION_NPC_PREFIX)) {
                    npcSubtypeDefaults.putIfAbsent(subtype, overallDefault);
                }
            }
        }
    }

    private abstract static class NpcDefaultsParserBase implements IRegistryDataParser {
        protected Optional<Boolean> getCanBreakDoors(JsonObject json) {
            return json.has("can_break_doors") ? Optional.of(GsonHelper.getAsBoolean(json, "can_break_doors")) : Optional.empty();
        }

        protected Optional<Boolean> getCanSwim(JsonObject json) {
            return json.has("can_swim") ? Optional.of(GsonHelper.getAsBoolean(json, "can_swim")) : Optional.empty();
        }

        protected Optional<Integer> getExperienceDrop(JsonObject json) {
            return json.has("experience_drop") ? Optional.of(GsonHelper.getAsInt(json, "experience_drop")) : Optional.empty();
        }

        protected Map<ResourceLocation, Double> getAttributes(JsonObject json) {
            if (!json.has("attributes")) {
                return Collections.emptyMap();
            }
            Map<ResourceLocation, Double> ret = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : GsonHelper.getAsJsonObject(json, "attributes").entrySet()) {
                String name = LEGACY_ATTRIBUTES.getOrDefault(e.getKey(), e.getKey());
                ResourceLocation id = ResourceLocation.tryParse(name);
                if (id == null) {
                    throw new JsonParseException("Invalid attribute name \"" + e.getKey() + "\"");
                }
                ret.put(id, (double) GsonHelper.convertToFloat(e.getValue(), name));
            }
            return ret;
        }

        protected Map<String, Item> getEquipment(JsonObject json) {
            if (!json.has("equipment")) {
                return Collections.emptyMap();
            }
            Map<String, Item> ret = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : GsonHelper.getAsJsonObject(json, "equipment").entrySet()) {
                ret.put(parseEquipmentSlot(e.getKey()), parseEquipmentItem(e.getValue()));
            }
            return ret;
        }

        private Item parseEquipmentItem(JsonElement itemJson) {
            try {
                return RegistryTools.getItem(GsonHelper.convertToString(itemJson, "item"));
            } catch (MissingResourceException ex) {
                LOGGER.error("Error parsing npc equipment: ", ex);
                return Items.AIR;
            }
        }

        private String parseEquipmentSlot(String slotName) {
            if (NpcDefault.WORK_SLOT.equals(slotName) || NpcDefault.UPKEEP_SLOT.equals(slotName)) {
                return slotName;
            }
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.getName().equals(slotName)) {
                    return slotName;
                }
            }
            throw new JsonParseException("Invalid equipment slot name \"" + slotName + "\"");
        }
    }

    public static class FactionNpcDefaultsParser extends NpcDefaultsParserBase {
        private static final String HEIGHT_ELEMENT = "height";
        private static final String THINNESS_ELEMENT = "thinness";

        @Override
        public String getName() {
            return "faction_npc_defaults";
        }

        @Override
        public void beginLoad() {
            factionNpcDefaults = new HashMap<>();
        }

        @Override
        public void parse(JsonObject json) {
            Map<String, Map<String, FactionNpcDefault>> parsed = new HashMap<>(factionNpcDefaults);
            Map<String, FactionNpcDefault> npcSubtypeDefaults = parseDefaultsWithSubtypes(json);
            parseFactions(json, npcSubtypeDefaults, parsed);
            factionNpcDefaults = parsed;
        }

        private Map<String, FactionNpcDefault> parseDefaultsWithSubtypes(JsonObject json) {
            JsonObject defaults = GsonHelper.getAsJsonObject(json, "defaults");
            FactionNpcDefault globalDefault = new FactionNpcDefault(getAttributes(defaults), getExperienceDrop(defaults).orElse(0),
                    getCanSwim(defaults).orElse(true), getCanBreakDoors(defaults).orElse(true), getEquipment(defaults),
                    FactionAdditionalAttributes.parse(defaults), getEnabled(defaults).orElse(true), getLootTable(defaults).orElse(null),
                    getHeightRange(defaults).orElse(HeightRange.DEFAULT), getThinness(defaults).orElse(1.0f));

            return parseSubtypes(defaults, globalDefault);
        }

        private void parseFactions(JsonObject json, Map<String, FactionNpcDefault> npcSubtypeDefaults, Map<String, Map<String, FactionNpcDefault>> out) {
            if (json.has("factions")) {
                for (Map.Entry<String, JsonElement> e : GsonHelper.getAsJsonObject(json, "factions").entrySet()) {
                    JsonObject factionElement = GsonHelper.convertToJsonObject(e.getValue(), "faction");
                    Map<String, FactionNpcDefault> factionSubtypeDefaults = getFactionDefaults(factionElement, npcSubtypeDefaults);
                    if (factionElement.has(NPC_SUBTYPES_ELEMENT)) {
                        factionSubtypeDefaults = getFactionNpcDefaults(factionSubtypeDefaults, factionElement.get(NPC_SUBTYPES_ELEMENT));
                    }
                    out.put(e.getKey(), factionSubtypeDefaults);
                }
            }
            fillRemainingFactionDefaults(npcSubtypeDefaults, out);
        }

        private FactionNpcDefault getSubtypeDefault(JsonElement json, FactionNpcDefault base) {
            JsonObject data = GsonHelper.convertToJsonObject(json, "npc subtype");
            FactionNpcDefault npcSubtypeDefault = base.setAttributes(getAttributes(data));
            npcSubtypeDefault = getExperienceDrop(data).map(npcSubtypeDefault::setExperienceDrop).orElse(npcSubtypeDefault);
            npcSubtypeDefault = getCanSwim(data).map(npcSubtypeDefault::setCanSwim).orElse(npcSubtypeDefault);
            npcSubtypeDefault = getCanBreakDoors(data).map(npcSubtypeDefault::setCanBreakDoors).orElse(npcSubtypeDefault);
            npcSubtypeDefault = npcSubtypeDefault.setEquipment(getEquipment(data));
            npcSubtypeDefault = npcSubtypeDefault.setAdditionalAttributes(FactionAdditionalAttributes.parse(data));
            npcSubtypeDefault = getEnabled(data).map(npcSubtypeDefault::setEnabled).orElse(npcSubtypeDefault);
            npcSubtypeDefault = getLootTable(data).map(npcSubtypeDefault::setLootTable).orElse(npcSubtypeDefault);
            npcSubtypeDefault = getHeightRange(data).map(npcSubtypeDefault::setHeightRange).orElse(npcSubtypeDefault);
            npcSubtypeDefault = getThinness(data).map(npcSubtypeDefault::setThinness).orElse(npcSubtypeDefault);
            // The "spells" key of the Electroblob's Wizardry spell casters is ignored (that compat is dropped).
            return npcSubtypeDefault;
        }

        private Optional<Boolean> getEnabled(JsonObject data) {
            return data.has("enabled") ? Optional.of(GsonHelper.getAsBoolean(data, "enabled")) : Optional.empty();
        }

        private Optional<HeightRange> getHeightRange(JsonObject data) {
            if (!data.has(HEIGHT_ELEMENT)) {
                return Optional.empty();
            }
            if (GsonHelper.isNumberValue(data, HEIGHT_ELEMENT)) {
                float height = GsonHelper.getAsFloat(data, HEIGHT_ELEMENT);
                return Optional.of(new HeightRange(height, height));
            }
            JsonObject range = GsonHelper.getAsJsonObject(data, HEIGHT_ELEMENT);
            return Optional.of(new HeightRange(GsonHelper.getAsFloat(range, "min"), GsonHelper.getAsFloat(range, "max")));
        }

        private Optional<Float> getThinness(JsonObject data) {
            return data.has(THINNESS_ELEMENT) ? Optional.of(GsonHelper.getAsFloat(data, THINNESS_ELEMENT)) : Optional.empty();
        }

        private Optional<ResourceLocation> getLootTable(JsonObject data) {
            return data.has("loot_table") ? Optional.of(ResourceLocation.parse(GsonHelper.getAsString(data, "loot_table"))) : Optional.empty();
        }

        private Map<String, FactionNpcDefault> parseSubtypes(JsonObject json, FactionNpcDefault overallDefault) {
            Map<String, FactionNpcDefault> npcSubtypeDefaults = new HashMap<>();
            if (json.has(NPC_SUBTYPES_ELEMENT)) {
                for (Map.Entry<String, JsonElement> e : GsonHelper.getAsJsonObject(json, NPC_SUBTYPES_ELEMENT).entrySet()) {
                    npcSubtypeDefaults.put(e.getKey(), getSubtypeDefault(e.getValue(), overallDefault));
                }
            }
            for (String subtype : getFactionNpcSubtypes()) {
                npcSubtypeDefaults.putIfAbsent(subtype, overallDefault);
            }
            return npcSubtypeDefaults;
        }

        private Map<String, FactionNpcDefault> getFactionDefaults(JsonElement json, Map<String, FactionNpcDefault> npcSubtypeDefaults) {
            Map<String, FactionNpcDefault> ret = new HashMap<>();
            for (Map.Entry<String, FactionNpcDefault> subtype : npcSubtypeDefaults.entrySet()) {
                ret.put(subtype.getKey(), getSubtypeDefault(json, subtype.getValue()));
            }
            return ret;
        }

        private Map<String, FactionNpcDefault> getFactionNpcDefaults(Map<String, FactionNpcDefault> npcSubtypeDefaults, JsonElement subtypesElement) {
            Map<String, FactionNpcDefault> typeDefaults = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : GsonHelper.convertToJsonObject(subtypesElement, NPC_SUBTYPES_ELEMENT).entrySet()) {
                FactionNpcDefault base = npcSubtypeDefaults.get(e.getKey());
                if (base == null) {
                    LOGGER.error("Faction npc subtype {} has no global default, skipping", e.getKey());
                    continue;
                }
                typeDefaults.put(e.getKey(), getSubtypeDefault(e.getValue(), base));
            }
            for (Map.Entry<String, FactionNpcDefault> subtypeDefault : npcSubtypeDefaults.entrySet()) {
                typeDefaults.putIfAbsent(subtypeDefault.getKey(), subtypeDefault.getValue());
            }
            return typeDefaults;
        }

        private void fillRemainingFactionDefaults(Map<String, FactionNpcDefault> npcSubtypeDefaults, Map<String, Map<String, FactionNpcDefault>> out) {
            for (String faction : FactionRegistry.getFactionNames()) {
                out.putIfAbsent(faction, new HashMap<>(npcSubtypeDefaults));
            }
        }

        private Set<String> getFactionNpcSubtypes() {
            Set<String> ret = new HashSet<>();
            for (String key : AwNpcs.getNpcMap().keySet()) {
                if (key.startsWith(FACTION_NPC_PREFIX)) {
                    ret.add(key.substring(FACTION_NPC_PREFIX.length()));
                }
            }
            return ret;
        }
    }
}
