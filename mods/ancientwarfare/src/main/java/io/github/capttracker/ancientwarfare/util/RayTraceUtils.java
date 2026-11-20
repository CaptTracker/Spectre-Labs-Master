/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/util/RayTraceUtils.
 */
package io.github.capttracker.ancientwarfare.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Ray traces against entities and sets of boxes. Vanilla's {@code ProjectileUtil} and {@code Level#clip}
 * cover the common single-trace cases; this keeps the original's "entity first, then block" trace, which
 * the vehicles and NPC code call, and the multi-box trace for blocks with several collision boxes.
 */
public final class RayTraceUtils {
    private RayTraceUtils() {}

    /** @return the entity or block the player is looking at within the range, or null */
    @Nullable
    public static HitResult getPlayerTarget(Player player, float range, float border) {
        Set<Entity> excluded = new HashSet<>();
        excluded.add(player);
        if (player.getVehicle() != null) {
            excluded.add(player.getVehicle());
        }
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(range));
        return tracePath(player.level(), start.x, start.y, start.z, end.x, end.y, end.z, border, excluded);
    }

    @Nullable
    public static HitResult tracePathWithYawPitch(Level world, float x, float y, float z, float yaw, float pitch, float range,
                                                  float borderSize, Set<Entity> excluded) {
        float tx = x + (Trig.sinDegrees(yaw + 180) * range * Trig.cosDegrees(pitch));
        float ty = (-Trig.sinDegrees(pitch) * range) + y;
        float tz = z + (Trig.cosDegrees(yaw) * range * Trig.cosDegrees(pitch));
        return tracePath(world, x, y, z, tx, ty, tz, borderSize, excluded);
    }

    /**
     * @param x          start X
     * @param y          start Y
     * @param z          start Z
     * @param tx         end X
     * @param ty         end Y
     * @param tz         end Z
     * @param borderSize extra area to examine around the line for entities
     * @param excluded   any excluded entities (the player, etc)
     * @return the block hit (no entity hit), the entity hit (an {@link EntityHitResult}), or null for nothing hit
     */
    @Nullable
    public static HitResult tracePath(Level world, double x, double y, double z, double tx, double ty, double tz, float borderSize,
                                      Set<Entity> excluded) {
        AABB bb = new AABB(Math.min(x, tx), Math.min(y, ty), Math.min(z, tz), Math.max(x, tx), Math.max(y, ty), Math.max(z, tz))
                .inflate(borderSize);
        Vec3 startVec = new Vec3(x, y, z);
        Vec3 endVec = new Vec3(tx, ty, tz);

        Entity closestHitEntity = null;
        Vec3 closestHitVec = null;
        double closestHit = Double.POSITIVE_INFINITY;
        for (Entity ent : world.getEntities((Entity) null, bb)) {
            if (ent.isPickable() && !excluded.contains(ent)) {
                float entBorder = ent.getPickRadius();
                Optional<Vec3> intercept = ent.getBoundingBox().inflate(entBorder).clip(startVec, endVec);
                if (intercept.isPresent()) {
                    double currentHit = intercept.get().distanceTo(startVec);
                    if (currentHit < closestHit || currentHit == 0) {
                        closestHit = currentHit;
                        closestHitEntity = ent;
                        closestHitVec = intercept.get();
                    }
                }
            }
        }
        if (closestHitEntity != null) {
            return new EntityHitResult(closestHitEntity, closestHitVec);
        }
        BlockHitResult blockHit = world.clip(new ClipContext(startVec, endVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        return blockHit.getType() == HitResult.Type.MISS ? null : blockHit;
    }

    /** The closest hit of the ray against any of the boxes (given relative to the block position), or null. */
    @Nullable
    public static BlockHitResult raytraceMultiAABB(List<AABB> aabbs, BlockPos pos, Vec3 start, Vec3 end) {
        return raytraceMultiAABB(aabbs, pos, start, end, (hit, box) -> hit);
    }

    /**
     * Like {@link #raytraceMultiAABB(List, BlockPos, Vec3, Vec3)} but returns a value computed from the closest hit
     * and the box that was hit.
     */
    @Nullable
    public static <T> T raytraceMultiAABB(List<AABB> aabbs, BlockPos pos, Vec3 start, Vec3 end, BiFunction<BlockHitResult, AABB, T> getValue) {
        List<BlockHitResult> list = new ArrayList<>();

        for (AABB aabb : aabbs) {
            list.add(rayTraceAABBIntercept(pos, start, end, aabb));
        }

        T ret = null;
        double closestHit = Integer.MAX_VALUE;

        for (int i = 0; i < list.size(); i++) {
            BlockHitResult result = list.get(i);
            if (result != null) {
                double distance = result.getLocation().distanceToSqr(start);

                if (distance < closestHit) {
                    ret = getValue.apply(result, aabbs.get(i));
                    closestHit = distance;
                }
            }
        }

        return ret;
    }

    /** Vanilla's multi-box clip moves the boxes (given relative to the block) to the position itself. */
    @Nullable
    private static BlockHitResult rayTraceAABBIntercept(BlockPos pos, Vec3 start, Vec3 end, AABB boundingBox) {
        return AABB.clip(List.of(boundingBox), start, end, pos);
    }

    /**
     * The box the player is pointing at (offset to the block position), or the first box if none is hit.
     * Replaces the CodeChickenLib start and end vector helpers with the player's eye and reach.
     */
    public static AABB getSelectedBoundingBox(List<AABB> aabbs, BlockPos pos, Player player) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(player.blockInteractionRange()));
        AABB box = raytraceMultiAABB(aabbs, pos, start, end, (hit, aabb) -> aabb);
        if (box == null) {
            box = aabbs.get(0);
        }
        return box.move(pos);
    }
}
