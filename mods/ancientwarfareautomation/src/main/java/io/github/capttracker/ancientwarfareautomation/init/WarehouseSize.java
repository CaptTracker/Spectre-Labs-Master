/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/block/BlockWarehouseStorage.Size (the metadata of the warehouse storage block).
 */
package io.github.capttracker.ancientwarfareautomation.init;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * The three sizes of the warehouse storage block. In 1.12.2 the size was the block's metadata; every size is its
 * own block now ({@code warehouse_storage_small}, {@code warehouse_storage_medium}, {@code warehouse_storage_large}),
 * created with the size by {@link AwAutomationBase}. Lives in the init package because the master registration
 * list needs it before the block classes exist.
 */
public enum WarehouseSize implements StringRepresentable {
    SMALL, MEDIUM, LARGE;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The 1.12.2 metadata value of the size. */
    public int getMeta() {
        return ordinal();
    }

    public static WarehouseSize byMetadata(int meta) {
        return values()[meta];
    }
}
