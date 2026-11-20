/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/block/Direction (renamed so it does not clash with net.minecraft.core.Direction).
 */
package io.github.capttracker.ancientwarfare.block;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * Translation keys for directions, for screens that show a direction by name. (The original marked itself
 * "TODO really needed or just replace with something like getFacingLocalization?"; it is kept because the
 * adjacent-inventory buttons of the research bench and the warehouse screens use it.)
 */
public enum AwDirection {
    DOWN("guistrings.inventory.direction.down"), UP("guistrings.inventory.direction.up"),
    NORTH("guistrings.inventory.direction.north"), SOUTH("guistrings.inventory.direction.south"),
    WEST("guistrings.inventory.direction.west"), EAST("guistrings.inventory.direction.east"),
    UNKNOWN("guistrings.inventory.direction.unknown");

    private final String translationKey;

    AwDirection(String key) {
        translationKey = key;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public Component getDisplayName() {
        return Component.translatable(translationKey);
    }

    public static AwDirection getDirectionFor(@Nullable Direction direction) {
        return direction == null ? UNKNOWN : values()[direction.get3DDataValue()];
    }
}
