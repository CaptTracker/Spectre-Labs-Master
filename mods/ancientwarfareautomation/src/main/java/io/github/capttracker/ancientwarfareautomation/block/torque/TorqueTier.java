/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/block/TorqueTier (and the three per-tier getEfficiency/getMaxTransfer
 * overrides of the tiered tile classes).
 */
package io.github.capttracker.ancientwarfareautomation.block.torque;

import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * The three quality levels of the torque blocks. In 1.12.2 this was the block metadata (and the block-state property
 * {@code tier}); in 1.21.1 every tier is a block of its own ({@code torque_shaft_light}, {@code torque_shaft_medium},
 * {@code torque_shaft_heavy}, ...), so the tier is a property of the block class instance, not of the block state.
 *
 * <p>The numbers (efficiency multiplier per tick, transfer per tick, flywheel energy per block) are read from
 * {@link AWAutomationStatics} when asked, as the original tile classes did.
 */
public enum TorqueTier implements StringRepresentable {
    LIGHT(0), MEDIUM(1), HEAVY(2);

    private final int meta;

    TorqueTier(int meta) {
        this.meta = meta;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The lower case name, which is also the suffix of the registry names ({@code torque_shaft_light}). */
    public String getName() {
        return getSerializedName();
    }

    /** The 1.12.2 metadata value of the tier (for migration tools only). */
    public int getMeta() {
        return meta;
    }

    public static TorqueTier byMetadata(int meta) {
        return values()[Math.floorMod(meta, values().length)];
    }

    /** Multiplier applied to stored energy every tick (lower means more loss). */
    public double getEfficiency() {
        return switch (this) {
            case LIGHT -> AWAutomationStatics.low_efficiency_factor;
            case MEDIUM -> AWAutomationStatics.med_efficiency_factor;
            case HEAVY -> AWAutomationStatics.high_efficiency_factor;
        };
    }

    /** Energy per tick a cell of this tier accepts and releases; also the size of the cell. */
    public double getMaxTransfer() {
        return switch (this) {
            case LIGHT -> AWAutomationStatics.low_transfer_max;
            case MEDIUM -> AWAutomationStatics.med_transfer_max;
            case HEAVY -> AWAutomationStatics.high_transfer_max;
        };
    }

    /** Energy a single flywheel storage block of this tier holds. */
    public double getStorageEnergyMax() {
        return switch (this) {
            case LIGHT -> AWAutomationStatics.low_storage_energy_max;
            case MEDIUM -> AWAutomationStatics.med_storage_energy_max;
            case HEAVY -> AWAutomationStatics.high_storage_energy_max;
        };
    }
}
