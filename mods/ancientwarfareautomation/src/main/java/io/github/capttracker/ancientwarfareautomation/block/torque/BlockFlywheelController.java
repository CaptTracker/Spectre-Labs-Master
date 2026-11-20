/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/block/BlockFlywheelController.
 */
package io.github.capttracker.ancientwarfareautomation.block.torque;

import io.github.capttracker.ancientwarfare.block.BlockRotationHandler.RotationType;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileFlywheelControllerHeavy;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileFlywheelControllerLight;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileFlywheelControllerMedium;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileTorqueBase;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * The input/output block that sits on top of a flywheel storage stack ({@code flywheel_controller_light},
 * {@code _medium}, {@code _heavy}). Faces one of the four horizontal directions; the front is the output side and a
 * redstone signal switches the output off.
 */
public class BlockFlywheelController extends BlockTorqueBase implements ITieredBlock {
    private final TorqueTier tier;

    public BlockFlywheelController(BlockBehaviour.Properties properties, TorqueTier tier, Supplier<? extends BlockEntityType<?>> blockEntityType) {
        super(properties, blockEntityType);
        this.tier = tier;
    }

    @Override
    public TorqueTier getTier() {
        return tier;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (tier) {
            case LIGHT -> new TileFlywheelControllerLight(pos, state);
            case MEDIUM -> new TileFlywheelControllerMedium(pos, state);
            case HEAVY -> new TileFlywheelControllerHeavy(pos, state);
        };
    }

    /** 1.12.2 {@code isSideSolid} was true on every side (things can be attached to the controller). */
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }

    /**
     * Small improvement over the original: a controller placed next to a powered block knows about the redstone signal
     * at once (the original only read it at the next neighbour change).
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof TileTorqueBase torque) {
            torque.onNeighborTileChanged();
        }
    }

    @Override
    public RotationType getRotationType() {
        return RotationType.FOUR_WAY;
    }

    @Override
    public boolean invertFacing() {
        return false;
    }
}
