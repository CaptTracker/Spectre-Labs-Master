/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/compat/CompatLoader.
 */
package io.github.capttracker.ancientwarfare.compat;

import net.neoforged.fml.ModList;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Registry of the soft-dependency hooks. Register during mod construction, {@link #init()} runs once in
 * common setup ({@code AwCoreMisc} does that).
 */
public final class CompatLoader {
    private static final Set<ICompat> COMPATS = new CopyOnWriteArraySet<>();

    private CompatLoader() {}

    public static void registerCompat(ICompat compat) {
        COMPATS.add(compat);
    }

    public static void init() {
        for (ICompat compat : COMPATS) {
            if (ModList.get().isLoaded(compat.getModId())) {
                compat.init();
            }
        }
    }
}
