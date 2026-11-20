/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/util/parsing/PropertyStateMatcher.
 */
package io.github.capttracker.ancientwarfare.util.parsing;

import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/** Matches block states whose one property has the given value. */
public class PropertyStateMatcher implements Predicate<BlockState> {
    private final PropertyState<?, ?> propertyState;

    public PropertyStateMatcher(PropertyState<?, ?> propertyState) {
        this.propertyState = propertyState;
    }

    @Override
    public boolean test(BlockState state) {
        // The original threw when the state had no such property; a state without it simply does not match.
        return state.hasProperty(propertyState.getProperty())
                && state.getValue(propertyState.getProperty()).equals(propertyState.getValue());
    }
}
