/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/manual/TableOfContentsElement.
 */
package io.github.capttracker.ancientwarfare.manual;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;

/** A list of links to other categories of the manual. */
public class TableOfContentsElement implements IContentElement {
    private final List<TableOfContentsItem> items;

    public TableOfContentsElement(List<TableOfContentsItem> contentItems) {
        items = contentItems;
    }

    public static TableOfContentsElement parse(JsonObject elementJson) {
        JsonArray contents = GsonHelper.getAsJsonArray(elementJson, "items");

        List<TableOfContentsItem> tocItems = new ArrayList<>();
        for (JsonElement e : contents) {
            JsonObject contentItem = GsonHelper.convertToJsonObject(e, "items entry");
            tocItems.add(new TableOfContentsItem(GsonHelper.getAsString(contentItem, "text"), GsonHelper.getAsString(contentItem, "category_link")));
        }

        return new TableOfContentsElement(tocItems);
    }

    public List<TableOfContentsItem> getItems() {
        return items;
    }

    public static class TableOfContentsItem {
        private final String text;
        private final String category;

        private TableOfContentsItem(String text, String category) {
            this.text = text;
            this.category = category;
        }

        public String getText() {
            return text;
        }

        public String getCategory() {
            return category;
        }
    }
}
