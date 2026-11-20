/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/registry/CropFarmRegistry.
 */
package io.github.capttracker.ancientwarfareautomation.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfare.registry.IRegistryDataParser;
import io.github.capttracker.ancientwarfare.util.parsing.BlockStateMatcher;
import io.github.capttracker.ancientwarfare.util.parsing.ItemStackMatcher;
import io.github.capttracker.ancientwarfare.util.parsing.JsonHelper;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.cropfarm.CropBreakOnly;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.cropfarm.CropDefault;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.cropfarm.CropGourd;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.cropfarm.CropKeepBottom;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.cropfarm.CropMatureDefined;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.cropfarm.CropStem;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.cropfarm.CropTall;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.cropfarm.ICrop;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What the crop farm knows: which blocks can be tilled (and into what), which blocks are soil, and the handlers
 * ({@link ICrop}) for the different kinds of crops.
 *
 * <p>The tables are filled by three data parsers (registered by {@code AwAutomationBase}) from
 * {@code data/<namespace>/registry/**} files: {@code tillable_blocks}, {@code crop_blocks} and {@code soil_blocks}
 * (the {@code type} field or the file name picks the parser, see {@code RegistryLoader}). The 1.12.2 base files are
 * rewritten for the 1.21.1 block names ({@code data/ancientwarfareautomation/registry/crop_farm/}); the compat
 * files for 1.12.2 mods are not ported. A reload (/reload) clears what the parsers loaded and parses again. The
 * handlers built in here ({@link CropStem}, {@link CropGourd}, {@link CropDefault}) and the ones other mods
 * add through {@link #registerCrop} stay.
 *
 * <p>Crop handlers are tried most recently registered first (data files, then API registrations, then the built-in
 * ones), so compatibility crops are processed before the default one; {@link CropDefault} is the fallback.
 */
public class CropFarmRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** tillable state to tilled state; filled by the tillable parser */
    private static final Map<BlockStateMatcher, BlockState> tillableBlocks = new HashMap<>();
    /** the tilled states of the tillable mapping count as soil; filled by the tillable parser */
    private static final Set<BlockStateMatcher> tilledSoilBlocks = new HashSet<>();
    /** filled by the soil parser */
    private static final Set<BlockStateMatcher> soilBlocks = new HashSet<>();
    private static final ICrop DEFAULT_CROP = new CropDefault();
    /** the built-in handlers, the first one is tried first */
    private static final List<ICrop> builtInCrops = List.of(new CropStem(), new CropGourd(), DEFAULT_CROP);
    /** registered through {@link #registerCrop}, never cleared; the first one is tried first */
    private static final List<ICrop> apiCrops = new CopyOnWriteArrayList<>();
    /** filled by the crop parser; the first one is tried first */
    private static final List<ICrop> dataCrops = new CopyOnWriteArrayList<>();

    public static boolean isTillable(BlockState state) {
        return tillableBlocks.keySet().stream().anyMatch(m -> m.test(state));
    }

    public static BlockState getTilledState(BlockState tillable) {
        return tillableBlocks.entrySet().stream().filter(e -> e.getKey().test(tillable)).map(Map.Entry::getValue).findFirst().orElse(tillable);
    }

    public static boolean isSoil(BlockState state) {
        return soilBlocks.stream().anyMatch(matcher -> matcher.test(state)) || tilledSoilBlocks.stream().anyMatch(matcher -> matcher.test(state));
    }

    /** Adds a crop handler that is tried before every older one. For other mods' compatibility code. */
    public static void registerCrop(ICrop crop) {
        //adding to start of the list so that the last registered is always the first one processed, allowing for compatibility crops to be processed before default one
        apiCrops.add(0, crop);
    }

    public static ICrop getCrop(BlockState state) {
        return allCrops().filter(h -> h.matches(state)).findFirst().orElse(DEFAULT_CROP);
    }

    public static ICrop getCrop(ItemStack stack) {
        return allCrops().filter(h -> h.matches(stack)).findFirst().orElse(DEFAULT_CROP);
    }

    private static java.util.stream.Stream<ICrop> allCrops() {
        List<ICrop> all = new ArrayList<>(dataCrops.size() + apiCrops.size() + builtInCrops.size());
        all.addAll(dataCrops);
        all.addAll(apiCrops);
        all.addAll(builtInCrops);
        return all.stream();
    }

    /** Reads {@code tillable_mapping}: pairs of a tillable state matcher and the state it becomes. */
    public static class TillableParser implements IRegistryDataParser {
        @Override
        public String getName() {
            return "tillable_blocks";
        }

        @Override
        public void beginLoad() {
            tillableBlocks.clear();
            tilledSoilBlocks.clear();
        }

        @Override
        public void parse(JsonObject json) {
            JsonArray tillables = GsonHelper.getAsJsonArray(json, "tillable_mapping");

            for (JsonElement t : tillables) {
                JsonObject tillableMapping = GsonHelper.convertToJsonObject(t, "tillable_mapping entry");
                try {
                    BlockStateMatcher tillableState = JsonHelper.getBlockStateMatcher(tillableMapping, "tillable");
                    BlockState tilledState = JsonHelper.getBlockState(tillableMapping, "tilled");

                    tillableBlocks.put(tillableState, tilledState);
                    tilledSoilBlocks.add(JsonHelper.getBlockStateMatcher(tillableMapping, "tilled"));
                } catch (RuntimeException e) {
                    LOGGER.error("Skipping invalid tillable_mapping entry {}", tillableMapping, e);
                }
            }
        }
    }

    /** Reads {@code crops}: a block state matcher and a {@code type} (tall, keep_bottom, mature_defined, break_only; else the default handler). */
    public static class CropParser implements IRegistryDataParser {
        @Override
        public String getName() {
            return "crop_blocks";
        }

        @Override
        public void beginLoad() {
            dataCrops.clear();
        }

        @Override
        public void parse(JsonObject json) {
            JsonArray crops = GsonHelper.getAsJsonArray(json, "crops");

            for (JsonElement t : crops) {
                JsonObject crop = GsonHelper.convertToJsonObject(t, "crops entry");
                try {
                    // registered like registerCrop: the last one parsed is tried first
                    dataCrops.add(0, getCrop(crop));
                } catch (RuntimeException e) {
                    LOGGER.error("Skipping invalid crops entry {}", crop, e);
                }
            }
        }

        private static ICrop getCrop(JsonObject crop) {
            BlockStateMatcher stateMatcher = JsonHelper.getBlockStateMatcher(crop, "crop");
            String type = GsonHelper.getAsString(crop, "type");
            JsonObject properties = crop.has("properties") ? GsonHelper.getAsJsonObject(crop, "properties") : new JsonObject();
            switch (type) {
                case "tall":
                    return TallParser.parse(stateMatcher, properties);
                case "keep_bottom":
                    return KeepBottomParser.parse(stateMatcher);
                case "mature_defined":
                    return MatureDefinedParser.parse(stateMatcher, JsonHelper.getBlockState(crop, "crop"), crop);
                case "break_only":
                    return BreakOnlyParser.parse(stateMatcher, JsonHelper.getItemStackMatcher(crop, "item"));
                default:
                    return DEFAULT_CROP;
            }
        }

        private static class TallParser {
            public static ICrop parse(BlockStateMatcher stateMatcher, JsonObject properties) {
                return new CropTall(stateMatcher, GsonHelper.getAsInt(properties, "height"));
            }
        }

        private static class KeepBottomParser {
            public static ICrop parse(BlockStateMatcher stateMatcher) {
                return new CropKeepBottom(stateMatcher);
            }
        }

        private static class MatureDefinedParser {
            public static ICrop parse(BlockStateMatcher stateMatcher, BlockState state, JsonObject crop) {
                return new CropMatureDefined(stateMatcher, JsonHelper.getPropertyStateMatcher(state, crop, "mature"));
            }
        }

        private static class BreakOnlyParser {
            public static ICrop parse(BlockStateMatcher stateMatcher, ItemStackMatcher stackMatcher) {
                return new CropBreakOnly(stateMatcher, stackMatcher);
            }
        }
    }

    /** Reads {@code soils}: block state matchers of the blocks crops can be planted on (besides farmland and the tilled states). */
    public static class SoilParser implements IRegistryDataParser {

        @Override
        public String getName() {
            return "soil_blocks";
        }

        @Override
        public void beginLoad() {
            soilBlocks.clear();
        }

        @Override
        public void parse(JsonObject json) {
            JsonArray plantables = GsonHelper.getAsJsonArray(json, "soils");

            for (JsonElement t : plantables) {
                JsonObject soil = GsonHelper.convertToJsonObject(t, "soils entry");
                try {
                    soilBlocks.add(JsonHelper.getBlockStateMatcher(soil));
                } catch (RuntimeException e) {
                    LOGGER.error("Skipping invalid soils entry {}", soil, e);
                }
            }
        }
    }
}
