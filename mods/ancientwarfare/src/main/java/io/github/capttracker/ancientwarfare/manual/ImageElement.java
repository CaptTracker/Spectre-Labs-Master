/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/manual/ImageElement.
 */
package io.github.capttracker.ancientwarfare.manual;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * An image of the manual. {@code path} is the path from the manual file ({@code /images/Ballista1.jpg}); the screen
 * maps it to a texture under {@code textures/gui/manual/} (lower case, always .png), see {@code ImageElementWrapper}.
 * {@code width} and {@code height} are the size of the image in pixels.
 */
public class ImageElement implements IContentElement {
    private final String path;
    private final int width;
    private final int height;

    private ImageElement(String path, int width, int height) {
        this.path = path;
        this.width = width;
        this.height = height;
    }

    public static ImageElement parse(JsonObject elementJson) {
        return new ImageElement(GsonHelper.getAsString(elementJson, "path"), GsonHelper.getAsInt(elementJson, "width"),
                GsonHelper.getAsInt(elementJson, "height"));
    }

    public String getPath() {
        return path;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
