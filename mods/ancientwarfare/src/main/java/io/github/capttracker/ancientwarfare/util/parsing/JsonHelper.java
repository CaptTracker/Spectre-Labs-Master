/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/util/parsing/JsonHelper.
 */
package io.github.capttracker.ancientwarfare.util.parsing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonWriter;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfare.util.RegistryTools;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

/**
 * Readers for the JSON shapes used by the registry data files: block states and matchers, item stacks and
 * matchers, and generic maps and sets. Where vanilla codecs fit new data (a plain {@code ItemStack} or
 * {@code BlockState}), use those; this keeps the original's compact shapes so the data files port over.
 *
 * <p>Block states: {@code "modid:block"} or {@code {"name": "modid:block", "properties": {"prop": "value"}}}.
 * Item stacks: {@code "modid:item"} or {@code {"name": "modid:item", "count": 2, "components": {...},
 * "nbt": "{snbt}", "ignore_nbt": true}}. {@code components} is a vanilla data component patch; {@code nbt} (SNBT
 * text) becomes the {@code custom_data} component, which is what the 1.12.2 item tag turned into for stacks
 * that were AW's own data. The 1.12.2 {@code data} (meta) field no longer means anything and is rejected
 * with an error so unconverted data is noticed.
 */
public final class JsonHelper {
    private static final Logger LOGGER = LogUtils.getLogger();

    private JsonHelper() {}

    /** The registries of the server being loaded; set from the reload event, as the server does not exist yet at the first load. */
    @Nullable
    private static volatile HolderLookup.Provider serverRegistryAccess;

