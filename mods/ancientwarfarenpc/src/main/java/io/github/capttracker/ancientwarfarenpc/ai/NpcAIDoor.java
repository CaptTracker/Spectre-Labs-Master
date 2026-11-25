/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/NpcAIDoor.
 */
package io.github.capttracker.ancientwarfarenpc.ai;

import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Opens the wooden doors and the fence gates on the path of the NPC and closes them behind it.
 *
 * <p>The goal starts when the current path passes within 1.2 blocks of a door or gate at the current, the previous or the next path node, or when the
 * NPC is blocked horizontally and stands in or next to one. A door counts as owned and is opened again every 40 ticks until the NPC is further away
 * than sqrt 2 and no friendly NPC is within 2.1 blocks (the friendliness check uses {@code isHostileTowards}, so NPCs of two factions do not hold
 * doors for each other); then the door is closed (if {@code closeBehind}).
 *
 * <h2>Changes from 1.12.2</h2>
 * <ul>
 *   <li>A door is a {@link DoorBlock} in the {@code minecraft:wooden_doors} tag (the original: {@code Material.WOOD}, which excluded the iron door).
 *       It is opened with {@code DoorBlock.setOpen}. The path (and the path finder: fence gates are walkable, see {@code NpcWalkNodeEvaluator}) is that of
 *       the navigation of the NPC, which is the mount's if the NPC rides.</li>
 *   <li>A gate plays its open or close sound only when its state really changed (the original played the opening sound at every recheck of an open gate).</li>
 * </ul>
 */
public class NpcAIDoor extends NpcAI<NpcBase> {
    private final boolean close;
    private final Set<BlockPos> doorPositions = new HashSet<>();
    private static final int RECHECK_INTERVAL = 40;
    private int doorCheckCooldown = RECHECK_INTERVAL;

    public NpcAIDoor(NpcBase npc, boolean closeBehind) {
        super(npc);
        close = closeBehind;
    }

    @Override
    public final boolean canUse() {
        if (!super.canUse()) {
            return false;
        }
        if (!doorPositions.isEmpty()) {
            return true;
        }
        if (!(npc.getNavigation() instanceof GroundPathNavigation navigation) || !navigation.canPassDoors() || navigation.isDone()) {
            return false;
        }
        Path path = navigation.getPath();
        if (path == null) {
            return false;
        }
        if (addDoorCloseOnThePath(path)) {
            return true;
        }
        if (!npc.horizontalCollision) {
            return false;
        }
        BlockPos potentialDoorPos = new BlockPos(Mth.floor(npc.getX()), Mth.floor(npc.getY()), Mth.floor(npc.getZ()));
        return findDoor(potentialDoorPos) || findDoor(potentialDoorPos.above());
    }

