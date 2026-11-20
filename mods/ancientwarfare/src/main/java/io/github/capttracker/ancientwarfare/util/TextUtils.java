/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/util/TextUtils.
 */
package io.github.capttracker.ancientwarfare.util;

import net.minecraft.core.BlockPos;

import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

/** Text helpers. For wrapping text to a pixel width in a screen use {@code Font#split} instead. */
public final class TextUtils {
    private TextUtils() {}

    /** Word-wraps the text to lines of at most {@code maxLineLength} characters. */
    public static List<String> split(String text, int maxLineLength) {
        StringTokenizer tok = new StringTokenizer(text, " ");
        StringBuilder output = new StringBuilder(text.length());
        int lineLen = 0;
        while (tok.hasMoreTokens()) {
            String word = tok.nextToken();

            while (word.length() > maxLineLength) {
                output.append(word, 0, maxLineLength - lineLen).append("\n");
                word = word.substring(maxLineLength - lineLen);
                lineLen = 0;
            }

            if (lineLen + word.length() > maxLineLength) {
                output.append("\n");
                lineLen = 0;
            }
            output.append(word).append(" ");

            lineLen += word.length() + 1;
        }
        return Arrays.asList(output.toString().split("\n"));
    }

    public static String getSimpleBlockPosString(BlockPos pos) {
        return "x " + pos.getX() + " y " + pos.getY() + " z " + pos.getZ();
    }
}
