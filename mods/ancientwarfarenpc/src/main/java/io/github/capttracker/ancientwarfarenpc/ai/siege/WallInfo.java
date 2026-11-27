/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 12): what the NPC siege AI knows about the wall an order is aimed at.
 */
package io.github.capttracker.ancientwarfarenpc.ai.siege;

import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarevehicle.entity.IGateEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;

/**
 * The target of a siege order: the block the baton was aimed at, the standing height on top of its column of solid blocks ({@code topY}) and the gate next to it, if any
 * (an Ancient Warfare gate entity, or a door or fence gate block).
 *
 * @param pos        the block the order was aimed at
 * @param topY       the y of the standing level on top of the wall (the first free block over the wall column)
 * @param gateEntity the UUID of the nearest gate entity within 2.5 blocks of the target, or null
 * @param gateBlock  the nearest door or fence gate block within 2 blocks of the target (only when there is no gate entity), or null
 */
public record WallInfo(BlockPos pos, int topY, @Nullable UUID gateEntity, @Nullable BlockPos gateBlock) {
    /** The side of the wall the siege equipment comes to, and the standing level of the ground there. */
    public record Approach(Direction normal, int groundY) {}

    public boolean hasGate() {
        return gateEntity != null || gateBlock != null;
    }

    public Vec3 center() {
        return new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
    }

    /**
     * Looks at the target. Returns null if it is not a wall: fewer than {@code siege_wall_min_height} solid blocks from the target up, and no gate next to it.
     */
    @Nullable
    public static WallInfo analyse(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        int up = columnHeight(level, pos);
        int topY = pos.getY() + up;
        UUID gateEntity = null;
        BlockPos gateBlock = null;
        AABB box = new AABB(pos).inflate(2.5D, 2.0D, 2.5D);
        Vec3 center = Vec3.atCenterOf(pos);
        Entity nearest = level.getEntities((Entity) null, box, e -> e instanceof IGateEntity && e.isAlive()).stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(center))).orElse(null);
        if (nearest != null) {
            gateEntity = nearest.getUUID();
        } else {
            double best = Double.MAX_VALUE;
            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 2, 2))) {
                BlockState state = level.getBlockState(p);
                if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock) {
                    double d = p.distSqr(pos);
                    if (d < best) {
                        best = d;
                        gateBlock = p.immutable();
                    }
                }
            }
        }
        if (up < SettlementConfig.siegeWallMinHeight && gateEntity == null && gateBlock == null) {
            return null;
        }
        if (up < SettlementConfig.siegeWallMinHeight) {
            // the order is aimed at a gate: the wall around it gives the height
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos around = pos.offset(dx, 0, dz);
                    if (level.isLoaded(around) && isSolid(level, around)) {
                        topY = Math.max(topY, around.getY() + columnHeight(level, around));
                    }
                }
            }
        }
        return new WallInfo(pos.immutable(), topY, gateEntity, gateBlock);
    }

    private static int columnHeight(Level level, BlockPos pos) {
        int up = 0;
        BlockPos.MutableBlockPos m = pos.mutable();
        while (up < 48 && level.isLoaded(m) && isSolid(level, m)) {
            up++;
            m.move(0, 1, 0);
        }
        return up;
    }

    static boolean isSolid(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    static boolean isPassable(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /**
     * The side of the wall facing {@code from} (the four horizontal directions, the one pointing most at {@code from} first) with a free column in front of the target,
     * and the standing level of the ground there. Null if the wall has no open side.
     */
    @Nullable
    public Approach approach(Level level, Vec3 from) {
        double dx = from.x - (pos.getX() + 0.5D);
        double dz = from.z - (pos.getZ() + 0.5D);
        Direction[] dirs = Direction.Plane.HORIZONTAL.stream().toArray(Direction[]::new);
        Arrays.sort(dirs, Comparator.comparingDouble((Direction d) -> -(d.getStepX() * dx + d.getStepZ() * dz)));
        for (Direction d : dirs) {
            BlockPos out = pos.relative(d);
            if (level.isLoaded(out) && isPassable(level, out) && isPassable(level, out.above())) {
                int y = out.getY();
                int floor = y;
                for (int i = 0; i < 14; i++) {
                    if (isSolid(level, new BlockPos(out.getX(), y - i - 1, out.getZ()))) {
                        floor = y - i;
                        break;
                    }
                }
                return new Approach(d, floor);
            }
        }
        return null;
    }

    /** The gate entity, or null if there is none (any more). */
    @Nullable
    public Entity findGateEntity(net.minecraft.server.level.ServerLevel level) {
        return gateEntity == null ? null : level.getEntity(gateEntity);
    }
}
