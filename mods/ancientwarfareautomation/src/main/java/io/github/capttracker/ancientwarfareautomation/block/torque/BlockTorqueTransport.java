/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/block/BlockTorqueTransport.
 */
package io.github.capttracker.ancientwarfareautomation.block.torque;

import io.github.capttracker.ancientwarfare.block.BlockRotationHandler.RotationType;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileTorqueSidedCell;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

/**
 * Base of shaft, junction and distributor: one tier per block (was the {@code tier} property and the metadata), can
 * face any of the six directions. The shape is a 0.1875 to 0.8125 core that reaches the block edge on every side the
 * block entity reports as connected.
 */
public abstract class BlockTorqueTransport extends BlockTorqueBase implements ITieredBlock {
    private static final float MIN = 0.1875f;
    private static final float MAX = 0.8125f;
    /** Shapes by connection bit mask (bit i = side with direction ordinal i), built when first needed. */
    private static final VoxelShape[] CONNECTION_SHAPES = new VoxelShape[64];

    private final TorqueTier tier;

    protected BlockTorqueTransport(BlockBehaviour.Properties properties, TorqueTier tier, Supplier<? extends BlockEntityType<?>> blockEntityType) {
        super(properties, blockEntityType);
        this.tier = tier;
    }

    @Override
    public TorqueTier getTier() {
        return tier;
    }

    @Override
    public RotationType getRotationType() {
        return RotationType.SIX_WAY;
    }

    @Override
    public boolean invertFacing() {
        return false;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (level.getBlockEntity(pos) instanceof TileTorqueSidedCell tile) {
            return getConnectionShape(tile.getConnections());
        }
        return getConnectionShape(null);
    }

    /** The shape for the given connections (index = direction ordinal: down, up, north, south, west, east); null means none. */
    protected static VoxelShape getConnectionShape(boolean[] sides) {
        int mask = 0;
        if (sides != null) {
            for (int i = 0; i < sides.length && i < 6; i++) {
                if (sides[i]) {
                    mask |= 1 << i;
                }
            }
        }
        VoxelShape shape = CONNECTION_SHAPES[mask];
        if (shape == null) {
            float x1 = MIN;
            float y1 = MIN;
            float z1 = MIN;
            float x2 = MAX;
            float y2 = MAX;
            float z2 = MAX;
            if ((mask & 1) != 0) {
                y1 = 0.f;
            }
            if ((mask & 2) != 0) {
                y2 = 1.f;
            }
            if ((mask & 4) != 0) {
                z1 = 0.f;
            }
            if ((mask & 8) != 0) {
                z2 = 1.f;
            }
            if ((mask & 16) != 0) {
                x1 = 0.f;
            }
            if ((mask & 32) != 0) {
                x2 = 1.f;
            }
            shape = Shapes.box(x1, y1, z1, x2, y2, z2);
            CONNECTION_SHAPES[mask] = shape;
        }
        return shape;
    }
}
