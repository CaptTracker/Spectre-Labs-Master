/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 3): one unit of work that a worksite hands to a worker.
 */
package io.github.capttracker.ancientwarfare.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One thing to do at one target: a position, an action and the base duration in ticks (what a worker of skill 1 with no tool bonus and no hunger needs).
 * Equality is by value, so a worksite finds a task again by the record the worker holds.
 *
 * @param pos       the target block (the block that is acted on: the crop, the soil to till, the trunk)
 * @param action    what to do
 * @param baseTicks the duration at speed 1, at least 1
 */
public record WorkTask(BlockPos pos, WorkAction action, int baseTicks) {
    /** The shortest duration of a task. */
    public static final int MIN_TICKS = 10;
    /** The duration of a task that does not break anything, as tilling, planting or fertilising. */
    public static final int SOFT_TICKS = 20;

    public WorkTask {
        pos = pos.immutable();
        baseTicks = Math.max(1, baseTicks);
    }

    /** A task with the default duration of its kind: breaking actions use the hardness of the block, the others {@link #SOFT_TICKS}. */
    public static WorkTask of(BlockGetter level, BlockPos pos, WorkAction action) {
        return new WorkTask(pos, action, action.isBreaking() ? breakTicks(level.getBlockState(pos), level, pos) : SOFT_TICKS);
    }

    /** Vanilla's rule of thumb for breaking with a fitting tool at speed 1: 30 ticks per unit of hardness (hardness 1.5 stone: 45), at least {@link #MIN_TICKS}. */
    public static int breakTicks(BlockState state, BlockGetter level, BlockPos pos) {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0) {
            return 1000; // unbreakable
        }
        return Math.max(MIN_TICKS, Math.round(hardness * 30.0F));
    }
}
