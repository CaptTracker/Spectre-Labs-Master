/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/manual/elements/ImageElementWrapper.
 */
package io.github.capttracker.ancientwarfare.client.screen.manual.elements;

import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.ancientwarfare.client.screen.manual.GuiManual;
import io.github.capttracker.ancientwarfare.client.screen.manual.IElementWrapperCreator;
import io.github.capttracker.ancientwarfare.manual.ImageElement;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/** An image of the manual, scaled down to the page width if it is wider. */
public class ImageElementWrapper extends BaseElementWrapper<ImageElement> {
    private static final int BOTTOM_PADDING = 4;
    private final ResourceLocation texture;

    public ImageElementWrapper(GuiManual gui, int topLeftY, int width, int height, ImageElement element) {
        super(gui, 0, topLeftY, width, height, element);
        texture = textureOf(element.getPath());
    }

    /**
     * The files name images like {@code /images/Ballista1.jpg} (any case, jpg or png). They are converted to lower case
     * png files under {@code textures/gui/manual/}, because 1.21.1 only reads PNG and requires lower case resource paths.
     */
    static ResourceLocation textureOf(String path) {
        String clean = path.replaceFirst("^/", "").toLowerCase(Locale.ROOT).replaceFirst("\\.(jpg|jpeg|png)$", "");
        return Ids.of(AncientWarfareCore.MOD_ID, "textures/gui/manual/" + clean + ".png");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int textureWidth = getElement().getWidth();
        int textureHeight = getElement().getHeight();
        float scale = getScale(width, textureWidth);
        int scaledWidth = (int) (scale * textureWidth);
        // 1.12.2 centred with min(0, ...), which always came out as 0: the image is drawn at the left edge.
        graphics.blit(texture, renderX, renderY, scaledWidth, (int) (textureHeight * scale), 0f, 0f, textureWidth, textureHeight,
                textureWidth, textureHeight);
    }

    private static float getScale(int width, int textureWidth) {
        return width < textureWidth ? (float) width / textureWidth : 1.0f;
    }

    public static class Creator implements IElementWrapperCreator<ImageElement> {
        @Override
        public List<BaseElementWrapper<?>> construct(GuiManual gui, int topLeftY, int width, int remainingPageHeight, int emptyPageHeight, ImageElement element) {
            int scaledHeight = (int) (getScale(width, element.getWidth()) * element.getHeight());
            return List.of(new ImageElementWrapper(gui, remainingPageHeight < scaledHeight ? 0 : topLeftY, width, scaledHeight + BOTTOM_PADDING, element));
        }
    }
}
