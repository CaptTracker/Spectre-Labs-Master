/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 3, mine styles).
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.mine;

/** The mine styles of the quarry. The ordinal is what the screen and the save file use; the key is the lang key of the mode's name. */
public enum MineMode {
    OPEN_QUARRY("open_quarry"), SHAFT("shaft"), BRANCH("branch"), STRIP("strip");

    private final String id;

    MineMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String langKey() {
        return "guistrings.automation.mine_mode." + id;
    }

    /** True if the mode digs at a chosen Y level (the open quarry digs from the top of its area down to its height). */
    public boolean usesY() {
        return this != OPEN_QUARRY;
    }

    public static MineMode byOrdinal(int ordinal) {
        MineMode[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : OPEN_QUARRY;
    }

    public static MineMode byId(String id) {
        for (MineMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        return OPEN_QUARRY;
    }
}
