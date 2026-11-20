/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: the part of structure/entity/EntityGate that the battering ram and the path finder use.
 */
package io.github.capttracker.ancientwarfarevehicle.entity;

/** A gate entity of the Structures module, as far as the vehicles are concerned. */
public interface IGateEntity {
    /** 0 when the gate is closed, above 0 when it has moved (opened); {@code edgePosition} of the original. */
    float getEdgePosition();

    /** Opens or closes the gate, whichever it is not now. */
    void activateGate();

    /** Whether soldiers may operate this gate type ({@code getGateType().canSoldierActivate()}). */
    boolean canSoldierActivate();

    /** The lower-case name of the gate variant; the ram plays the wooden or the iron hit sound by whether it contains "wood" or "iron". */
    String getGateVariantName();
}
