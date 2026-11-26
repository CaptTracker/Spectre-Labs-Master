/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): building designs.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

import java.util.Locale;

/** The look of a design. {@link #ANY} fits every settlement. The JSON value is the lower case name. */
public enum DesignStyle {
    TIMBER, STONE, DESERT, NORSE, ANY;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "design.ancientwarfarenpc.style." + id();
    }

    /** The style with this id, or {@code null} when unknown. */
    public static DesignStyle byId(String id) {
        if (id != null) {
            for (DesignStyle value : values()) {
                if (value.id().equals(id.trim().toLowerCase(Locale.ROOT))) {
                    return value;
                }
            }
        }
        return null;
    }

    public static DesignStyle byOrdinal(int ordinal) {
        DesignStyle[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : ANY;
    }
}
