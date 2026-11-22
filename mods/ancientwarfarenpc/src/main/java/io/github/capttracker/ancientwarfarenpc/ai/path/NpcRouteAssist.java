/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (playtest fixes): routes over ladders for NPCs that the vanilla ground navigation cannot get anywhere.
 */
package io.github.capttracker.ancientwarfarenpc.ai.path;

import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.Direction.Axis;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Gets an NPC where the vanilla ground navigation cannot: down a mine shaft and back up. The vanilla path finder does not climb ladders, and an NPC never
 * flies, so a worker that has to reach something in a pit or a tunnel (or leave it) follows a route found here instead.
 *
 * <p>The planner is an A* over standing cells (feet block; the cell and the one above must be free, the cell below solid or the cell itself a climbable
 * block). Moves: walk to a neighbour, step up one block, drop up to three blocks, and climb up or down a climbable block (ladder, vine, scaffolding).
 * The goal is a standing cell within {@code reach} of the target: measured from the eyes with a line of sight for work (a worker may work a block within
 * {@link #WORK_REACH}), or from the feet for plain walking.
 *
 * <p>Following the route: stretches on flat ground are walked with the vanilla navigation (to the end of the stretch); every step that has a ladder at
 * either end is done with the move control, which makes the NPC jump while it is in the ladder block (vanilla ladder physics: that climbs) and lets it
 * slide down at the speed of the ladder. There is no flying and no teleport. The route lives while somebody asks for it ({@link #request}) and is dropped
 * a few ticks after the last request; a route that makes no progress is dropped and its goal is left alone for a while ({@link #request} says no).
 */
public final class NpcRouteAssist {
    /** How far (blocks, from the eyes) a worker can work a block. */
    public static final double WORK_REACH = 4.5D;

    private static final int MAX_NODES = 3000;
    private static final int MAX_HORIZONTAL = 72;
    private static final int MAX_VERTICAL = 120;
    private static final int KEEP_TICKS = 12;
    private static final int STUCK_TICKS = 80;
    private static final int FAIL_CACHE_TICKS = 200;
    private static final int MAX_REPLANS = 3;
    private static final int MAX_DROP = 3;

    private final NpcBase npc;

    private List<BlockPos> cells = List.of();
    private int index;
    private boolean active;
    private boolean done;
    private long goalKey;
    private BlockPos target = BlockPos.ZERO;
    private double reach;
    private boolean eyes;
    private boolean los;
    private int lastRequest;
    private int replans;

    private long failedKey;
    private int failedUntil = Integer.MIN_VALUE;

    private double bestSegment = Double.MAX_VALUE;
    private int stuck;
    private BlockPos runTarget = BlockPos.ZERO;
    private int runStarted;

    public NpcRouteAssist(NpcBase npc) {
        this.npc = npc;
    }

    // ---------------------------------------------------------------- use

    /** True while a route is being followed. */
    public boolean isActive() {
        return active;
    }

    /** Drops the route (the goal was reached, changed or somebody else moves the NPC). */
    public void cancel() {
        if (active) {
            active = false;
            cells = List.of();
            index = 0;
            done = false;
            replans = 0;
            npc.getNavigation().stop();
        }
    }

    /**
     * Asks for the NPC to be taken to a standing cell from where the target is within reach, over ladders if need be, and keeps asking for it: call this
     * every tick the goal wants it. The first call (and a call after the route ran out without reaching the goal) plans.
     *
     * @param eyes true to measure the reach from the eyes and (with {@code los}) require a line of sight, false to measure from the feet
     * @return false if there is no route (or the route got stuck): the goal is left alone for a while
     */
    public boolean request(BlockPos target, double reach, boolean eyes, boolean los) {
        int now = npc.tickCount;
        long key = key(target, reach, eyes, los);
        if (key == failedKey && now < failedUntil) {
            return false;
        }
        if (active && key == goalKey && !done) {
            lastRequest = now;
            return true;
        }
        if (active && key == goalKey) {
            // the route ran out and the NPC is still not there: plan again from where it stands
            if (++replans > MAX_REPLANS) {
                fail(key);
                return false;
            }
        } else {
            replans = 0;
        }
        List<BlockPos> route = plan(npc, target, reach, eyes, los, MAX_NODES);
        if (route == null) {
            fail(key);
            return false;
        }
        this.cells = route;
        this.index = 0;
        this.active = true;
        this.done = false;
        this.goalKey = key;
        this.target = target.immutable();
        this.reach = reach;
        this.eyes = eyes;
        this.los = los;
        this.lastRequest = now;
        this.bestSegment = Double.MAX_VALUE;
        this.stuck = 0;
        this.runTarget = BlockPos.ZERO;
        return true;
    }

    /** As {@link #request} for a plain walk to a position (the feet within a block of its cell). */
    public boolean requestMove(double x, double y, double z) {
        return request(BlockPos.containing(x, y, z), 1.0D, false, false);
    }

    private void fail(long key) {
        failedKey = key;
        failedUntil = npc.tickCount + FAIL_CACHE_TICKS;
        cancel();
        replans = 0;
    }

    private static long key(BlockPos target, double reach, boolean eyes, boolean los) {
        long key = target.asLong();
        key = key * 31 + Double.hashCode(reach);
        key = key * 31 + (eyes ? 1 : 0) + (los ? 2 : 0);
        return key;
    }

    /** True if the NPC can work the block from where it stands: within {@code reach} of the eyes, and with a line of sight if {@code los}. */
    public static boolean inReach(NpcBase npc, BlockPos target, double reach, boolean los) {
        Vec3 eye = npc.getEyePosition();
        if (eye.distanceToSqr(Vec3.atCenterOf(target)) > reach * reach) {
            return false;
        }
        return !los || canSee(npc, eye, target);
    }

    private static boolean canSee(NpcBase npc, Vec3 eye, BlockPos target) {
        BlockHitResult hit = npc.level().clip(new ClipContext(eye, Vec3.atCenterOf(target), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, npc));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
    }

    // ---------------------------------------------------------------- following (every server tick, before the move control runs)

    /** One tick of the route; called by the NPC every server tick. */
    public void tick() {
        if (!active) {
            return;
        }
        if (npc.tickCount - lastRequest > KEEP_TICKS || npc.isPassenger() || npc.isSleeping()) {
            cancel();
            return;
        }
        if (done) {
            return;
        }
        List<BlockPos> route = cells;
        int count = route.size();
        int before = index;
        while (index < count && reached(route.get(index), false)) {
            index++;
        }
        if (index != before) {
            bestSegment = Double.MAX_VALUE;
            stuck = 0;
        }
        if (index >= count) {
            done = true;
            npc.getNavigation().stop();
            return;
        }
        BlockPos next = route.get(index);
        BlockPos previous = index > 0 ? route.get(index - 1) : npc.blockPosition();
        BlockPos segmentEnd = next;
        if (manual(previous, next)) {
            manualStep(next);
        } else {
            int end = index;
            while (end + 1 < count && !manual(route.get(end), route.get(end + 1))) {
                end++;
            }
            segmentEnd = route.get(end);
            walk(segmentEnd);
            if (reached(segmentEnd, true)) {
                index = end + 1;
                bestSegment = Double.MAX_VALUE;
                stuck = 0;
                return;
            }
        }
        double distance = npc.distanceToSqr(segmentEnd.getX() + 0.5D, segmentEnd.getY(), segmentEnd.getZ() + 0.5D);
        if (distance < bestSegment - 0.05D) {
            bestSegment = distance;
            stuck = 0;
        } else if (++stuck > STUCK_TICKS) {
            fail(goalKey); // no way forward
        }
    }

    /** Whether the NPC stands in the cell: close to its centre, and at its height. {@code loose} allows a whole block sideways (the end of a walk). */
    private boolean reached(BlockPos cell, boolean loose) {
        double dx = npc.getX() - (cell.getX() + 0.5D);
        double dz = npc.getZ() - (cell.getZ() + 0.5D);
        double limit = loose ? 1.0D : 0.36D;
        return dx * dx + dz * dz <= limit && Math.abs(npc.getY() - cell.getY()) < (loose ? 1.2D : 0.8D);
    }

    /** A step that is not walked with the vanilla navigation: one that starts or ends in a climbable block. */
    private boolean manual(BlockPos from, BlockPos to) {
        Level level = npc.level();
        return isClimbable(level.getBlockState(from)) || isClimbable(level.getBlockState(to));
    }

    private void walk(BlockPos end) {
        var navigation = npc.getNavigation();
        if (!end.equals(runTarget) || navigation.isDone() && npc.tickCount - runStarted > 5 || npc.tickCount - runStarted > 40) {
            runTarget = end.immutable();
            runStarted = npc.tickCount;
            navigation.moveTo(end.getX() + 0.5D, end.getY(), end.getZ() + 0.5D, 1.0D);
        }
    }

    /** One step with a climbable block at either end: the move control makes the NPC jump in the ladder (climbing) or lets it slide down. */
    private void manualStep(BlockPos next) {
        npc.getNavigation().stop();
        runTarget = BlockPos.ZERO;
        double cx = next.getX() + 0.5D;
        double cz = next.getZ() + 0.5D;
        double dx = cx - npc.getX();
        double dz = cz - npc.getZ();
        if (next.getY() < npc.getY() - 0.5D) {
            // down: line up with the cell and let gravity (the slide of the ladder) do the rest
            if (dx * dx + dz * dz > 0.12D) {
                npc.getMoveControl().setWantedPosition(cx, npc.getY(), cz, 1.0D);
            }
        } else {
            npc.getMoveControl().setWantedPosition(cx, next.getY(), cz, 1.0D);
        }
    }

    // ---------------------------------------------------------------- planning

    private static final class Node implements Comparable<Node> {
        final BlockPos pos;
        final double g;
        final double f;
        final Node parent;

        Node(BlockPos pos, double g, double f, Node parent) {
            this.pos = pos;
            this.g = g;
            this.f = f;
            this.parent = parent;
        }

        @Override
        public int compareTo(Node other) {
            return Double.compare(f, other.f);
        }
    }

    /**
     * Plans the route (the cells to stand in, from the NPC's own cell to the goal) or returns null if there is none within the node budget.
     */
    @Nullable
    public static List<BlockPos> plan(NpcBase npc, BlockPos target, double reach, boolean eyes, boolean los, int maxNodes) {
        Level level = npc.level();
        BlockPos start = npc.blockPosition();
        Vec3 goal = Vec3.atCenterOf(target);
        double eyeHeight = npc.getEyeHeight();
        PriorityQueue<Node> open = new PriorityQueue<>();
        Map<Long, Double> best = new HashMap<>();
        Node first = new Node(start, 0.0D, heuristic(start, goal, reach), null);
        open.add(first);
        best.put(start.asLong(), 0.0D);
        int expanded = 0;
        while (!open.isEmpty() && expanded < maxNodes) {
            Node node = open.poll();
            Double known = best.get(node.pos.asLong());
            if (known != null && known < node.g) {
                continue; // a cheaper way to this cell was found since
            }
            expanded++;
            BlockPos cell = node.pos;
            if ((cell.equals(start) || standable(level, cell)) && isGoal(npc, cell, goal, target, reach, eyes, los, eyeHeight)) {
                List<BlockPos> route = new ArrayList<>();
                for (Node n = node; n != null; n = n.parent) {
                    route.add(n.pos);
                }
                Collections.reverse(route);
                return route;
            }
            for (Move move : moves(level, cell)) {
                BlockPos to = move.to;
                if (Math.abs(to.getX() - start.getX()) > MAX_HORIZONTAL || Math.abs(to.getZ() - start.getZ()) > MAX_HORIZONTAL
                        || Math.abs(to.getY() - start.getY()) > MAX_VERTICAL) {
                    continue;
                }
                double g = node.g + move.cost;
                Double old = best.get(to.asLong());
                if (old == null || g < old) {
                    best.put(to.asLong(), g);
                    open.add(new Node(to, g, g + heuristic(to, goal, reach), node));
                }
            }
        }
        return null;
    }

    private static double heuristic(BlockPos cell, Vec3 goal, double reach) {
        return Math.max(0.0D, Math.sqrt(Vec3.atCenterOf(cell).distanceToSqr(goal)) - reach);
    }

    private static boolean isGoal(NpcBase npc, BlockPos cell, Vec3 goal, BlockPos target, double reach, boolean eyes, boolean los, double eyeHeight) {
        if (eyes) {
            Vec3 eye = new Vec3(cell.getX() + 0.5D, cell.getY() + eyeHeight, cell.getZ() + 0.5D);
            return eye.distanceToSqr(goal) <= reach * reach && (!los || canSee(npc, eye, target));
        }
        return Vec3.atCenterOf(cell).distanceToSqr(goal) <= reach * reach;
    }

    private record Move(BlockPos to, double cost) {}

    private static List<Move> moves(Level level, BlockPos c) {
        List<Move> moves = new ArrayList<>(8);
        boolean onLadder = isClimbable(level.getBlockState(c));
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos n = c.relative(dir);
            if (standable(level, n)) {
                moves.add(new Move(n, 1.0D));
                continue;
            }
            // one block up: the cell beside is blocked (or not floored) and the one above it can be stood in; the head must clear the jump
            BlockPos up = n.above();
            if (!passable(level, n) && standable(level, up) && passable(level, c.above(2))) {
                moves.add(new Move(up, 1.6D));
                continue;
            }
            // a drop beside: up to MAX_DROP blocks
            if (passable(level, n) && passable(level, n.above())) {
                for (int k = 1; k <= MAX_DROP; k++) {
                    BlockPos m = n.below(k);
                    if (standable(level, m)) {
                        moves.add(new Move(m, 1.0D + 0.5D * k));
                        break;
                    }
                    if (!passable(level, m)) {
                        break;
                    }
                }
            }
        }
        if (onLadder) {
            BlockPos above = c.above();
            if (standable(level, above) && isClimbable(level.getBlockState(above))) {
                moves.add(new Move(above, 1.2D));
            }
        }
        // straight down: a ladder below, or a fall to the floor
        BlockPos below = c.below();
        if (passable(level, below)) {
            if (standable(level, below)) {
                moves.add(new Move(below, isClimbable(level.getBlockState(below)) ? 1.1D : 1.5D));
            } else {
                for (int k = 2; k <= MAX_DROP; k++) {
                    BlockPos m = c.below(k);
                    if (standable(level, m)) {
                        moves.add(new Move(m, 1.0D + 0.5D * k));
                        break;
                    }
                    if (!passable(level, m)) {
                        break;
                    }
                }
            }
        }
        return moves;
    }

    // ---------------------------------------------------------------- the world

    private static boolean isClimbable(BlockState state) {
        return state.is(BlockTags.CLIMBABLE);
    }

    private static boolean loaded(Level level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.isInWorldBounds(pos);
    }

    /** True if the NPC may be in the block: not a fluid or a hazard, and nothing solid (a door or a gate counts as free, the NPC opens it). */
    static boolean passable(Level level, BlockPos pos) {
        if (!loaded(level, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.getFluidState().isEmpty() || hazard(state)) {
            return false;
        }
        if (isClimbable(state)) {
            return true;
        }
        if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock || state.getBlock() instanceof TrapDoorBlock) {
            return true;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        return shape.isEmpty() || shape.max(Axis.Y) <= 0.5D;
    }

    private static boolean hazard(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.COBWEB)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.MAGMA_BLOCK) || state.is(BlockTags.CAMPFIRES);
    }

    /** The cell below holds the NPC up: something with a collision shape that is not a climbable block, and no hazard. */
    private static boolean supported(Level level, BlockPos pos) {
        BlockPos below = pos.below();
        if (!loaded(level, below)) {
            return false;
        }
        BlockState state = level.getBlockState(below);
        return !isClimbable(state) && state.getFluidState().isEmpty() && !hazard(state) && !state.getCollisionShape(level, below).isEmpty();
    }

    /** A cell the NPC can stand in: it and the cell above are free, and it is floored or a climbable block (which holds the NPC by itself). */
    static boolean standable(Level level, BlockPos pos) {
        return passable(level, pos) && passable(level, pos.above()) && (isClimbable(level.getBlockState(pos)) || supported(level, pos));
    }
}
