/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/upgrade/WorksiteUpgrade.
 */
package io.github.capttracker.ancientwarfare.upgrade;

import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The upgrades a worksite can have. Each upgrade has a set of upgrades it excludes (it cannot be applied while
 * one of them is present) and a set it overrides (applying it removes them).
 *
 * <p>The 1.12.2 enum was saved by ordinal ("DO NOT EVER CHANGE ENUM ORDERING"). Saving is by {@link #getSerializedName()}
 * now, so the order only matters for the exclusive/override tables below, which are written as ordinals.
 */
public enum WorksiteUpgrade implements StringRepresentable {
    SIZE_MEDIUM(new int[]{}, new int[]{}),
    SIZE_LARGE(new int[]{0}, new int[]{0}),
    QUARRY_MEDIUM(new int[]{}, new int[]{}),
    QUARRY_LARGE(new int[]{2}, new int[]{2}),
    ENCHANTED_TOOLS_1(new int[]{}, new int[]{}),
    ENCHANTED_TOOLS_2(new int[]{4}, new int[]{4}),
    TOOL_QUALITY_1(new int[]{}, new int[]{}),
    TOOL_QUALITY_2(new int[]{6}, new int[]{6}),
    TOOL_QUALITY_3(new int[]{6, 7}, new int[]{6, 7}),
    BASIC_CHUNK_LOADER(new int[]{}, new int[]{}),
    QUARRY_CHUNK_LOADER(new int[]{}, new int[]{});

    public static final Codec<WorksiteUpgrade> CODEC = StringRepresentable.fromEnum(WorksiteUpgrade::values);
    public static final StreamCodec<io.netty.buffer.ByteBuf, WorksiteUpgrade> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[i], WorksiteUpgrade::ordinal);

    private final Set<Integer> exclusive;
    private final Set<Integer> overrides;

    WorksiteUpgrade(int[] exc, int[] ovr) {
        this.exclusive = new HashSet<>(exc.length);
        this.overrides = new HashSet<>(ovr.length);
        for (int anExc : exc) {
            exclusive.add(anExc);
        }
        for (int anOvr : ovr) {
            overrides.add(anOvr);
        }
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The translation key of this upgrade, for a worksite-upgrade list. 1.12.2 built it from the upgrade item's name
     * and the ordinal ({@code item.worksite_upgrade.<ordinal>.name}); it is {@code item.ancientwarfare.worksite_upgrade.<name>} now.
     */
    public String unlocalizedName() {
        return "item.ancientwarfare.worksite_upgrade." + getSerializedName();
    }

    /** True if THIS should override INPUT (input will be removed, this will be applied). */
    public boolean overrides(WorksiteUpgrade ug) {
        return overrides.contains(ug.ordinal());
    }

    /** True if THIS prevents the INPUT from being applied (input will not be applied, no change to upgrades). */
    public boolean exclusive(WorksiteUpgrade ug) {
        return exclusive.contains(ug.ordinal());
    }

    /** The upgrade with the saved name, or null. */
    @Nullable
    public static WorksiteUpgrade byName(String name) {
        for (WorksiteUpgrade upgrade : values()) {
            if (upgrade.getSerializedName().equals(name)) {
                return upgrade;
            }
        }
        return null;
    }
}
