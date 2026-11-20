/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/util/CompatUtils.
 */
package io.github.capttracker.ancientwarfare.util;

import net.neoforged.fml.ModList;

/** Mod presence checks; {@code ModList.get().isLoaded(modId)} for a single mod. */
public final class CompatUtils {
    private CompatUtils() {}

    /** True if every non-empty mod id in the array is loaded. */
    public static boolean areModsLoaded(String[] mods) {
        for (String mod : mods) {
            if (!mod.isEmpty() && !ModList.get().isLoaded(mod)) {
                return false;
            }
        }
        return true;
    }
}
