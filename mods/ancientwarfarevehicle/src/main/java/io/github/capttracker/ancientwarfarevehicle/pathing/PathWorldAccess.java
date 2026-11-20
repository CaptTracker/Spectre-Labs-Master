/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/pathing/PathWorldAccess.
 */
package io.github.capttracker.ancientwarfarevehicle.pathing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The walkability tests of the path finders (fences, doors, ladders, trapdoors, cactus, water), with the entity's abilities. */
public class PathWorldAccess {
    public boolean canOpenDoors;
    public boolean canSwim;
    public boolean canDrop;
    public boolean canUseLaders;
    private boolean canGoOnLand = true;
    protected final Level level;

    public PathWorldAccess(Level level) {
        this.level = level;
    }

    public void setCanGoOnLand(boolean val) {
        this.canGoOnLand = val;
        if (!val) {
            this.canSwim = true;
        }
    }

    public Block getBlock(BlockPos pos) {
        return level.getBlockState(pos).getBlock();
    }

    public int getTravelCost(BlockPos pos) {
        if (level.getBlockState(pos).is(Blocks.WATER)) {
            // can't swim check
            if (!canGoOnLand) {
                return 10;
            }
            return 30;
        }
        if (!canGoOnLand) {
            return 30;
        }
        return 10;
    }

    /**
     * Checks the collision bounds of the block at x, y, z to make sure it is at most 0.5 tall (pathable).
     *
     * @return true if it is a pathable block, false if it fails the bounds check
     */
    public boolean checkBlockBounds(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.WATER)) {
            return true;
        } else if (state.getBlock() instanceof TrapDoorBlock) {
            return state.getValue(TrapDoorBlock.OPEN);
        }
        if (!state.isAir()) {
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) {
                return true;
            }
            if (shape.max(Direction.Axis.Y) >= 0.5d) {
                return false;
            }
        }
        return true;
    }

    private boolean isWalkable2(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        Block blockDown = getBlock(pos.below());
        BlockState stateUp = level.getBlockState(pos.above());
        Block blockUp = stateUp.getBlock();
        boolean cube = !checkBlockBounds(x, y, z);
        boolean cube2 = !checkBlockBounds(x, y - 1, z);
        boolean cube3 = !checkBlockBounds(x, y + 1, z);
        if (isFence(blockDown) || (isDoor(pos.below()) && isDoor(pos)) || (block == Blocks.CACTUS || blockDown == Blocks.CACTUS || blockUp == Blocks.CACTUS)) {
            return false;
        }
        if (canGoOnLand) {
            if (canUseLaders && isLadder(block)) {
                return true;
            }
            if (canOpenDoors && isDoor(pos) && cube2) {
                return true;
            }
            if (!cube && !cube3 && (cube2 || canSupport(state, pos))) {
                // finally, check if block and blockY+1 are clear and blockY-1 is solid
                return true;
            }
        }
        return canSwim && state.is(Blocks.WATER) && stateUp.isAir();
    }

    public boolean isPartialBlock(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.isAir()) {
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) {
                return false;
            }
            AABB bb = shape.bounds();
            return bb.maxY <= 0.75d && bb.minX < 0.35 && bb.maxX > 0.65 && bb.minZ < 0.35 && bb.maxZ > 0.65;
        }
        return false;
    }

    private boolean canSupport(BlockState state, BlockPos pos) {
        if (state.getBlock() instanceof TrapDoorBlock) {
            return !state.getValue(TrapDoorBlock.OPEN) && state.getValue(TrapDoorBlock.HALF) == Half.BOTTOM;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return false;
        }
        AABB bb = shape.bounds();
        return bb.maxY <= 0.5d && bb.minX < 0.35 && bb.maxX > 0.65 && bb.minZ < 0.35 && bb.maxZ > 0.65;
    }

    private boolean isFence(Block block) {
        return block instanceof FenceBlock || block instanceof FenceGateBlock || block instanceof WallBlock;
    }

    public boolean isWalkable(int x, int y, int z) {
        return isWalkable2(x, y, z);
    }

    /** A wooden door (one that opens by hand) or a fence gate. */
    static boolean isHandOperatedDoor(BlockState state) {
        return (state.getBlock() instanceof DoorBlock door && door.type().canOpenByHand()) || state.getBlock() instanceof FenceGateBlock;
    }

    public boolean isDoor(BlockPos pos) {
        if (GateLookup.isGateProxy(level, pos)) {
            return GateLookup.canSoldierUseProxy(level, pos);
        }
        return isHandOperatedDoor(level.getBlockState(pos));
    }

    private boolean isLadder(Block block) {
        return block == Blocks.LADDER || block == Blocks.VINE;
    }

    protected boolean isLadder(BlockPos pos) {
        return isLadder(level.getBlockState(pos).getBlock());
    }

    public boolean isWalkable(int x, int y, int z, Node src) {
        return this.isWalkable(x, y, z);
    }

    public boolean isRemote() {
        return level.isClientSide;
    }
}
