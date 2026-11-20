/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/compat/ICompat.
 */
package io.github.capttracker.ancientwarfare.compat;

/** A soft dependency hook: {@link #init()} runs only if the mod with {@link #getModId()} is installed. */
public interface ICompat {
    String getModId();

    void init();
}
