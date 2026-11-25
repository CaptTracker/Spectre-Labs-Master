/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: structure/block/BlockFirePit.Variant (DEFAULT, LOG, SQUARE).
 */
package io.github.capttracker.ancientwarfarestructure.decor;

import java.util.Locale;

/**
 * The three fire pit variants. In 1.12.2 they were one block with a {@code variant} property and item NBT; each is
 * its own block now (registry names {@code fire_pit}, {@code fire_pit_log}, {@code fire_pit_square}). The material
 * of the original decides the block properties: DEFAULT and SQUARE were rock (pickaxe, stone sound), LOG was wood.
 */
public enum FirePitVariant {
    DEFAULT("fire_pit", false),
    LOG("fire_pit_log", true),
    SQUARE("fire_pit_square", false);

    private final String blockName;
    private final boolean wood;

    FirePitVariant(String blockName, boolean wood) {
        this.blockName = blockName;
        this.wood = wood;
    }

    /** The registry name of this variant's block and item. */
    public String blockName() {
        return blockName;
    }

    /** True for the wood variant (1.12.2 Material.WOOD), false for the rock variants (Material.ROCK). */
    public boolean isWood() {
        return wood;
    }

    /** The 1.12.2 {@code variant} NBT value. */
    public String legacyName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
