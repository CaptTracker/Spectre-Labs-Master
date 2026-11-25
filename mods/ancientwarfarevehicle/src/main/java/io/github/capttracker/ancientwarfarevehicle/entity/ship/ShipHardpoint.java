/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): the state of one turret mount of a ship.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.ship;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * One turret mount: where its weapon points ({@code yaw} in degrees from the bow, positive towards the side of negative horizontal as a vehicle yaw is;
 * {@code pitch} is the elevation), where the gunner wants it to point, and the reload and the wind-up of its shot. The server and every client run
 * {@link #tick}, so a turret turns smoothly on both; the gunner's aim reaches the server by a payload and is sent on to the tracking clients.
 */
public final class ShipHardpoint {
    public final int index;
    public final ShipSpec.Hardpoint spec;

    public float yaw;
    public float yawPrev;
    public float pitch;
    public float pitchPrev;
    float destYaw;
    float destPitch;
    /** Ticks until the next shot is possible, and ticks until the shot that was ordered leaves the barrel. */
    int reload = 0;
    int windup = 0;
    /** CLIENT and server: how far back the weapon is from its shot (1 at the shot, then down to 0), for the model. */
    public float recoil = 0.F;
    public float recoilPrev = 0.F;
    @Nullable
    LivingEntity shooter = null;

    ShipHardpoint(int index, ShipSpec.Hardpoint spec) {
        this.index = index;
        this.spec = spec;
        this.yaw = spec.centerYaw();
        this.yawPrev = yaw;
        this.destYaw = yaw;
        this.pitch = Math.max(0.F, spec.pitchMin());
        this.pitchPrev = pitch;
        this.destPitch = pitch;
    }

    public float turnRate() {
        return spec.weapon() == ShipSpec.Weapon.CANNON ? 2.5F : 4.0F;
    }

    public int windupTicks() {
        return spec.weapon() == ShipSpec.Weapon.CANNON ? 10 : 3;
    }

    /** Limits a yaw (from the bow) to the arc of the mount. */
    public float clampYaw(float wanted) {
        if (spec.arc() >= 180.0F) {
            return Mth.wrapDegrees(wanted);
        }
        float rel = Mth.wrapDegrees(wanted - spec.centerYaw());
        return Mth.wrapDegrees(spec.centerYaw() + Mth.clamp(rel, -spec.arc(), spec.arc()));
    }

    public float clampPitch(float wanted) {
        return Mth.clamp(wanted, spec.pitchMin(), spec.pitchMax());
    }

    void setDestination(float wantedYaw, float wantedPitch) {
        this.destYaw = clampYaw(wantedYaw);
        this.destPitch = clampPitch(wantedPitch);
    }

    /** CLIENT: the local gunner's wish, applied at once so his own turret does not wait for the server. */
    public void setClientDestination(float wantedYaw, float wantedPitch) {
        setDestination(wantedYaw, wantedPitch);
    }

    /** True if the mount can take a new shot: reloaded, and no shot is winding up. */
    public boolean isReady() {
        return reload <= 0 && windup <= 0;
    }

    /** True if the weapon points at its destination (within 3 degrees). */
    public boolean onTarget() {
        return Math.abs(Mth.wrapDegrees(destYaw - yaw)) < 3.0F && Math.abs(destPitch - pitch) < 3.0F;
    }

    /** The place in the arc a mount points at while nobody aims it. */
    public float getDestYaw() {
        return destYaw;
    }

    public float getDestPitch() {
        return destPitch;
    }

    void tick() {
        yawPrev = yaw;
        pitchPrev = pitch;
        recoilPrev = recoil;
        float rate = turnRate();
        float diff = Mth.wrapDegrees(destYaw - yaw);
        yaw = Mth.wrapDegrees(yaw + Mth.clamp(diff, -rate, rate));
        pitch += Mth.clamp(destPitch - pitch, -rate, rate);
        if (recoil > 0.F) {
            recoil = Math.max(0.F, recoil - 0.08F);
        }
        if (reload > 0) {
            reload--;
        }
    }

    void save(CompoundTag tag) {
        tag.putFloat("yaw", yaw);
        tag.putFloat("pitch", pitch);
        tag.putFloat("dyaw", destYaw);
        tag.putFloat("dpitch", destPitch);
        tag.putInt("reload", reload);
    }

    void load(CompoundTag tag) {
        if (tag.contains("yaw")) {
            yaw = tag.getFloat("yaw");
            pitch = tag.getFloat("pitch");
            destYaw = tag.getFloat("dyaw");
            destPitch = tag.getFloat("dpitch");
            reload = tag.getInt("reload");
            yawPrev = yaw;
            pitchPrev = pitch;
        }
    }
}
