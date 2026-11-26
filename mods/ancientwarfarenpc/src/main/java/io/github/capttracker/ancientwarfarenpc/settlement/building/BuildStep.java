/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): the build job API.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One thing a builder does on a {@link BuildProject}: place a block, clear a cell, fill ground, or (when taking a building down) remove a block.
 * {@code index} is the place of the step in the order of the job and the bit used by {@link BuildProject}. {@code state} is the block to place
 * (air for {@link Kind#CLEAR}) or, for {@link Kind#REMOVE}, the block that the design put there (what the builder takes down).
 */
public record BuildStep(int index, Kind kind, BlockPos pos, BlockState state) {
    public enum Kind {
        /** Earth under the building, to level the ground (comes before the design's blocks). */
        FILL,
        /** A block of the design. */
        PLACE,
        /** A cell of the design that must be air (the block that is there is removed). */
        CLEAR,
        /** Taking a building down: the block is removed and its item goes back to the storage. */
        REMOVE
    }

    /** True if the step puts a block into the world (needs material from the storage). */
    public boolean places() {
        return kind == Kind.PLACE || kind == Kind.FILL;
    }
}
