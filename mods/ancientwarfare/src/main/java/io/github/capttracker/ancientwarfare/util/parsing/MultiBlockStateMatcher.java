/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/util/parsing/MultiBlockStateMatcher.
 */
package io.github.capttracker.ancientwarfare.util.parsing;

import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/** Matches when any one of the block state matchers does. */
public class MultiBlockStateMatcher implements Predicate<BlockState> {
    private final BlockStateMatcher[] blockStateMatchers;

    public MultiBlockStateMatcher(BlockStateMatcher... blockStateMatchers) {
        this.blockStateMatchers = blockStateMatchers;
    }

    @Override
    public boolean test(BlockState state) {
        for (BlockStateMatcher blockStateMatcher : blockStateMatchers) {
            if (blockStateMatcher.test(state)) {
                return true;
            }
        }
        return false;
    }
}