    /**
     * Where component values that need registries (enchantments, potions...) are looked up. Defaults to the running
     * server's registries, else the ones given to {@link #setServerRegistryAccess}, else none. Client code that
     * parses data may install the connected level's registry access.
     */
    private static volatile Supplier<HolderLookup.Provider> registryProvider = () -> {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.registryAccess();
        }
        HolderLookup.Provider access = serverRegistryAccess;
        return access != null ? access : RegistryAccess.EMPTY;
    };

    public static void setRegistryProvider(Supplier<HolderLookup.Provider> provider) {
        registryProvider = provider;
    }

    /** Called with the registry access of each server data reload ({@code AddReloadListenerEvent#getRegistryAccess}). */
    public static void setServerRegistryAccess(@Nullable HolderLookup.Provider access) {
        serverRegistryAccess = access;
    }

    // ---- block states ----

    public static BlockState getBlockState(JsonObject parent, String elementName) {
        return getBlockState(parent, elementName, Block::defaultBlockState, BlockTools::updateProperty);
    }

    public static BlockState getBlockState(JsonElement json) {
        return BlockTools.getBlockState(getBlockNameAndProperties(json), Block::defaultBlockState, BlockTools::updateProperty);
    }

    public static BlockStateMatcher getBlockStateMatcher(JsonElement stateJson) {
        return BlockTools.getBlockState(getBlockNameAndProperties(stateJson), BlockStateMatcher::new, BlockStateMatcher::addProperty);
    }

    public static BlockStateMatcher getBlockStateMatcher(JsonObject stateJson) {
        return getBlockState(stateJson, BlockStateMatcher::new, BlockStateMatcher::addProperty);
    }

    public static BlockStateMatcher getBlockStateMatcher(JsonObject parent, String elementName) {
        return getBlockState(parent, elementName, BlockStateMatcher::new, BlockStateMatcher::addProperty);
    }

    private static <T> T getBlockState(JsonObject stateJson, Function<Block, T> init, BlockTools.AddPropertyFunction<T> addProperty) {
        return BlockTools.getBlockState(getBlockNameAndProperties(stateJson), init, addProperty);
    }

    private static <T> T getBlockState(JsonObject parent, String elementName, Function<Block, T> init, BlockTools.AddPropertyFunction<T> addProperty) {
        return BlockTools.getBlockState(getBlockNameAndProperties(parent, elementName), init, addProperty);
    }

    private static Tuple<String, Map<String, String>> getBlockNameAndProperties(JsonObject stateJson) {
        Map<String, String> properties = new HashMap<>();

        if (GsonHelper.isValidNode(stateJson, "properties")) {
            GsonHelper.getAsJsonObject(stateJson, "properties").entrySet().forEach(p -> properties.put(p.getKey(), p.getValue().getAsString()));
        }

        return new Tuple<>(GsonHelper.getAsString(stateJson, "name"), properties);
    }

    private static Tuple<String, Map<String, String>> getBlockNameAndProperties(JsonElement stateElement) {
        if (stateElement.isJsonPrimitive()) {
            return new Tuple<>(GsonHelper.convertToString(stateElement, "block"), new HashMap<>());
        }

        return getBlockNameAndProperties(GsonHelper.convertToJsonObject(stateElement, "block"));
    }

    private static Tuple<String, Map<String, String>> getBlockNameAndProperties(JsonObject parent, String elementName) {
        if (!GsonHelper.isValidNode(parent, elementName)) {
            throw new JsonParseException(getMissingMemberErrorMessage(parent, elementName));
        }
        return getBlockNameAndProperties(parent.get(elementName));
    }

    /**
     * A predicate over block states read from either an array of matchers ({@code arrayElement}, each holding an
     * {@code individualElement}) or one matcher ({@code individualElement}).
     */
    public static Predicate<BlockState> getBlockStateMatcher(JsonObject json, String arrayElement, String individualElement) {
        if (json.has(arrayElement)) {
            JsonArray stateMatchers = GsonHelper.getAsJsonArray(json, arrayElement);
            return new MultiBlockStateMatcher(StreamSupport.stream(stateMatchers.spliterator(), false)
                    .map(e -> getBlockStateMatcher(GsonHelper.convertToJsonObject(e, individualElement)))
                    .toArray(BlockStateMatcher[]::new));
        }
        return getBlockStateMatcher(json, individualElement);
    }

    @SuppressWarnings("rawtypes")
    public static PropertyState getPropertyState(BlockState state, JsonObject parent, String elementName) {
        JsonObject jsonProperty = GsonHelper.getAsJsonObject(parent, elementName);

        if (jsonProperty.entrySet().isEmpty()) {
            throw new JsonParseException("Expected at least one property defined for " + elementName + " in " + parent);
        }

        Entry<String, JsonElement> propJson = jsonProperty.entrySet().iterator().next();
        String propName = propJson.getKey();
        String propValue = propJson.getValue().getAsString();

        return BlockTools.getPropertyState(state.getBlock(), state.getBlock().getStateDefinition(), propName, propValue);
    }

    public static PropertyStateMatcher getPropertyStateMatcher(BlockState state, JsonObject parent, String elementName) {
        return new PropertyStateMatcher(getPropertyState(state, parent, elementName));
    }

    // ---- item stacks ----

    public static ItemStack getItemStack(JsonElement json) {
        return readItemStack(json, (stack, ignoreComponents) -> stack);
    }

    public static ItemStack getItemStack(JsonObject json, String elementName) {
        if (!GsonHelper.isValidNode(json, elementName)) {
            throw new JsonParseException(getMissingMemberErrorMessage(json, elementName));
        }
        return getItemStack(json.get(elementName));
    }

    private static String getMissingMemberErrorMessage(JsonObject json, String elementName) {
        return "Expected " + elementName + " member in " + json;
    }

    private static <T> T readItemStack(JsonElement element, ItemStackCreator<T> creator) {
        if (element.isJsonPrimitive()) {
            return creator.instantiate(new ItemStack(RegistryTools.getItem(element.getAsString()), 1), false);
        }

        JsonObject obj = element.getAsJsonObject();
        if (obj.has("data")) {
            throw new JsonParseException("Item stacks no longer have a data (meta) value, use a separate item or components: " + obj);
        }
        Item item = RegistryTools.getItem(GsonHelper.getAsString(obj, "name"));
        int count = GsonHelper.getAsInt(obj, "count", 1);

        ItemStack stack = new ItemStack(item, count);
        if (obj.has("components")) {
            DataComponentPatch patch = DataComponentPatch.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registryProvider.get()), obj.get("components"))
                    .getOrThrow(message -> new JsonParseException("Invalid item components in " + obj + ": " + message));
            stack.applyComponents(patch);
        }
        if (obj.has("nbt")) {
            try {
                CompoundTag tag = TagParser.parseTag(GsonHelper.getAsString(obj, "nbt"));
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            } catch (CommandSyntaxException e) {
                LOGGER.error("Error reading item stack nbt {}", obj.get("nbt"), e);
            }
        }

        boolean ignoreComponents = GsonHelper.getAsBoolean(obj, "ignore_nbt", false);

        return creator.instantiate(stack, ignoreComponents);
    }

    public static ItemStackMatcher getItemStackMatcher(JsonElement element) {
        return readItemStack(element, (stack, ignore) -> new ItemStackMatcher.Builder(stack.getItem())
                .setComponents(stack.getComponentsPatch()).setIgnoreComponents(ignore).build());
    }

    public static ItemStackMatcher getItemStackMatcher(JsonObject parent, String elementName) {
        if (!GsonHelper.isValidNode(parent, elementName)) {
            throw new JsonParseException(getMissingMemberErrorMessage(parent, elementName));
        }
        return getItemStackMatcher(parent.get(elementName));
    }

    /** The inverse of {@link #getItemStack(JsonElement)}: name, count if not 1, and the component changes if any. */
    public static JsonObject serializeItemStack(ItemStack stack, HolderLookup.Provider registries) {
        JsonObject json = new JsonObject();
        json.addProperty("name", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (stack.getCount() != 1) {
            json.addProperty("count", stack.getCount());
        }
        DataComponentPatch patch = stack.getComponentsPatch();
        if (!patch.isEmpty()) {
            json.add("components", DataComponentPatch.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), patch)
                    .getOrThrow(message -> new JsonParseException("Cannot serialize item components: " + message)));
        }
        return json;
    }

    public static List<ItemStack> getItemStacks(JsonArray stacks) {
        List<ItemStack> ret = NonNullList.create();
        for (JsonElement stackElement : stacks) {
            ret.add(getItemStack(GsonHelper.convertToJsonObject(stackElement, "itemstack")));
        }
        return ret;
    }

    // ---- maps and sets ----

    public static <K, V> Map<K, V> mapFromJson(JsonObject json, String propertyName, Function<Entry<String, JsonElement>, K> parseKey,
                                               Function<Entry<String, JsonElement>, V> parseValue) {
        return mapFromObjectProperties(GsonHelper.getAsJsonObject(json, propertyName), new HashMap<>(), parseKey, parseValue);
    }

    public static <K, V> Map<K, V> mapFromJson(JsonElement json, Function<Entry<String, JsonElement>, K> parseKey,
                                               Function<Entry<String, JsonElement>, V> parseValue) {
        return mapFromJson(GsonHelper.convertToJsonObject(json, "map"), parseKey, parseValue);
    }

    public static <K, V> Map<K, V> mapFromJson(JsonObject json, Function<Entry<String, JsonElement>, K> parseKey,
                                               Function<Entry<String, JsonElement>, V> parseValue) {
        return mapFromObjectProperties(json, new HashMap<>(), parseKey, parseValue);
    }

    public static <K, V> void mapFromJson(JsonObject json, String propertyName, Map<K, V> ret, Function<Entry<String, JsonElement>, K> parseKey,
                                          Function<Entry<String, JsonElement>, V> parseValue) {
        mapFromObjectProperties(GsonHelper.getAsJsonObject(json, propertyName), ret, parseKey, parseValue);
    }

    public static <K, V> Map<K, V> mapFromObjectArray(JsonArray jsonArray, String keyName, String valueName, Function<JsonElement, K> parseKey,
                                                      Function<JsonElement, V> parseValue) {
        Map<K, V> ret = new HashMap<>();
        for (JsonElement element : jsonArray) {
            JsonObject entry = GsonHelper.convertToJsonObject(element, "entry");
            ret.put(parseKey.apply(entry.get(keyName)), parseValue.apply(entry.get(valueName)));
        }
        return ret;
    }

    private static <K, V> Map<K, V> mapFromObjectProperties(JsonObject jsonObject, Map<K, V> ret, Function<Entry<String, JsonElement>, K> parseKey,
                                                            Function<Entry<String, JsonElement>, V> parseValue) {
        for (Map.Entry<String, JsonElement> pair : jsonObject.entrySet()) {
            ret.put(parseKey.apply(pair), parseValue.apply(pair));
        }
        return ret;
    }

    public static <V> Set<V> setFromJson(JsonElement element, Function<JsonElement, V> getElement) {
        return setFromJson(GsonHelper.convertToJsonArray(element, "set"), getElement);
    }

    private static <V> Set<V> setFromJson(JsonArray array, Function<JsonElement, V> getElement) {
        Set<V> ret = new HashSet<>();
        for (JsonElement element : array) {
            ret.add(getElement.apply(element));
        }
        return ret;
    }

    // ---- files ----

    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Writes the object as indented JSON, replacing the file and creating missing folders. */
    public static void saveJsonFile(JsonObject parent, Path file) {
        try {
            Path folder = file.toAbsolutePath().getParent();
            if (folder != null) {
                Files.createDirectories(folder);
            }
            try (BufferedWriter writer = Files.newBufferedWriter(file); JsonWriter jsonWriter = new JsonWriter(writer)) {
                jsonWriter.setIndent("    ");
                PRETTY_GSON.toJson(parent, jsonWriter);
            }
        } catch (IOException e) {
            LOGGER.error("Error saving Json file {}", file, e);
        }
    }

    private interface ItemStackCreator<R> {
        R instantiate(ItemStack stack, boolean ignoreComponents);
    }
}
