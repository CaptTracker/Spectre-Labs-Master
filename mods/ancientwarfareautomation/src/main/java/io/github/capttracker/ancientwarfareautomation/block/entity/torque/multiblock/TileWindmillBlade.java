/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/torque/multiblock/TileWindmillBlade.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.torque.multiblock;

import io.github.capttracker.ancientwarfare.block.entity.UpdatableBlockEntity;
import io.github.capttracker.ancientwarfare.util.Trig;
import io.github.capttracker.ancientwarfare.util.WorldTools;
import io.github.capttracker.ancientwarfareautomation.block.torque.IBlockEntityTypeProvider;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * One blade block of a windmill. Blades that touch form a flat square in a vertical plane: valid when the height is
 * odd and at least 5, one horizontal extent is 1, the other equals the height, and the square is full. The centre
 * block is the control block; it produces {@code size * windmill_per_size_output} energy every tick, which the
 * windmill controller block directly behind it takes. There is no wind, weather or height check (as in the original;
 * the comment there says sizes up to 17, but nothing enforces an upper bound beyond the flood fill radius of 20).
 *
 * <p>Only the control block renders (see {@link #isControl()}); the renderer reads {@link #getRotation(float)},
 * {@link #getWindmillSize()} and {@link #getDirection()}. Validation runs when a blade is placed
 * ({@link #blockPlaced()}) and when one is broken ({@link #blockBroken()}).
 */
public class TileWindmillBlade extends UpdatableBlockEntity {

    private static final String IS_CONTROL_TAG = "isControl";
    private static final String CONTROL_POS_TAG = "controlPos";
    private static final String DIRECTION_TAG = "direction";
    private final double bladeRpm = 20.d;
    private final double bladeRpt = bladeRpm * AWAutomationStatics.rpmToRpt;

    @Nullable
    private BlockPos controlPos;

    private boolean isControl = false;//set to true if this is the control block for a setup
    protected float rotation;//used in rendering
    private float lastRotationDiff;

    /*
     * the raw size of the windmill in blocks tall
     */
    private int windmillSize = 0;
    public double energy = 0;
    private Direction windmillDirection = Direction.NORTH;

    public TileWindmillBlade(BlockPos pos, BlockState state) {
        super(IBlockEntityTypeProvider.typeOf(state), pos, state);
    }

    public boolean isControl() {
        return isControl;
    }

    public boolean isFormed() {
        return controlPos != null;
    }

    /** The control block of the windmill this blade belongs to, or null if it is not part of a valid one. */
    @Nullable
    public BlockPos getControlPos() {
        return controlPos;
    }

    /** The axis the blade plane lies along: WEST if the plane is thin in X, NORTH otherwise (only the axis counts). */
    public Direction getDirection() {
        return windmillDirection;
    }

    public int getWindmillSize() {
        return windmillSize;
    }

    public double getEnergy() {
        return energy;
    }

    /** The original took an int although the field is a double (windmill_per_size_output may be fractional); it is a double here. */
    public void setEnergy(double energy) {
        this.energy = energy;
    }

    /** Called every tick on both sides by the block's ticker. */
    public void update() {
        if (isControl) {
            if (!level.isClientSide) {
                energy = windmillSize * AWAutomationStatics.windmill_per_size_output;
            } else {
                updateRotation();
            }
        }
    }

    public float getRotation(float delta) {
        return rotation - lastRotationDiff * (1 - delta);
    }

    private void updateRotation() {
        lastRotationDiff = (float) (bladeRpt * Trig.TORADIANS);
        rotation += lastRotationDiff;
        rotation %= Trig.PI * 2;
    }

    /** Called by the block after it was placed (server). */
    public void blockPlaced() {
        if (!level.isClientSide) {
            validateSetup();
        }
    }

    /** Called by the block after it was removed from the world (server). */
    public void blockBroken() {
        if (!level.isClientSide) {
            if (isControl) {
                int expand = (windmillSize - 1) / 2;
                if (expand > 0) {
                    if (windmillDirection.getAxis() == Direction.Axis.Z) {
                        for (int j = -expand; j < expand + 1; j++) {
                            for (int i = -expand; i < expand + 1; i++) {
                                WorldTools.getTile(level, worldPosition.offset(i, j, 0), TileWindmillBlade.class).ifPresent(t -> t.setController(null));
                            }
                        }
                    } else if (windmillDirection.getAxis() == Direction.Axis.X) {
                        for (int j = -expand; j < expand + 1; j++) {
                            for (int i = -expand; i < expand + 1; i++) {
                                WorldTools.getTile(level, worldPosition.offset(0, j, i), TileWindmillBlade.class).ifPresent(t -> t.setController(null));
                            }
                        }
                    }
                }
                isControl = false;
            } else if (controlPos != null) {
                WorldTools.getTile(level, controlPos, TileWindmillBlade.class).ifPresent(TileWindmillBlade::validateSetup);
                controlPos = null;
            } else {
                informNeighborsToValidate();
            }
        }
    }

    private boolean validateSetup() {
        Block block = getBlockState().getBlock();
        Predicate<BlockState> sameBlock = s -> s.is(block);
        MultiblockScan.Region region = MultiblockScan.cross(level, worldPosition, sameBlock);
        int minX = region.min().getX();
        int minY = region.min().getY();
        int minZ = region.min().getZ();
        int xSize = region.sizeX();
        int ySize = region.sizeY();
        int zSize = region.sizeZ();

        /*
         * if y size >= 5
         * and h%2==1 (is an odd size, 5, 7, 9, etc)
         * and either x or z size == 1 (one needs to be a single block thick)
         * and either x or z == y size (the other needs to be the same size as height)
         * and is full cube (all block spots filled) (will need to modify this check for those sizes with missing corner blocks, create bit mask 2d array to test for proper setup)
         */
        boolean valid = ySize >= 5 && ySize % 2 == 1 && (zSize == 1 || xSize == 1) && (zSize == ySize || xSize == ySize) && MultiblockScan.isBoxFilled(level, region, sameBlock);
        if (valid) {
            /*
             * calculate the control block coordinates from the min coordinate and sizes
             */
            int controlX;
            int controlY;
            int controlZ;

            int halfSize = (ySize - 1) / 2;

            controlY = minY + halfSize;//should be the center
            if (xSize > 1)//widest on X axis
            {
                controlX = minX + halfSize;//should be the center
                controlZ = minZ;//only 1 z-coordinate
            } else//widest on Z axis
            {
                controlX = minX;//only 1 x-coordinate
                controlZ = minZ + halfSize;//should be the center
            }
            setValidSetup(MultiblockScan.boxPositions(region), controlX, controlY, controlZ, xSize, ySize, zSize);
        } else {
            setInvalidSetup(region.positions());
        }
        return valid;
    }

    private void informNeighborsToValidate() {
        for (Direction d : Direction.values()) {
            WorldTools.getTile(level, worldPosition.relative(d), TileWindmillBlade.class).ifPresent(TileWindmillBlade::validateSetup);
        }
    }

    private void setController(@Nullable BlockPos pos) {
        boolean dirty = false;
        if (pos == null) {
            if (isControl || controlPos != null) {
                dirty = true;
            }
            isControl = false;
            controlPos = null;
        } else {
            if (!pos.equals(controlPos)) {
                dirty = true;
            }
            controlPos = pos;
        }
        if (dirty) {
            setChanged();
            syncToClients();
        }
    }

    private void setInvalidSetup(Set<BlockPos> set) {
        for (BlockPos pos : set) {
            WorldTools.getTile(level, pos, TileWindmillBlade.class).ifPresent(t -> t.setController(null));
        }
    }

    private void setValidSetup(Set<BlockPos> set, int cx, int cy, int cz, int xs, int ys, int zs) {
        setController(new BlockPos(cx, cy, cz));
        final BlockPos control = controlPos;
        Optional<TileWindmillBlade> te = WorldTools.getTile(level, control, TileWindmillBlade.class);
        if (te.isPresent()) {
            te.get().setAsController(xs, ys, zs);
            for (BlockPos pos : set) {
                WorldTools.getTile(level, pos, TileWindmillBlade.class).ifPresent(t -> t.setController(control));
            }
        } else {
            setController(null);
        }
    }

    private void setAsController(int xSize, int ySize, int zSize) {
        windmillDirection = xSize == 1 ? Direction.WEST : Direction.NORTH;
        windmillSize = ySize;
        this.isControl = true;
        setChanged();
        syncToClients();
    }

    @Override
    protected void writeUpdateNBT(CompoundTag tag) {
        super.writeUpdateNBT(tag);
        tag.putBoolean(IS_CONTROL_TAG, isControl);
        if (controlPos != null) {
            tag.putLong(CONTROL_POS_TAG, controlPos.asLong());
        }
        if (isControl) {
            tag.putInt("size", windmillSize);
            tag.putByte(DIRECTION_TAG, (byte) windmillDirection.get3DDataValue());
        }
    }

    @Override
    protected void handleUpdateNBT(CompoundTag tag) {
        super.handleUpdateNBT(tag);
        controlPos = tag.contains(CONTROL_POS_TAG) ? BlockPos.of(tag.getLong(CONTROL_POS_TAG)) : null;
        isControl = tag.getBoolean(IS_CONTROL_TAG);
        if (isControl) {
            windmillSize = tag.getInt("size");
            windmillDirection = Direction.from3DDataValue(tag.getByte(DIRECTION_TAG));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controlPos = tag.contains(CONTROL_POS_TAG) ? BlockPos.of(tag.getLong(CONTROL_POS_TAG)) : null;
        isControl = tag.getBoolean(IS_CONTROL_TAG);
        if (isControl) {
            windmillSize = tag.getInt("size");
            windmillDirection = Direction.from3DDataValue(tag.getByte(DIRECTION_TAG));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean(IS_CONTROL_TAG, isControl);
        if (controlPos != null) {
            tag.putLong(CONTROL_POS_TAG, controlPos.asLong());
        }
        if (isControl) {
            tag.putInt("size", windmillSize);
            tag.putByte(DIRECTION_TAG, (byte) windmillDirection.get3DDataValue());
        }
    }

    /** Render bounds for the block entity renderer (it grows with the size of the blade plane). */
    public AABB getRenderBounds() {
        int expand = (windmillSize - 1) / 2;
        return new AABB(worldPosition.getX() - expand, worldPosition.getY() - expand, worldPosition.getZ() - expand, worldPosition.getX() + 1 + expand, worldPosition.getY() + 1 + expand, worldPosition.getZ() + 1 + expand);
    }
}
