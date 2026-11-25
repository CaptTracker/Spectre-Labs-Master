/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/manual/ManualContentRegistry.
 */
package io.github.capttracker.ancientwarfare.manual;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.capttracker.ancientwarfare.registry.IRegistryDataParser;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The manual's content by category, in the player's language with English as the fallback. Filled by
 * {@link ManualContentParser} from {@code assets/ancientwarfare/registry/manual/<lang>/<category>.json} when the client
 * resources (re)load (language changes and F3+T reload them, which replaces the {@code /awutils reloadmanual} loop).
 *
 * <p>Client data only. The class has no client imports so the manual model can stay in the common source set;
 * {@code CoreScreensClient} installs the language lookup and registers the parser with
 * {@code RegistryLoader.registerClientParser(new ManualContentRegistry.ManualContentParser())}.
 */
public final class ManualContentRegistry {
    private static final Map<String, List<IContentElement>> ENGLISH_CATEGORY_CONTENTS = new HashMap<>();
    private static final Map<String, List<IContentElement>> CATEGORY_CONTENTS = new HashMap<>();
    private static Supplier<String> languageSupplier = () -> "en_us";

    private ManualContentRegistry() {}

    /** The code of the selected language, such as {@code en_us} (client: {@code LanguageManager#getSelected}). */
    public static void setLanguageSupplier(Supplier<String> supplier) {
        languageSupplier = supplier;
    }

    public static List<IContentElement> getCategoryContent(String category) {
        if (CATEGORY_CONTENTS.containsKey(category)) {
            return CATEGORY_CONTENTS.get(category);
        } else if (ENGLISH_CATEGORY_CONTENTS.containsKey(category)) {
            return ENGLISH_CATEGORY_CONTENTS.get(category);
        }
        return Collections.emptyList();
    }

    public static void clearContents() {
        ENGLISH_CATEGORY_CONTENTS.clear();
        CATEGORY_CONTENTS.clear();
    }

    public static class ManualContentParser implements IRegistryDataParser {
        private static final IContentElement EMPTY_ELEMENT = new IContentElement() {};

        @Override
        public String getName() {
            return "manual_content";
        }

        /** Reloads start from nothing (the 1.12.2 loader ran once, and the reload command cleared the tables itself). */
        @Override
        public void beginLoad() {
            clearContents();
        }

        @Override
        public void parse(JsonObject json) {
            String lang = GsonHelper.getAsString(json, "lang").toLowerCase(Locale.ROOT);
            String currentLang = languageSupplier.get().toLowerCase(Locale.ROOT);
            if (!lang.equals("en_us") && !lang.equals(currentLang)) {
                return;
            }
            String category = GsonHelper.getAsString(json, "category");
            List<IContentElement> contents = new ArrayList<>();
            if (lang.equals("en_us") && !currentLang.equals(lang)) {
                ENGLISH_CATEGORY_CONTENTS.put(category, contents);
            } else {
                CATEGORY_CONTENTS.put(category, contents);
            }

            JsonArray elements = GsonHelper.getAsJsonArray(json, "content");

            for (JsonElement el : elements) {
                JsonObject elementJson = GsonHelper.convertToJsonObject(el, "content entry");
                contents.add(parseElement(elementJson));
            }
        }

        private IContentElement parseElement(JsonObject elementJson) {
            String contentType = GsonHelper.getAsString(elementJson, "content_type");
            return switch (contentType) {
                case "text" -> TextElement.parse(elementJson);
                case "heading" -> HeadingElement.parse(elementJson);
                case "toc_list" -> TableOfContentsElement.parse(elementJson);
                case "image" -> ImageElement.parse(elementJson);
                case "item" -> ItemElement.parse(elementJson);
                default -> EMPTY_ELEMENT;
            };
        }
    }
}
