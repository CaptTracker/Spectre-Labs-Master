/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/manual/HeadingElement.
 */
package io.github.capttracker.ancientwarfare.manual;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/** A heading. Level 1 is drawn bold, any other level underlined. */
public class HeadingElement implements IContentElement {
    private final String text;
    private final int level;

    private HeadingElement(String text, int level) {
        this.text = text;
        this.level = level;
    }

    public String getText() {
        return text;
    }

    public int getLevel() {
        return level;
    }

    public static HeadingElement parse(JsonObject elementJson) {
        return new HeadingElement(GsonHelper.getAsString(elementJson, "text"), GsonHelper.getAsInt(elementJson, "level"));
    }
}
