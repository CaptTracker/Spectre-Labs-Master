/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Replaces Ancient Warfare 2's core/util/RenderTools (the GUI part) and the immediate-mode GL the widgets used.
 */
package io.github.capttracker.ancientwarfare.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.awt.Color;

/**
 * Small drawing and text helpers shared by the widget toolkit. Everything draws through
 * {@link GuiGraphics}; nothing touches GL state directly.
 */
public final class GuiRender {
    /** The vanilla "uniform" (unicode) font, which stands in for the original's private unicode FontRenderer. */
    private static final Style UNICODE_STYLE = Style.EMPTY.withFont(Minecraft.UNIFORM_FONT);

    private GuiRender() {}

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    /**
     * Looks a lang key up. Text that is not a key comes back unchanged. Unlike {@code I18n.get} no
     * formatting is applied, so literal text containing '%' is safe (the original passed every label
     * through the translator, including text that was already translated).
     */
    public static String translate(String keyOrText) {
        if (keyOrText == null) {
            return "";
        }
        return Language.getInstance().getOrDefault(keyOrText);
    }

    public static boolean hasTranslation(String key) {
        return Language.getInstance().has(key);
    }

    public static void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ---- colour ----

    /** The original widgets pass colours as 0xRRGGBBAA; GuiGraphics wants 0xAARRGGBB. */
    public static int rgbaToArgb(int rgba) {
        return ((rgba & 0xFF) << 24) | (rgba >>> 8);
    }

    public static void fillRgba(GuiGraphics graphics, int x1, int y1, int x2, int y2, int rgba) {
        graphics.fill(x1, y1, x2, y2, rgbaToArgb(rgba));
    }

    /** Multiplies everything drawn next by the colour. Call {@link #resetColor} afterwards. */
    public static void tint(GuiGraphics graphics, Color color) {
        graphics.setColor(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, color.getAlpha() / 255f);
    }

    public static void resetColor(GuiGraphics graphics) {
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    // ---- textures ----

    /**
     * Port of RenderTools.renderQuarteredTexture: draws a texture region at any size by taking the
     * four corners from the region 1:1 and letting them meet in the middle. A render size smaller than
     * the region shows the corners of the region; a larger one stretches them.
     *
     * @param texW   texture width in pixels
     * @param texH   texture height in pixels
     * @param u      region start U
     * @param v      region start V
     * @param usedW  region width
     * @param usedH  region height
     */
    public static void quartered(GuiGraphics graphics, ResourceLocation texture, int texW, int texH, int u, int v, int usedW, int usedH,
                                 int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int leftW = w / 2;
        int rightW = w - leftW;
        int topH = h / 2;
        int botH = h - topH;
        int srcLeftW = Math.min(leftW, usedW);
        int srcRightW = Math.min(rightW, usedW);
        int srcTopH = Math.min(topH, usedH);
        int srcBotH = Math.min(botH, usedH);
        int rightU = u + usedW - srcRightW;
        int botV = v + usedH - srcBotH;
        part(graphics, texture, x, y, leftW, topH, u, v, srcLeftW, srcTopH, texW, texH);
        part(graphics, texture, x + leftW, y, rightW, topH, rightU, v, srcRightW, srcTopH, texW, texH);
        part(graphics, texture, x, y + topH, leftW, botH, u, botV, srcLeftW, srcBotH, texW, texH);
        part(graphics, texture, x + leftW, y + topH, rightW, botH, rightU, botV, srcRightW, srcBotH, texW, texH);
    }

    /** Draws a texture region stretched over a rectangle (RenderTools.renderTexturedQuad with pixel coordinates). */
    public static void region(GuiGraphics graphics, ResourceLocation texture, int texW, int texH, int u, int v, int usedW, int usedH,
                              int x, int y, int w, int h) {
        part(graphics, texture, x, y, w, h, u, v, usedW, usedH, texW, texH);
    }

    private static void part(GuiGraphics graphics, ResourceLocation texture, int x, int y, int dw, int dh, int u, int v, int sw, int sh, int texW, int texH) {
        if (dw <= 0 || dh <= 0 || sw <= 0 || sh <= 0) {
            return;
        }
        graphics.blit(texture, x, y, dw, dh, (float) u, (float) v, sw, sh, texW, texH);
    }

    // ---- unicode font (replaces ClientProxy.getUnicodeFontRenderer) ----

    public static Component unicode(String text) {
        return Component.literal(text == null ? "" : text).withStyle(UNICODE_STYLE);
    }

    public static int unicodeWidth(String text) {
        return font().width(unicode(text));
    }

    /** Height of one line in the unicode font (FONT_HEIGHT of the original renderer). */
    public static int unicodeLineHeight() {
        return font().lineHeight;
    }

    /** Height of the text once wrapped to {@code width} pixels. */
    public static int unicodeWrappedHeight(String text, int width) {
        return font().lineHeight * font().split(unicode(text), width).size();
    }

    /** Draws wrapped text without shadow (the original's drawSplitString). */
    public static void drawUnicodeWrapped(GuiGraphics graphics, String text, int x, int y, int width, int color) {
        graphics.drawWordWrap(font(), unicode(text), x, y, width, color);
    }
}
