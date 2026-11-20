/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/util/parsing/PropertyState.
 */
package io.github.capttracker.ancientwarfare.util.parsing;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** A block-state property together with one of its values. */
public class PropertyState<T extends Comparable<T>, V extends T> {
    private final Property<T> property;
    private final V value;

    public PropertyState(Property<T> property, V value) {
        this.property = property;
        this.value = value;
    }

    public Property<T> getProperty() {
        return property;
    }

    public V getValue() {
        return value;
    }

    public BlockState update(BlockState state) {
        return state.setValue(property, value);
    }
}
