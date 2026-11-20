/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/TextureData.
 */
package io.github.capttracker.ancientwarfare.client.gui;

import net.minecraft.resources.ResourceLocation;

/** A texture plus the region of it a widget draws (sizes in texture pixels). */
public class TextureData {
    private final ResourceLocation texture;
    private final int textureU;
    private final int textureV;
    private final int textureWidth;
    private final int textureHeight;
    private final int partWidth;
    private final int partHeight;

    public TextureData(ResourceLocation texture, int textureWidth, int textureHeight, int textureU, int textureV, int partWidth, int partHeight) {
        this.texture = texture;
        this.textureU = textureU;
        this.textureV = textureV;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.partWidth = partWidth;
        this.partHeight = partHeight;
    }

    public int getTextureWidth() {
        return textureWidth;
    }

    public ResourceLocation getTexture() {
        return texture;
    }

    public int getTextureU() {
        return textureU;
    }

    public int getTextureV() {
        return textureV;
    }

    public int getTextureHeight() {
        return textureHeight;
    }

    public int getPartWidth() {
        return partWidth;
    }

    public int getPartHeight() {
        return partHeight;
    }
}
