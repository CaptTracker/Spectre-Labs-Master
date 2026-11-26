/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): building designs.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

import java.util.Locale;

/** What a design is for. The planner picks designs by category. The JSON value is the lower case name. */
public enum DesignCategory {
    TENT, HOUSE, STOREHOUSE, BUSINESS, CHAPEL, OTHER;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "design.ancientwarfarenpc.category." + id();
    }

    /** The category with this id, or {@code null} when unknown. */
    public static DesignCategory byId(String id) {
        if (id != null) {
            for (DesignCategory value : values()) {
                if (value.id().equals(id.trim().toLowerCase(Locale.ROOT))) {
                    return value;
                }
            }
        }
        return null;
    }

    public static DesignCategory byOrdinal(int ordinal) {
        DesignCategory[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : OTHER;
    }
}