    private boolean addDoorCloseOnThePath(Path path) {
        for (int i = Math.max(path.getNextNodeIndex() - 1, 0); i < Math.min(path.getNextNodeIndex() + 2, path.getNodeCount()); ++i) {
            Node pathPoint = path.getNode(i);
            if (npc.distanceToSqr(pathPoint.x + 0.5D, npc.getY(), pathPoint.z + 0.5D) <= 1.5D) {
                BlockPos potentialDoorPos = new BlockPos(pathPoint.x, pathPoint.y, pathPoint.z);
                if (findDoor(potentialDoorPos)) {
                    interactWithDoor(potentialDoorPos, true);
                    return true;
                } else if (findDoor(potentialDoorPos.above())) {
                    interactWithDoor(potentialDoorPos.above(), true);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void tick() {
        closeTooFarAwayDoor();
        recheckDoorOpen();
        addDoorCloseOnThePath();
    }

    private void closeTooFarAwayDoor() {
        Iterator<BlockPos> it = doorPositions.iterator();
        while (it.hasNext()) {
            BlockPos doorPos = it.next();
            if (!(isCloseToDoor(doorPos) || isFriendlyInDoor(doorPos))) {
                it.remove();
                if (close) {
                    interactWithDoor(doorPos, false);
                }
            }
        }
    }

    private void addDoorCloseOnThePath() {
        Path path = npc.getNavigation().getPath();
        if (path == null) {
            return;
        }
        addDoorCloseOnThePath(path);
    }

    private void recheckDoorOpen() {
        if (doorCheckCooldown <= 0) {
            doorCheckCooldown = RECHECK_INTERVAL;
            doorPositions.forEach(doorPos -> interactWithDoor(doorPos, true));
        } else {
            doorCheckCooldown--;
        }
    }

    private boolean isFriendlyInDoor(BlockPos doorPos) {
        Vec3 doorCenter = Vec3.atCenterOf(doorPos);
        return !npc.level().getEntitiesOfClass(NpcBase.class, new AABB(doorCenter, doorCenter).inflate(2.1D), n -> n != null && !n.isHostileTowards(npc)).isEmpty();
    }

    private boolean isCloseToDoor(BlockPos doorPos) {
        return doorPos.distToCenterSqr(npc.getX(), npc.getY(), npc.getZ()) <= 2D;
    }

    private boolean isDoor(BlockPos potentialDoorPos) {
        BlockState doorState = npc.level().getBlockState(potentialDoorPos);
        if (doorState.getBlock() instanceof DoorBlock) {
            return doorState.is(BlockTags.WOODEN_DOORS);
        } else {
            return doorState.getBlock() instanceof FenceGateBlock;
        }
    }

    private boolean findDoor(BlockPos potentialDoorPos) {
        if (isDoor(potentialDoorPos) && !doorPositions.contains(potentialDoorPos)) {
            doorPositions.add(potentialDoorPos);
            return true;
        }
        return false;
    }

    private void interactWithDoor(BlockPos doorPos, boolean isOpening) {
        Level level = npc.level();
        BlockState doorState = level.getBlockState(doorPos);
        if (doorState.getBlock() instanceof DoorBlock doorBlock) {
            doorBlock.setOpen(npc, level, doorState, doorPos, isOpening);
        } else if (doorState.getBlock() instanceof FenceGateBlock) {
            interactWithFenceGate(doorPos, isOpening, doorState);
        }
    }

    private void interactWithFenceGate(BlockPos doorPos, boolean isOpening, BlockState doorState) {
        Level level = npc.level();
        boolean fenceGateOpen = doorState.getValue(FenceGateBlock.OPEN);
        if (isOpening) {
            if (fenceGateOpen) {
                return;
            }
            Direction entityFacing = Direction.fromYRot(npc.getYRot());
            openFenceGate(doorState, doorPos, entityFacing);
            BlockState state = level.getBlockState(doorPos.above());
            if (state.getBlock() instanceof FenceGateBlock) {
                openFenceGate(state, doorPos.above(), entityFacing);
            }
        } else {
            boolean changed = fenceGateOpen;
            if (fenceGateOpen) {
                level.setBlock(doorPos, doorState.setValue(FenceGateBlock.OPEN, false), 10);
            }
            BlockState state = level.getBlockState(doorPos.above());
            if (state.getBlock() instanceof FenceGateBlock && state.getValue(FenceGateBlock.OPEN)) {
                level.setBlock(doorPos.above(), state.setValue(FenceGateBlock.OPEN, false), 10);
                changed = true;
            }
            if (!changed) {
                return;
            }
        }
        // 1.12.2: world event 1008 (open) / 1014 (close)
        level.playSound(null, doorPos, isOpening ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 1.0F,
                level.getRandom().nextFloat() * 0.1F + 0.9F);
        level.gameEvent(npc, isOpening ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, doorPos);
    }

    /** Opens the gate and turns it away from the NPC. */
    private void openFenceGate(BlockState state, BlockPos pos, Direction entityFacing) {
        BlockState updatedState = state;
        if (updatedState.getValue(FenceGateBlock.FACING) == entityFacing.getOpposite()) {
            updatedState = updatedState.setValue(FenceGateBlock.FACING, entityFacing);
        }
        updatedState = updatedState.setValue(FenceGateBlock.OPEN, true);
        npc.level().setBlock(pos, updatedState, 10);
    }
}
