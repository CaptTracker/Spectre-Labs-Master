/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/pathing/Navigator.
 */
package io.github.capttracker.ancientwarfarevehicle.pathing;

import io.github.capttracker.ancientwarfare.util.Trig;
import io.github.capttracker.ancientwarfarevehicle.entity.IGateEntity;
import io.github.capttracker.ancientwarfarevehicle.entity.IPathableEntity;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Drives a vehicle along a path: stuck detection, door and gate opening, ladder motion, node claiming, and
 * feeding {@code owner.setMoveTo} so that the move helper steers the vehicle.
 *
 * <p>Dormant: only {@code VehicleBase} constructs and ticks a navigator, and nothing in the original gives it a
 * target ({@link #setMoveToTarget} and {@link #forcePath} have no live callers), so the only live behaviour is the
 * stuck detection, which never triggers without a target.
 */
public class Navigator implements IPathableCallback {
    private static final int DOOR_OPEN_MAX = 15;
    private static final int DOOR_CHECK_TICKS_MAX = 5;

    private final PathFinderThetaStar pathFinder = new PathFinderThetaStar();
    protected final IPathableEntity owner;
    protected final Entity entity;
    protected final PathWorldAccess world;
    protected final EntityPath path;
    private final Node finalTarget = new Node(0, 0, 0);
    @Nullable
    private Node currentTarget;
    @Nullable
    protected IGateEntity gate = null;
    private boolean hasDoor = false;
    private BlockPos doorPos = BlockPos.ZERO;
    private int doorOpenTicks = 0;
    private int doorCheckTicks = 0;
    private Vec3 stuckCheckPosition;
    private int stuckCheckTicks = 40;
    private int stuckCheckTicksMax = 40;
    private final PathFinderCrawler testCrawler;

    // TODO(original): fallen detection; better handling of when no path can be found (remember the last
    // request(s), and if the same or similar and no path, try what?); the crawler needs to test for vertical
    // clearance when moving up or down (see theta)

    public Navigator(VehicleBase owner) {
        this.owner = owner;
        this.entity = owner.getEntity();
        this.world = owner.worldAccess;
        this.path = new EntityPath();
        finalTarget.reassign(Mth.floor(entity.getX()), Mth.floor(entity.getY()), Mth.floor(entity.getZ()));
        this.stuckCheckPosition = entity.position();
        this.testCrawler = new PathFinderCrawler();
    }

    public void setStuckCheckTicks(int ticks) {
        if (ticks > 0) {
            this.stuckCheckTicksMax = ticks;
        }
    }

    public void setMoveToTarget(BlockPos pos) {
        if (!entity.level().isLoaded(pos)) {
            return;
        }
        int ex = Mth.floor(entity.getX());
        int ey = Mth.floor(entity.getY());
        int ez = Mth.floor(entity.getZ());
        if (entity.getY() % 1.f > 0.75 && !world.isWalkable(ex, ey, ez)) {
            ey++;
        }
        if (this.shouldCalculatePath(pos)) {
            this.finalTarget.reassign(pos);
            this.calculatePath(ex, ey, ez, pos);
        }
    }

    public void onMovementUpdate() {
        this.updateMoveHelper();
        this.detectStuck();
        this.claimNode();
        if (this.currentTarget != null) {
            if (this.world.canUseLaders) {
                this.handleLadderMovement();
            }
            if (this.world.canOpenDoors) {
                this.doorInteraction();
            }
            owner.setMoveTo(currentTarget.x + 0.5d, currentTarget.y, currentTarget.z + 0.5d, owner.getDefaultMoveSpeed());
        }
    }

    private void handleLadderMovement() {
        if (owner.isPathableEntityOnLadder()) {
            Vec3 motion = entity.getDeltaMovement();
            if (currentTarget.y < (int) entity.getY()) {
                entity.setDeltaMovement(motion.x, -0.125f, motion.z);
            } else if (currentTarget.y > (int) entity.getY()) {
                entity.setDeltaMovement(motion.x, 0.125f, motion.z);
            }
        }
    }

    private void updateMoveHelper() {
        this.pathFinder.doSearchIterations(10);
        if (this.doorOpenTicks > 0) {
            this.doorOpenTicks--;
        }
        if (this.hasDoor && this.doorOpenTicks <= 0) {
            this.hasDoor = false;
            this.interactWithDoor(doorPos, false);
        }
        if (this.gate != null && this.doorOpenTicks <= 0) {
            this.interactWithGate(false);
            this.gate = null;
        }
    }

    private void detectStuck() {
        if (this.stuckCheckTicks <= 0) {
            this.stuckCheckTicks = this.stuckCheckTicksMax;
            if (this.currentTarget != null && Math.sqrt(entity.distanceToSqr(stuckCheckPosition)) < 1.5d) {
                this.owner.onStuckDetected();
                this.clearPath();
                this.currentTarget = null;
            }
            stuckCheckPosition = entity.position();
        } else {
            this.stuckCheckTicks--;
        }
    }

    private boolean isNewTargetClose(BlockPos target) {
        float dist = (float) Math.sqrt(entity.distanceToSqr(finalTarget.x, finalTarget.y, finalTarget.z));
        float tDist = Trig.getDistance(finalTarget.x, finalTarget.y, finalTarget.z, target.getX(), target.getY(), target.getZ());
        return tDist < dist * 0.1f;
    }

    private boolean isNewTarget(BlockPos target) {
        return !isNewTargetClose(target) && !this.finalTarget.equals(target.getX(), target.getY(), target.getZ());
    }

    private boolean isAtTarget(BlockPos pos) {
        return Math.sqrt(entity.distanceToSqr(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d)) < entity.getBbWidth();
    }

    private boolean isPathEmpty() {
        return this.path.getActivePathSize() <= 0;
    }

    private boolean shouldCalculatePath(BlockPos target) {
        return isNewTarget(target) || (isPathEmpty() && !isAtTarget(target) && currentTarget == null && !pathFinder.isSearching);
    }

    private void calculatePath(int ex, int ey, int ez, BlockPos target) {
        this.path.clearPath();
        this.currentTarget = null;
        if (PathUtils.canPathStraightToTarget(world, ex, ey, ez, target)) {
            this.currentTarget = new Node(target);
        } else {
            this.path.setPath(testCrawler.findPath(world, ex, ey, ez, target, 8));
            Node end = this.path.getEndNode();
            if (end != null && (end.x != target.getX() || end.y != target.getY() || end.z != target.getZ())) {
                this.pathFinder.findPath(world, end.x, end.y, end.z, target, 60, this, false);
            }
        }
        this.stuckCheckTicks = this.stuckCheckTicksMax;
        stuckCheckPosition = entity.position();
        Node start = this.path.getFirstNode();
        if (start != null && (getEntityDistance(start) < 0.8f && start.y == ey)) {
            this.path.claimNode(); // skip the first node because it is probably behind you, move on to the next
        }
        this.claimNode();
    }

    private void doorInteraction() {
        if (this.doorCheckTicks <= 0) {
            this.doorCheckTicks = DOOR_CHECK_TICKS_MAX;
            if (this.entity.horizontalCollision && checkForDoors(entity.blockPosition())) {
                if (this.hasDoor) {
                    this.interactWithDoor(doorPos, true);
                    this.doorOpenTicks = DOOR_OPEN_MAX;
                } else if (gate != null) {
                    this.interactWithGate(true);
                    this.doorOpenTicks = DOOR_OPEN_MAX;
                }
            }
        } else {
            this.doorCheckTicks--;
        }
    }

    /** Looks for a door, fence gate or gate at the entity's position and then one block in the direction it faces. */
    private boolean checkForDoors(BlockPos entityPos) {
        if (checkDoorAt(entityPos)) {
            return true;
        }
        float yaw = Trig.wrapTo360(entity.getYRot());
        int x = entityPos.getX();
        int y = entityPos.getY();
        int z = entityPos.getZ();
        if (yaw >= 360 - 45 || yaw < 45) {
            z++; // south, check z+
        } else if (yaw >= 45 && yaw < 45 + 90) {
            x--; // west
        } else if (yaw >= 180 - 45 && yaw < 180 + 45) {
            z--; // north
        } else {
            x++; // east
        }
        // The original stored the entity's own position as the door position here (sic), so the door it found
        // was never the one it toggled. Obvious local fix: remember the position that was checked.
        return checkDoorAt(new BlockPos(x, y, z));
    }

    private boolean checkDoorAt(BlockPos pos) {
        Level level = entity.level();
        BlockState state = level.getBlockState(pos);
        if (PathWorldAccess.isHandOperatedDoor(state)) {
            if (hasDoor && !doorPos.equals(pos)) {
                this.interactWithDoor(doorPos, false);
            }
            doorPos = pos;
            hasDoor = true;
            return true;
        }
        if (GateLookup.isGateProxy(level, pos)) {
            interactWithGate(false);
            gate = GateLookup.getOwner(level, pos);
            return true;
        }
        return false;
    }

    private void interactWithGate(boolean open) {
        if (gate == null) {
            return;
        }
        if ((gate.getEdgePosition() > 0 && !open) || (gate.getEdgePosition() == 0 && open)) {
            gate.activateGate();
        }
        if (!open) {
            this.gate = null;
        }
    }

    private void interactWithDoor(BlockPos doorPos, boolean open) {
        Level level = entity.level();
        BlockState state = level.getBlockState(doorPos);
        if (state.getBlock() instanceof DoorBlock door && door.type().canOpenByHand()) {
            door.setOpen(entity, level, state, doorPos, open);
        } else if (state.getBlock() instanceof FenceGateBlock && open != state.getValue(FenceGateBlock.OPEN)) {
            // the 1008 and 1014 level events of 1.12.2 (fence gate sounds) are played directly now
            level.setBlock(doorPos, state.setValue(FenceGateBlock.OPEN, open), 2);
            level.playSound(null, doorPos, open ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    private void claimNode() {
        if (this.currentTarget == null || this.getEntityDistance(currentTarget) < entity.getBbWidth()) {
            this.currentTarget = this.path.claimNode();
            while (this.currentTarget != null && this.getEntityDistance(currentTarget) < entity.getBbWidth()) {
                this.currentTarget = this.path.claimNode();
            }
            this.stuckCheckTicks = this.stuckCheckTicksMax;
            stuckCheckPosition = entity.position();
        }
    }

    private float getEntityDistance(@Nullable Node n) {
        return n == null ? 0.f : (float) Math.sqrt(entity.distanceToSqr(n.x + 0.5d, n.y, n.z + 0.5d));
    }

    @Override
    public void onPathFound(List<Node> pathNodes) {
        this.path.addPath(world, pathNodes);
    }

    public void clearPath() {
        this.path.clearPath();
        this.currentTarget = null;
    }

    public void forcePath(List<Node> n) {
        this.path.setPath(n);
        this.claimNode();
    }

    public void setCanGoOnLand(boolean land) {
        this.world.setCanGoOnLand(land);
    }
}
