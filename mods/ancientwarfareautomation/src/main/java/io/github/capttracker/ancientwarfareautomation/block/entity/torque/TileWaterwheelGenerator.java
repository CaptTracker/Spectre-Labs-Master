/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/torque/TileWaterwheelGenerator.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.torque;

import io.github.capttracker.ancientwarfare.api.ITorque.TorqueCell;
import io.github.capttracker.ancientwarfare.util.Trig;
import io.github.capttracker.ancientwarfareautomation.block.torque.BlockWaterwheelGenerator;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Generates a flat 1 torque per tick (times the config factor) while a water channel is in front of the wheel.
 * The channel is checked every 20 ticks: the two inner blocks must be air, the 2 by 3 blocks beside and below them
 * must be air or water, and the water must flow down one side with air on the other (or run under the wheel).
 * The wheel itself sits on the back side of the block (the block faces away from the wheel).
 *
 * <p>The 1.12.2 {@code Material.WATER} tests are fluid tests now ({@code FluidTags.WATER}); the flow strength of the
 * lower cells comes from {@code LiquidBlock.LEVEL} (0 for a source, 1 to 7 flowing, 8 and above falling), as before.
 */
public class TileWaterwheelGenerator extends TileTorqueSingleCell {

    private static final int AIR = 1;
    private static final int WATER = 2;
    private static final int OTHER = 0;

    public float wheelRotation;
    public float lastWheelRotationDiff;

    private final float rotTick;
    private byte rotationDirection = 1; //1, -1 or 0 (no turning); sent to the client
    private byte syncedRotationDirection = 1;

    private int updateTick;

    public boolean validSetup = false;

    public TileWaterwheelGenerator(BlockPos pos, BlockState state) {
        super(pos, state);
        torqueCell = new TorqueCell(0, 4, AWAutomationStatics.low_transfer_max, AWAutomationStatics.low_efficiency_factor);
        float maxWheelRpm = 20;
        rotTick = maxWheelRpm * AWAutomationStatics.rpmToRpt;
    }

    @Override
    public void update() {
        super.update();
        if (!level.isClientSide) {
            updateTick--;
            if (updateTick <= 0) {
                updateTick = 20;
                boolean valid = validateBlocks();
                if (valid != validSetup || rotationDirection != syncedRotationDirection) {
                    validSetup = valid;
                    syncedRotationDirection = rotationDirection;
                    updateValidSetupState();
                    setChanged();
                    syncToClients();
                }
            }
            if (validSetup)//server, update power gen
            {
                torqueCell.setEnergy(torqueCell.getEnergy() + AWAutomationStatics.waterwheel_generator_output);
            }
        }
    }

    /** Mirrors the flag into the block state property {@code valid_setup} (sent to clients, no neighbour update). */
    private void updateValidSetupState() {
        BlockState state = getBlockState();
        if (state.hasProperty(BlockWaterwheelGenerator.VALID_SETUP) && state.getValue(BlockWaterwheelGenerator.VALID_SETUP) != validSetup) {
            level.setBlock(worldPosition, state.setValue(BlockWaterwheelGenerator.VALID_SETUP, validSetup), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void updateRotation() {
        super.updateRotation();
        if (validSetup) {
            lastWheelRotationDiff = (rotTick * (float) rotationDirection) * Trig.TORADIANS;
            wheelRotation += lastWheelRotationDiff;
            wheelRotation %= Trig.PI * 2;
        }
    }

    @Override
    protected void handleUpdateNBT(CompoundTag tag) {
        super.handleUpdateNBT(tag);
        validSetup = tag.getBoolean("validSetup");
        rotationDirection = tag.getByte("rotationDirection");
    }

    @Override
    protected void writeUpdateNBT(CompoundTag tag) {
        super.writeUpdateNBT(tag);
        tag.putBoolean("validSetup", validSetup);
        tag.putByte("rotationDirection", rotationDirection);
    }

    // validSetup and rotationDirection are not saved (as in 1.12.2): the first tick after a load validates the channel.

    private boolean validateBlocks() {
        Direction d = getPrimaryFacing().getOpposite();
        BlockPos innerPos = worldPosition.relative(d);
        //quick check for invalid setup
        //must have air inside the inner two blocks
        if (getValidationType(innerPos.above()) != AIR || getValidationType(innerPos) != AIR) {
            return false;
        }
        BlockPos right = innerPos.relative(d.getClockWise());
        BlockPos left = innerPos.relative(d.getCounterClockWise());

        byte[] validationGrid = new byte[6];
        validationGrid[0] = getValidationType(left.above());
        validationGrid[1] = getValidationType(right.above());
        validationGrid[2] = getValidationType(left);
        validationGrid[3] = getValidationType(right);
        validationGrid[4] = getValidationType(left.below());
        validationGrid[5] = getValidationType(right.below());
        for (byte aValue : validationGrid) {
            if (aValue == OTHER) {
                return false;
            }
        }
        if (validationGrid[2] == WATER && validationGrid[4] == WATER)//left side water flowing down
        {
            //check opposite side for air (underneath has already been checked by quick block check above)
            if (validationGrid[1] == AIR && validationGrid[3] == AIR) {
                rotationDirection = -1;//TODO check if these are correct rotation directions
                return true;
            }
            return false;
        } else if (validationGrid[3] == WATER && validationGrid[5] == WATER)//right side water flowing down
        {
            //check opposite side for air (underneath has already been checked by quick block check above)
            if (validationGrid[0] == AIR && validationGrid[2] == AIR) {
                rotationDirection = 1;
                return true;
            }
            return false;
        } else//not a direct flow downwards, check underneath for flow
        {
            if (validationGrid[4] != WATER || validationGrid[5] != WATER || getValidationType(innerPos.below()) != WATER) {
                return false;
            }
            int levelLeft = getFlowLevel(left.below());
            int levelRight = getFlowLevel(right.below());
            rotationDirection = (byte) (levelLeft < levelRight ? -1 : levelRight < levelLeft ? 1 : 0);
            return true;
        }
    }

    /** The {@code LiquidBlock.LEVEL} of the water at the position (0 for a source or anything that is not flowing water). */
    private int getFlowLevel(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getFluidState().is(FluidTags.WATER) && state.getBlock() instanceof LiquidBlock) {
            return state.getValue(LiquidBlock.LEVEL);
        }
        return 0;
    }

    private byte getValidationType(BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return OTHER;
        }
        if (level.isEmptyBlock(pos)) {
            return AIR;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getFluidState().is(FluidTags.WATER)) {
            return WATER;
        }
        return OTHER;
    }

    /** Whether a valid water channel was found at the last check (client: as sent by the server). */
    public boolean isValidSetup() {
        return validSetup;
    }

    /** 1, -1 or 0: which way the wheel turns (0 for water that does not flow). */
    public byte getRotationDirection() {
        return rotationDirection;
    }

    @Override
    public AABB getRenderBounds() {
        return new AABB(worldPosition.getX() - 3, worldPosition.getY() - 3, worldPosition.getZ() - 3, worldPosition.getX() + 4, worldPosition.getY() + 4, worldPosition.getZ() + 4);
    }

    @Override
    public float getClientOutputRotation(Direction from, float delta) {
        if (from == getPrimaryFacing().getOpposite()) {
            return getRenderRotation(wheelRotation, lastWheelRotationDiff, delta);
        }
        return super.getClientOutputRotation(from, delta);
    }
}
