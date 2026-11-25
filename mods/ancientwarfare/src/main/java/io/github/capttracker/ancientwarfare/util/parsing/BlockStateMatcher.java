/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/util/parsing/BlockStateMatcher.
 */
package io.github.capttracker.ancientwarfare.util.parsing;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Matches a block and, optionally, a subset of its property values (a partial block state).
 * For plain "this block or tag" needs in new data a tag or a vanilla block predicate is simpler.
 */
public class BlockStateMatcher implements Predicate<BlockState> {
    private final Block block;
    private final PropertyMapMatcher propertyMatcher = new PropertyMapMatcher();

    /** Matches the block and every property value of the state. */
    public BlockStateMatcher(BlockState fullState) {
        this(fullState.getBlock());
        fullState.getValues().forEach(this::addProperty);
    }

    public BlockStateMatcher(Block block) {
        this.block = block;
    }

    public BlockStateMatcher addProperty(Property<?> property, Comparable<?> value) {
        propertyMatcher.addProperty(property, value);
        return this;
    }

    public Block getBlock() {
        return block;
    }

    @Override
    public boolean test(BlockState state) {
        return block == state.getBlock() && propertyMatcher.test(state.getValues());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BlockStateMatcher that = (BlockStateMatcher) o;
        return block.equals(that.block) && propertyMatcher.equals(that.propertyMatcher);
    }

    @Override
    public int hashCode() {
        return 31 * block.hashCode() + propertyMatcher.hashCode();
    }

    public static class PropertyMapMatcher implements Predicate<Map<Property<?>, Comparable<?>>> {
        private final Map<Property<?>, Comparable<?>> propertyValues = new HashMap<>();

        void addProperty(Property<?> property, Comparable<?> value) {
            propertyValues.put(property, value);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            return propertyValues.equals(((PropertyMapMatcher) o).propertyValues);
        }

        @Override
        public int hashCode() {
            return propertyValues.hashCode();
        }

        @Override
        public boolean test(Map<Property<?>, Comparable<?>> properties) {
            for (Map.Entry<Property<?>, Comparable<?>> property : propertyValues.entrySet()) {
                if (!properties.containsKey(property.getKey()) || !property.getValue().equals(properties.get(property.getKey()))) {
                    return false;
                }
            }
            return true;
        }
    }
}
