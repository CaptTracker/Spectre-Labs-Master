/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/NpcAI.
 */
package io.github.capttracker.ancientwarfarenpc.ai;

import io.github.capttracker.ancientwarfarenpc.ai.path.NpcRouteAssist;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * AI template class with utility methods and member access for a non-specific NPC type.
 *
 * <h2>Changes from 1.12.2</h2>
 * <ul>
 *   <li>The base class is {@link Goal}: {@code shouldExecute} is {@code canUse}, {@code startExecuting} is {@code start},
 *       {@code updateTask} is {@code tick} and {@code resetTask} is {@code stop}. A goal keeps running while {@code canUse} stays
 *       true (the default {@code canContinueToUse}), as {@code shouldContinueExecuting} did.</li>
 *   <li>Mutex bits. The AW constants {@link #MOVE}, {@link #ATTACK}, {@link #SWIM} and {@link #HUNGRY} keep their values and keep
 *       their meaning for the NPC's "stop control flags" ({@link NpcBase#stopAIControlFlag}). {@link #setMutexBits(int)} also
 *       maps them to {@link Goal.Flag}: MOVE to MOVE, ATTACK to LOOK (the numeric bit of the 1.12.2 mutex collided with the vanilla
 *       look bit, so the attack goals always locked the head), SWIM to JUMP, HUNGRY is dropped (the priorities already separate the
 *       two goals that used it).</li>
 *   <li>Every NPC goal ticks each game tick ({@link #requiresUpdateEveryTick()}); vanilla 1.21.1 would run a goal only every second tick.
 *       The start checks run every second tick ({@code Mob.serverAiStep}), 1.12.2 checked every third tick.</li>
 *   <li>The maximum path finding distance is read from the follow range when it is used, not when the goal is created:
 *       goals are created inside {@code Mob}'s constructor, before the NPC defaults set the follow range.</li>
 *   <li>Positions given as {@link BlockPos} measure the distance to the corner of the block, as {@code Entity.getDistanceSq(BlockPos)} did
 *       ({@link #distanceSq(BlockPos)}).</li>
 * </ul>
 */
public abstract class NpcAI<T extends NpcBase> extends Goal {
    /*
     * used during npc-ai task-rendering to determine how many bits to loop through
     * of the task bitfield -- needs to be increased if more task types / bits are added
     */
    public static final int NUMBER_OF_TASKS = 14;
    public static final int TASK_ATTACK = 1;
    public static final int TASK_UPKEEP = 2;
    public static final int TASK_IDLE_HUNGRY = 4;
    public static final int TASK_GO_HOME = 8;
    public static final int TASK_WORK = 16;
    public static final int TASK_PATROL = 32;
    public static final int TASK_GUARD = 64;
    public static final int TASK_FOLLOW = 128;
    public static final int TASK_WANDER = 256;
    public static final int TASK_MOVE = 512;
    public static final int TASK_ALARM = 1024;
    public static final int TASK_FLEE = 2048;
    public static final int TASK_SLEEP = 4096;
    public static final int TASK_RAIN = 8192;

    /*
     * internal flag used to determine exclusion types
     */
    public static final int MOVE = 1;
    public static final int ATTACK = 2;
    public static final int SWIM = 4;
    public static final int HUNGRY = 8;

    public static final int MIN_RANGE = 9;

    private static final double DEFAULT_FOLLOW_RANGE = 16.0D;

    protected int moveRetryDelay;
    protected double moveSpeed = 1.d;
    protected final T npc;
    private int mutexBits;

    public NpcAI(T npc) {
        this.npc = npc;
    }

    /**
     * Sets the AW mutex bits (a combination of {@link #MOVE}, {@link #ATTACK}, {@link #SWIM}, {@link #HUNGRY}) and the goal flags they map to.
     */
    protected final void setMutexBits(int bits) {
        mutexBits = bits;
        EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);
        if ((bits & MOVE) != 0) {
            flags.add(Flag.MOVE);
        }
        if ((bits & ATTACK) != 0) {
            flags.add(Flag.LOOK);
        }
        if ((bits & SWIM) != 0) {
            flags.add(Flag.JUMP);
        }
        setFlags(flags);
    }

    protected final int getMutexBits() {
        return mutexBits;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private double getMaxPathFindDistance() {
        AttributeInstance range = npc.getAttribute(Attributes.FOLLOW_RANGE);
        double base = range == null ? DEFAULT_FOLLOW_RANGE : range.getBaseValue();
        if (npc instanceof NpcPlayerOwned owned && owned.getSettlementId() != null) {
            base = Math.max(base, SettlementConfig.maxPathRange); // rework phase 2: settlement NPCs plan longer routes
        }
        return base * 0.90d;
    }

    /** The squared distance from the NPC to the corner of the block (1.12.2: {@code getDistanceSq(BlockPos)}). */
    protected final double distanceSq(BlockPos pos) {
        return npc.distanceToSqr(pos.getX(), pos.getY(), pos.getZ());
    }

    protected final void moveToEntity(Entity target, double sqDist) {
        moveToPosition(target.getX(), target.getBoundingBox().minY, target.getZ(), sqDist);
    }

    protected final void moveToPosition(int x, int y, int z, double sqDist) {
        moveToPosition(x + 0.5d, y, z + 0.5d, sqDist);
    }

    protected final void moveToPosition(BlockPos pos, double sqDist) {
        moveToPosition(pos.getX(), pos.getY(), pos.getZ(), sqDist);
    }

    protected final void forceMoveToPosition(BlockPos pos, double sqDist) {
        moveRetryDelay = 0;
        moveToPosition(pos, sqDist);
    }

    /**
     * The one movement entry point of the goals. The path is only recomputed when the retry delay (5, 10 or 20 ticks by distance, 30 for the
     * long distance steps; it counts down by 10 per call) has run out.
     */
    protected final void moveToPosition(double x, double y, double z, double sqDist) {
        moveRetryDelay -= 10;
        if (moveRetryDelay <= 0) {
            double maxPFDist = getMaxPathFindDistance();
            if (sqDist > maxPFDist * maxPFDist) {
                moveLongDistance(x, y, z);
                moveRetryDelay = 30;
            } else {
                setPath(x, y, z);
                moveRetryDelay = 5;
                if (sqDist > 256) {
                    moveRetryDelay += 5;
                }
                if (sqDist > 1024) {
                    moveRetryDelay += 10;
                }
            }
        }
    }

    @Override
    public boolean canUse() {
        return npc.getIsAIEnabled() && !npc.isAIFlagStopped(getMutexBits());
    }

    /** Hop offsets tried for a long trip: the step as a fraction of the path range, and the turn away from the straight line in degrees. */
    private static final double[][] HOPS = {{1.0, 0}, {1.0, 40}, {1.0, -40}, {0.5, 0}, {0.5, 70}, {0.5, -70}};

    /**
     * Steers towards a target that is further away than the path finder can search, through an intermediate waypoint 0.9 times the path range
     * away. The waypoint is set on the ground (the height map, as the straight line may end in a wall or in the air); if there is no path to it the
     * step is turned aside or halved, and only if every try fails the plain straight-line point is used, as in 1.12.2.
     */
    protected final void moveLongDistance(double x, double y, double z) {
        double dx = x - npc.getX();
        double dz = z - npc.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double step = getMaxPathFindDistance();
        if (horizontal > 1.0E-3 && npc.level().isLoaded(npc.blockPosition())) {
            double baseAngle = Math.atan2(dz, dx);
            Path fallback = null;
            for (double[] hop : HOPS) {
                double angle = baseAngle + Math.toRadians(hop[1]);
                double length = Math.min(step * hop[0], horizontal);
                double wx = npc.getX() + Math.cos(angle) * length;
                double wz = npc.getZ() + Math.sin(angle) * length;
                BlockPos column = BlockPos.containing(wx, npc.getY(), wz);
                if (!npc.level().isLoaded(column)) {
                    continue;
                }
                int wy = npc.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).getY();
                if (Math.abs(wy - npc.getY()) > step) {
                    continue;
                }
                Path path = trimPath(npc.getNavigation().createPath(wx, wy, wz, 1));
                if (path != null && path.getNodeCount() > 0) {
                    if (path.canReach()) {
                        npc.getNavigation().moveTo(path, moveSpeed);
                        return;
                    }
                    if (fallback == null) {
                        fallback = path; // a partial path towards the first waypoint
                    }
                }
            }
            if (fallback != null) {
                npc.getNavigation().moveTo(fallback, moveSpeed);
                return;
            }
        }
        Vec3 vec = new Vec3(x - npc.getX(), y - npc.getY(), z - npc.getZ());
        double w = vec.length();
        if (w != 0) {
            vec = vec.scale(1d / w);
        }
        vec = vec.scale(step).add(npc.getX(), npc.getY(), npc.getZ());
        setPath(vec.x, vec.y, vec.z);
    }

    protected final void returnHome() {
        setPath(npc.getHomePosition());
    }

    /**
     * Paths to the position and starts following the path at {@link #moveSpeed}. The navigation is the one of the mount if the NPC
     * rides one ({@code Mob.getNavigation}).
     */
    protected final void setPath(double x, double y, double z) {
        PathNavigation navigation = npc.getNavigation();
        Path path = trimPath(navigation.createPath(x, y, z, 1));
        startPath(path, x, y, z);
    }

    protected final void setPath(BlockPos pos) {
        PathNavigation navigation = npc.getNavigation();
        Path path = trimPath(navigation.createPath(pos, 1));
        startPath(path, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
    }

    /**
     * Follows the path. An NPC that has no way on foot to a place at another height (down a mine shaft: vanilla navigation does not climb ladders) gets a
     * route over the ladders instead ({@link NpcRouteAssist}); a way on foot always wins.
     */
    private void startPath(@Nullable Path path, double x, double y, double z) {
        NpcRouteAssist assist = npc.getRouteAssist();
        if ((path == null || !path.canReach()) && Math.abs(y - npc.getY()) >= 2.0D && assist.requestMove(x, y, z)) {
            return; // the route moves the NPC
        }
        assist.cancel();
        npc.getNavigation().moveTo(path, moveSpeed);
    }

    /**
     * Cuts the path at the first node with a negative walk target value (lava or a cactus below, see {@code NpcBase.getWalkTargetValue}); if the
     * node to walk to next is already bad, a path to a random position away from the NPC replaces it.
     */
    @Nullable
    protected Path trimPath(@Nullable Path path) {
        if (path == null || path.getNodeCount() == 0) {
            return path;
        }
        int index = path.getNextNodeIndex();
        if (index >= path.getNodeCount()) {
            return path;
        }
        Node node = path.getNode(index);
        if (npc.getWalkTargetValue(new BlockPos(node.x, node.y, node.z)) >= 0) {
            for (int i = index + 1; i < path.getNodeCount(); i++) {
                node = path.getNode(i);
                if (npc.getWalkTargetValue(new BlockPos(node.x, node.y, node.z)) < 0) {
                    path.truncateNodes(i - 1);
                    break;
                }
            }
        } else {
            Vec3 vec = DefaultRandomPos.getPosAway(npc, MIN_RANGE, MIN_RANGE, npc.position());
            if (vec != null) {
                return npc.getNavigation().createPath(vec.x, vec.y, vec.z, 1);
            }
        }
        return path;
    }
}
