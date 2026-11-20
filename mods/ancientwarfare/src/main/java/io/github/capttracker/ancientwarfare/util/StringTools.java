/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/util/StringTools.
 */
package io.github.capttracker.ancientwarfare.util;

import net.minecraft.core.BlockPos;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.StringJoiner;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

/**
 * Parsing helpers for the old "key:value,value" text formats. Not ported: {@code writeString} and
 * {@code readString} (use {@code FriendlyByteBuf#writeUtf} / {@code readUtf}).
 */
public final class StringTools {
    private StringTools() {}

    public static String getCSVStringForArray(int[] values) {
        return Arrays.stream(values).mapToObj(Integer::toString).collect(Collectors.joining(","));
    }

    public static String getCSVValueFor(Set<String> values) {
        return String.join(",", values);
    }

    /** Splits test at regex and returns the int array parsed from the csv value of the remaining string. */
    public static int[] safeParseIntArray(String regex, String test) {
        String[] splits = test.split(regex);
        if (splits.length > 1) {
            return parseIntArray(splits[1]);
        }
        return new int[0];
    }

    public static int[] parseIntArray(String csv) {
        if (csv.trim().isEmpty()) {
            return new int[0];
        }
        String[] splits = csv.split(",");
        int[] array = new int[splits.length];
        for (int i = 0; i < splits.length; i++) {
            array[i] = Integer.parseInt(splits[i].trim());
        }
        return array;
    }

    public static short[] parseShortArray(String csv) {
        String[] splits = csv.split(",");
        short[] array = new short[splits.length];
        for (int i = 0; i < splits.length; i++) {
            array[i] = Short.parseShort(splits[i].trim());
        }
        return array;
    }

    /** The boolean after a split at regex, or false. */
    public static boolean safeParseBoolean(String regex, String test) {
        String[] split = test.split(regex);
        return split.length > 1 && Boolean.parseBoolean(split[1].trim());
    }

    public static float safeParseFloat(String regex, String test) {
        String[] split = test.trim().split(regex);
        if (split.length > 1) {
            return safeParseFloat(split[1]);
        }
        return 0;
    }

    public static float safeParseFloat(String val) {
        try {
            return Float.parseFloat(val.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The value after a split at regex, or an empty string. */
    public static String safeParseString(String regex, String test) {
        String[] split = test.split(regex);
        if (split.length > 1) {
            return split[1];
        }
        return "";
    }

    /** The value after a split at regex, or zero. */
    public static int safeParseInt(String regex, String test) {
        String[] split = test.split(regex);
        if (split.length > 1) {
            return Integer.parseInt(split[1].trim());
        }
        return 0;
    }

    public static String formatPos(BlockPos pos) {
        return String.format("X:%d Y:%d Z:%d", pos.getX(), pos.getY(), pos.getZ());
    }

    public static Set<String> parseStringSet(String valueString) {
        return java.util.Collections.list(new StringTokenizer(valueString, ",")).stream()
                .map(token -> (String) token).collect(Collectors.toSet());
    }

    public static String joinElements(String delimiter, Collection<String> stringCollection) {
        StringJoiner joiner = new StringJoiner(delimiter);
        stringCollection.forEach(joiner::add);
        return joiner.toString();
    }
}
