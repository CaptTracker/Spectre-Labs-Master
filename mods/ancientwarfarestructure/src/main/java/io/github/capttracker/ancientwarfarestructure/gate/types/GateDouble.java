/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: structure/gates/types/GateDouble.
 */
package io.github.capttracker.ancientwarfarestructure.gate.types;

import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfarestructure.entity.EntityGate;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.AABB;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** A gate of two panels that slide apart. */
public class GateDouble extends Gate {

    public GateDouble(int id, String registryName, String textureLocation, Supplier<SoundEvent> moveSound, Supplier<SoundEvent> hurtSound, Supplier<SoundEvent> breakSound, IntSupplier maxHealth) {
        super(id, registryName, textureLocation, moveSound, hurtSound, breakSound, maxHealth);
    }

    @Override
    public void setInitialBounds(EntityGate gate, BlockPos pos1, BlockPos pos2) {
        BlockPos min = BlockTools.getMin(pos1, pos2);
        BlockPos max = BlockTools.getMax(pos1, pos2);
        boolean wideOnXAxis = min.getX() != max.getX();
        float width = wideOnXAxis ? max.getX() - min.getX() + 1 : max.getZ() - min.getZ() + 1;
        float xOffset = wideOnXAxis ? width * 0.5f : 0.5f;
        float zOffset = wideOnXAxis ? 0.5f : width * 0.5f;
        gate.setPositions(pos1, pos2);
        // the original used new AxisAlignedBB(min, max), one block short on each axis; the volume is what it meant
        gate.setRenderBoundingBox(new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1));
        gate.edgeMax = width * 0.5f;
        gate.setPos(min.getX() + xOffset, min.getY(), min.getZ() + zOffset);
    }
}
