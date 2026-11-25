/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/input/IInputCallback.
 */
package io.github.capttracker.ancientwarfare.input;

/** Called on the client when the key mapping it was registered for is pressed. */
@FunctionalInterface
public interface IInputCallback {
    void onKeyPressed();
}
