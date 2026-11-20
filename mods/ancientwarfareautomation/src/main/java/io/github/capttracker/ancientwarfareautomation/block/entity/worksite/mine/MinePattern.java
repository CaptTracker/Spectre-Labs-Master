/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 3, mine styles).
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.mine;

import net.minecraft.nbt.CompoundTag;

import java.util.List;

/**
 * A mine style: the order in which a quarry digs and what it places on the way. It keeps its own saved cursor. A pattern never changes the world; it
 * plans {@link MineOp}s from the world as it is (so a restart or a reset loses nothing), and is told through {@link #completed} when one was done.
 */
public interface MinePattern {
    MineMode mode();

    /** Starts over from the first position (the area, the Y or an upgrade changed). */
    void reset(MineSite site);

    /** True when there is nothing left to dig. */
    boolean isFinished();

    /**
     * Advances the cursor past finished work and adds the operations that can be done now to {@code out}: most urgent first, at most {@code limit}.
     */
    void plan(MineSite site, List<MineOp> out, int limit);

    /** A worker did the operation (the block is already changed). */
    default void completed(MineSite site, MineOp op) {
    }

    void save(CompoundTag tag);

    void load(CompoundTag tag);
}
