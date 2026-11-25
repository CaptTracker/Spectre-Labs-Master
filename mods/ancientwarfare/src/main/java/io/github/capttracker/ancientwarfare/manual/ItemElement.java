/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/manual/ItemElement.
 */
package io.github.capttracker.ancientwarfare.manual;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfare.util.parsing.JsonHelper;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One row of items shown in the manual. The item names are resolved the first time the screen asks for the stacks,
 * not when the file is read: the manual names items of the other Ancient Warfare modules, which may not be
 * registered (or not loaded at all), and such entries are skipped.
 *
 * <p>The files still carry the 1.12.2 {@code data} (metadata) value. It no longer selects anything. Items that
 * were one registry name with several metadata values (the worksite upgrades, the vehicle upgrades) are now one item
 * per variant, so the module that registers the variants maps the old value with
 * {@link #registerLegacyVariant}; without a mapping the value is dropped and the plain name is looked up.
 */
public class ItemElement implements IContentElement {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, String> LEGACY_VARIANTS = new HashMap<>();

    private final List<JsonElement> sources;
    private ItemStack[] itemStacks;

    private ItemElement(List<JsonElement> sources) {
        this.sources = sources;
    }

    /** Maps a 1.12.2 name plus metadata value used by the manual files to the registry name of the item that replaced it. */
    public static void registerLegacyVariant(String legacyName, int data, String itemName) {
        LEGACY_VARIANTS.put(legacyName + "#" + data, itemName);
    }

    /** The stacks to show; entries that cannot be resolved are left out. */
    public ItemStack[] getItemStacks() {
        if (itemStacks == null) {
            List<ItemStack> stacks = new ArrayList<>();
            for (JsonElement source : sources) {
                try {
                    ItemStack stack = JsonHelper.getItemStack(withoutLegacyData(source));
                    if (!stack.isEmpty()) {
                        stacks.add(stack);
                    }
                } catch (RuntimeException e) {
                    LOGGER.debug("Manual item {} skipped: {}", source, e.getMessage());
                }
            }
            itemStacks = stacks.toArray(new ItemStack[0]);
        }
        return itemStacks;
    }

    private static JsonElement withoutLegacyData(JsonElement source) {
        if (!source.isJsonObject() || !source.getAsJsonObject().has("data")) {
            return source;
        }
        JsonObject copy = source.getAsJsonObject().deepCopy();
        JsonElement data = copy.remove("data");
        String mapped = LEGACY_VARIANTS.get(GsonHelper.getAsString(copy, "name") + "#" + data.getAsInt());
        if (mapped != null) {
            copy.addProperty("name", mapped);
        }
        return copy;
    }

    public static IContentElement parse(JsonObject elementJson) {
        List<JsonElement> sources = new ArrayList<>();
        if (elementJson.has("items")) {
            JsonArray items = GsonHelper.getAsJsonArray(elementJson, "items");
            items.forEach(sources::add);
        } else {
            sources.add(elementJson);
        }
        return new ItemElement(sources);
    }
}
