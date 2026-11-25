/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the InventoryCrafting inside core/tile/CraftingRecipeMemory.
 */
package io.github.capttracker.ancientwarfare.crafting;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.CraftingContainer;

/**
 * The 3x3 recipe grid of a bench: a plain container that also is a {@link CraftingContainer}. It is the template the
 * player lays out; the ingredients themselves come from the bench's storage. Contents are kept by the owning block
 * entity, which listens for changes ({@code addListener}).
 */
public class CraftMatrix extends SimpleContainer implements CraftingContainer {
    public static final int SIZE = 9;

    public CraftMatrix() {
        super(SIZE);
    }

    @Override
    public int getWidth() {
        return 3;
    }

    @Override
    public int getHeight() {
        return 3;
    }
}
