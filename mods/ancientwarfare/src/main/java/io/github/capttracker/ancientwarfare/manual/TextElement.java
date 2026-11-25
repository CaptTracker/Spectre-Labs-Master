/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/manual/TextElement.
 */
package io.github.capttracker.ancientwarfare.manual;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/** A paragraph. {@code \n} in the text starts a new line. */
public class TextElement implements IContentElement {
    private final String text;

    public TextElement(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public static TextElement parse(JsonObject elementJson) {
        return new TextElement(GsonHelper.getAsString(elementJson, "text"));
    }
}
