/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: structure/block/BlockGateProxy (the collision rules that depended on the owner entity
 * and on the reflective bounding box swap of structure/gates/types/Gate; they are a block-state property now).
 */
package io.github.capttracker.ancientwarfarestructure.block;

import net.minecraft.util.StringRepresentable;

/**
 * What a gate proxy block collides as. The gate entity sets it on the proxies when it starts to open, when it has closed
 * and (drawbridge) when it has swung down, so that collision is the same on the client and the server and needs no
 * block entity lookup.
 *
 * <p>1.12.2 equivalents: {@link #CLOSED} was the thin plane through the middle of the block (the original box had no
 * thickness, which 1.21 shapes cannot have, so it is 2 pixels thick like the gate model); {@link #OPEN} was
 * {@code isOpen} (no collision); {@link #LINTEL} was the 0.5 high box that the gate entity kept at the top of its volume
 * while it was open; {@link #FLOOR} was the full-block box of an open drawbridge.
 */
public enum GateProxyMode implements StringRepresentable {
    /** The gate is shut: a thin wall across the block. */
    CLOSED("closed"),
    /** The gate is opening, open or closing: nothing collides. */
    OPEN("open"),
    /** Top row of an opening, open or closing sliding or lifting gate: the upper half of the block collides. */
    LINTEL("lintel"),
    /** A cell of the landing area of a lowered drawbridge: a full block. */
    FLOOR("floor");

    private final String name;

    GateProxyMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
