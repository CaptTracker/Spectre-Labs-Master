/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the Render subclasses of structure/render/gate (as plain classes; 1.21 cannot build an
 * entity Render outside the entity pipeline).
 */
package io.github.capttracker.ancientwarfarestructure.client.render.gate;

import io.github.capttracker.ancientwarfarestructure.entity.EntityGate;

/** Draws one kind of gate. The pose stack of the draw is at the gate's position and already flipped by scale(-1, -1, 1). */
public interface GateModelRenderer {
    void render(EntityGate gate, float partialTick, GateDraw draw);
}
