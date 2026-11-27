/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 12): the shield of the mantlet.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.siege;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSettings;
import io.github.capttracker.ancientwarfarevehicle.missiles.MissileBase;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The shield of a mantlet: a rectangle 3 blocks wide and 2.5 high standing across the heading of the vehicle, 0.55 blocks in front of its middle. Arrows, bolts and missiles are not tested
 * against the box of the mantlet (that is not hittable by projectiles, see {@code VehicleBase#canBeHitByProjectile}); instead, every tick the mantlet looks at the projectiles near it and
 * stops each one whose last step went through the rectangle from its front side to its back side. The projectile is removed (a missile first has its impact on the mantlet, so that
 * explosive ammunition goes off at the shield) and the mantlet takes damage from it. A projectile that crosses the plane from behind (an archer standing behind the mantlet), passes
 * beside the rectangle or over its top is not touched. Server side.
 */
public final class MantletShield {
    /** Half the width and the height of the shield, and how far in front of the middle of the vehicle it stands, in blocks. */
    public static final double HALF_WIDTH = 1.5D;
    public static final double HEIGHT = 2.5D;
    public static final double PLANE = 0.55D;
    /** Projectiles within this many blocks are looked at. */
    private static final double REACH = 10.0D;

    private MantletShield() {}

    public static void tick(VehicleBase mantlet) {
        if (mantlet.level().isClientSide || !VehicleSettings.mantletBlocksProjectiles || mantlet.getHealth() <= 0.F || mantlet.isRemoved()) {
            return;
        }
        AABB box = new AABB(mantlet.getX() - REACH, mantlet.getY() - 2.0D, mantlet.getZ() - REACH, mantlet.getX() + REACH, mantlet.getY() + 6.0D, mantlet.getZ() + REACH);
        List<Entity> found = mantlet.level().getEntities(mantlet, box, e -> (e instanceof Projectile || e instanceof MissileBase) && !e.isRemoved() && e.tickCount > 0);
        if (found.isEmpty()) {
            return;
        }
        double yaw = Math.toRadians(mantlet.getYRot());
        // forward is (-sin, -cos) of the yaw, the side is (cos, -sin), as for every vehicle
        double fx = -Math.sin(yaw);
        double fz = -Math.cos(yaw);
        double sx = Math.cos(yaw);
        double sz = -Math.sin(yaw);
        for (Entity projectile : found) {
            if (projectile instanceof MissileBase missile && (missile.isInGround() || missile.launcher == mantlet)) {
                continue;
            }
            if (projectile instanceof Projectile shot && shot.getOwner() != null && shot.getOwner().getVehicle() == mantlet) {
                continue;
            }
            Vec3 from = new Vec3(projectile.xo, projectile.yo, projectile.zo);
            Vec3 to = projectile.position();
            double ax = from.x - mantlet.getX();
            double az = from.z - mantlet.getZ();
            double bx = to.x - mantlet.getX();
            double bz = to.z - mantlet.getZ();
            double fromForward = ax * fx + az * fz;
            double toForward = bx * fx + bz * fz;
            if (!(fromForward > PLANE && toForward <= PLANE)) {
                continue; // not coming through the front
            }
            double t = (fromForward - PLANE) / (fromForward - toForward);
            double hitX = from.x + (to.x - from.x) * t;
            double hitY = from.y + (to.y - from.y) * t;
            double hitZ = from.z + (to.z - from.z) * t;
            double side = (hitX - mantlet.getX()) * sx + (hitZ - mantlet.getZ()) * sz;
            double up = hitY - mantlet.getY();
            if (Math.abs(side) <= HALF_WIDTH && up >= -0.1D && up <= HEIGHT) {
                stop(mantlet, projectile, new Vec3(hitX, hitY, hitZ));
            }
        }
    }

    private static void stop(VehicleBase mantlet, Entity projectile, Vec3 at) {
        ServerLevel level = (ServerLevel) mantlet.level();
        float damage;
        DamageSource source;
        if (projectile instanceof MissileBase missile) {
            // the missile's own effect on what it hits (damage, explosion) is applied to the mantlet at the shield
            missile.onImpactEntity(mantlet, (float) at.x, (float) at.y, (float) at.z);
            projectile.discard();
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.2D, 0.2D, 0.2D, 0.1D);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.WOOD_HIT, SoundSource.NEUTRAL, 1.2F, 0.8F);
            return;
        }
        Projectile shot = (Projectile) projectile;
        if (shot instanceof AbstractArrow arrow) {
            double speed = arrow.getDeltaMovement().length();
            damage = (float) Math.ceil(Math.min(speed * arrow.getBaseDamage(), 20.0D));
            source = level.damageSources().arrow(arrow, shot.getOwner());
            if (arrow.isOnFire()) {
                mantlet.igniteForSeconds(4.0F);
            }
        } else {
            damage = 3.0F;
            source = level.damageSources().thrown(shot, shot.getOwner());
        }
        mantlet.ramHurt(source, damage * (float) VehicleSettings.mantletDamageFactor);
        shot.discard();
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 4, 0.1D, 0.1D, 0.1D, 0.05D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ARROW_HIT, SoundSource.NEUTRAL, 1.0F, 0.9F);
    }
}
