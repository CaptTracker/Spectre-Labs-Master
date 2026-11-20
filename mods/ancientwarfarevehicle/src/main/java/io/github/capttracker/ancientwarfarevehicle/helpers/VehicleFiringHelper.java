/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/helpers/VehicleFiringHelper.
 */
package io.github.capttracker.ancientwarfarevehicle.helpers;

import io.github.capttracker.ancientwarfare.util.MathUtils;
import io.github.capttracker.ancientwarfare.util.Trig;
import io.github.capttracker.ancientwarfarevehicle.entity.IAimTarget;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleMovementType;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSettings;
import io.github.capttracker.ancientwarfarevehicle.init.AwVehicles;
import io.github.capttracker.ancientwarfarevehicle.missiles.IAmmo;
import io.github.capttracker.ancientwarfarevehicle.missiles.MissileBase;
import io.github.capttracker.ancientwarfarevehicle.network.VehicleEntityPayloads;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Random;

/**
 * Handles aiming, firing, updating the turret, and the client/server communication of aim and fire input.
 */
public class VehicleFiringHelper implements INBTSerializable<CompoundTag> {
    private static final int TRAJECTORY_ITERATIONS_CLIENT = 20;
    private static final float NEGLIGIBLE_ANGLE_DIFFERENCE = 0.35f;

    protected static final Random rng = new Random();

    /** Updated when the client chooses an aim point, used by the overlay rendering. */
    public float clientHitRange = 0.f;
    public float clientHitPosX = 0.f;
    public float clientHitPosY = 0.f;
    public float clientHitPosZ = 0.f;

    /** Client-side values used by the riding player to check the current input against the previous to see if new input packets should be sent. */
    public float clientTurretYaw = 0.f;
    public float clientTurretPitch = 0.f;
    public float clientLaunchSpeed = 0.f;

    /** Used on the final launch, to calculate the final angle from the 'approximate' firing arm/turret angle. */
    @Nullable
    public Vec3 targetPos = null;

    /** Is this vehicle in the process of launching a missile (animation and so on)? */
    private boolean isFiring = false;
    /** Has started launching. */
    private boolean isLaunching = false;
    /** If this vehicle was firing, has it already launched and is now cooling down? */
    private boolean isReloading = false;
    /** How many ticks until this vehicle is done reloading and can fire again. */
    private int reloadingTicks = 0;

    private final VehicleBase vehicle;

    public VehicleFiringHelper(VehicleBase vehicle) {
        this.vehicle = vehicle;
    }

    /**
     * Spawns the number of missiles that this vehicle should fire by weight count, at the given offset from the
     * normal missile spawn position (the offset is in world coordinates and needs translating before it is passed in).
     */
    public void spawnMissilesByWeightCount(float ox, float oy, float oz) {
        int count = getMissileLaunchCount();
        for (int i = 0; i < count; i++) {
            spawnMissile(ox, oy, oz);
        }
    }

    /** Spawns a missile of the current missile type with the current firing parameters, with additional raw x, y, z offsets. */
    public void spawnMissile(float ox, float oy, float oz) {
        if (vehicle.level().isClientSide) {
            return;
        }
        IAmmo ammo = vehicle.ammoHelper.getCurrentAmmoType();
        if (ammo == null) {
            return;
        }
        if (vehicle.ammoHelper.getCurrentAmmoCount() > 0) {
            vehicle.ammoHelper.decreaseCurrentAmmo();

            Vec3 off = vehicle.getMissileOffset();
            float x = (float) (vehicle.getX() + off.x + ox);
            float y = (float) (vehicle.getY() + off.y + oy);
            float z = (float) (vehicle.getZ() + off.z + oz);

            int count = ammo.hasSecondaryAmmo() ? ammo.getSecondaryAmmoTypeCount() : 1;
            for (int i = 0; i < count; i++) {
                float maxPower = getAdjustedMaxMissileVelocity();
                float power = Math.min(vehicle.localLaunchPower, maxPower);
                float yaw = vehicle.localTurretRotation;
                float pitch = vehicle.localTurretPitch + vehicle.getXRot();
                if (VehicleSettings.adjustMissilesForAccuracy) {
                    float accuracy = getAccuracyAdjusted();
                    yaw += (float) rng.nextGaussian() * (1.f - accuracy) * 10.f;
                    if (vehicle.canAimPower() && !ammo.isRocket()) {
                        power += (float) rng.nextGaussian() * (1.f - accuracy) * 2.5f;
                        if (power < 1.f) {
                            power = 1.f;
                        }
                    } else if (vehicle.canAimPitch()) {
                        pitch += (float) rng.nextGaussian() * (1.f - accuracy) * 10.f;
                    } else if (ammo.isRocket()) {
                        power += power / vehicle.currentLaunchSpeedPowerMax;
                        pitch += (rng.nextFloat() * 2.f - 1.f) * (1.f - accuracy) * 50.f;
                    }
                }
                MissileBase missile = getMissile2(x, y, z, yaw, pitch, power);
                if (missile != null) {
                    VehicleMovementType movementType = vehicle.vehicleType.getMovementType();
                    if (movementType == VehicleMovementType.AIR1 || movementType == VehicleMovementType.AIR2) {
                        missile.setDeltaMovement(missile.getDeltaMovement().add(vehicle.getDeltaMovement()));
                    }
                    vehicle.level().addFreshEntity(missile);
                }
            }
        }
    }

    /**
     * Fires one missile of the vehicle's current ammo from a turret mount of a ship (rework phase 4): the ammo, its count, the launch speed and the scatter are the
     * vehicle's, as for the single turret of the other types; the place, the direction and the shooter are the mount's. Server side.
     *
     * @param yaw   the world yaw of the barrel in the vehicle yaw convention, {@code pitch} the elevation in degrees
     * @return false if there was nothing to fire
     */
    public boolean fireFromHardpoint(Vec3 origin, float yaw, float pitch, @Nullable LivingEntity shooter) {
        if (vehicle.level().isClientSide) {
            return false;
        }
        IAmmo ammo = vehicle.ammoHelper.getCurrentAmmoType();
        if (ammo == null || vehicle.ammoHelper.getCurrentAmmoCount() <= 0) {
            return false;
        }
        vehicle.ammoHelper.decreaseCurrentAmmo();
        int count = ammo.hasSecondaryAmmo() ? ammo.getSecondaryAmmoTypeCount() : 1;
        for (int i = 0; i < count; i++) {
            float power = getAdjustedMaxMissileVelocity();
            float shotYaw = yaw;
            float shotPitch = pitch;
            if (VehicleSettings.adjustMissilesForAccuracy) {
                float accuracy = getAccuracyAdjusted();
                shotYaw += (float) rng.nextGaussian() * (1.f - accuracy) * 10.f;
                shotPitch += (float) rng.nextGaussian() * (1.f - accuracy) * 10.f;
            }
            MissileBase missile = getMissile2((float) origin.x, (float) origin.y, (float) origin.z, shotYaw, shotPitch, power, shooter);
            if (missile != null) {
                vehicle.level().addFreshEntity(missile);
            }
        }
        return true;
    }

    @Nullable
    MissileBase getMissile2(float x, float y, float z, float yaw, float pitch, float velocity) {
        return getMissile2(x, y, z, yaw, pitch, velocity, vehicle.getFiringCrew());
    }

    @Nullable
    MissileBase getMissile2(float x, float y, float z, float yaw, float pitch, float velocity, @Nullable LivingEntity shooter) {
        IAmmo ammo = vehicle.ammoHelper.getCurrentAmmoType();
        if (ammo != null) {
            MissileBase missile = new MissileBase(AwVehicles.MISSILE.get(), vehicle.level());
            if (ammo.hasSecondaryAmmo()) {
                ammo = ammo.getSecondaryAmmoType();
            }
            missile.setMissileParams2(ammo, x, y, z, yaw, pitch, velocity);
            missile.setMissileCallback(vehicle);
            missile.setLaunchingEntity(vehicle);
            missile.setShooter(shooter);
            return missile;
        }
        return null;
    }

    public void onTick() {
        if (this.isReloading) {
            this.vehicle.onReloadUpdate();
            if (this.reloadingTicks <= 0) {
                this.setFinishedReloading();
                this.vehicle.firingVarsHelper.onReloadingFinished();
            }
            this.reloadingTicks--;
        }
        if (this.isFiring) {
            vehicle.onFiringUpdate();
        }
        if (this.isLaunching) {
            vehicle.onLaunchingUpdate();
        }
        if (vehicle.level().isClientSide) {
            if (!vehicle.canAimPitch()) {
                this.clientTurretPitch = vehicle.localTurretPitch;
            }
            if (!vehicle.canAimPower()) {
                this.clientLaunchSpeed = vehicle.localLaunchPower;
            }
            if (!vehicle.canAimRotate()) {
                this.clientTurretYaw = vehicle.getYRot();
            }
        }
        if (!vehicle.canAimPower()) {
            vehicle.localLaunchPower = vehicle.currentLaunchSpeedPowerMax;
        }
        if (vehicle.canAimRotate()) {
            float diff = vehicle.getYRot() - vehicle.yRotO;
            vehicle.localTurretRotation += diff;
            this.clientTurretYaw += diff;
        }
    }

    /** How many missiles can be fired at the current missile type and weight; at least 1. */
    public int getMissileLaunchCount() {
        IAmmo ammo = vehicle.ammoHelper.getCurrentAmmoType();
        int missileCount = 1;
        if (ammo != null) {
            missileCount = (int) (vehicle.vehicleType.getMaxMissileWeight() / ammo.getAmmoWeight());
            if (missileCount < 1) {
                missileCount = 1;
            }
        }
        return missileCount;
    }

    /** The max missile velocity, adjusted by the missile weight as a percentage of the vehicle's max missile weight. */
    public float getAdjustedMaxMissileVelocity() {
        float velocity = vehicle.currentLaunchSpeedPowerMax;
        IAmmo ammo = vehicle.ammoHelper.getCurrentAmmoType();
        if (ammo != null) {
            float missileWeight = ammo.getAmmoWeight();
            float maxWeight = vehicle.vehicleType.getMaxMissileWeight();
            if (missileWeight > maxWeight) {
                float totalWeight = missileWeight + maxWeight;
                float temp = maxWeight / totalWeight;
                temp *= 2;
                velocity *= temp;
            }
        }
        return velocity;
    }

    /** The accuracy after adjusting for the rider (soldier); the same as the vehicle's current accuracy. */
    private float getAccuracyAdjusted() {
        return vehicle.currentAccuracy;
    }

    /** If not already firing, initiates the launch sequence (phase 1 of 3). Triggered from a packet. */
    private void initiateLaunchSequence() {
        if (!this.isFiring && !this.isLaunching && this.reloadingTicks <= 0) {
            this.isFiring = true;
            this.isLaunching = false;
            this.isReloading = false;
            vehicle.vehicleType.playFiringSound(vehicle);
        }
    }

    /** Sets reloading to finished (back to phase 0). Private for a reason. */
    private void setFinishedReloading() {
        this.isFiring = false;
        this.isReloading = false;
        this.isLaunching = false;
        this.reloadingTicks = 0;
    }

    /** Initiates the actual launching of the missiles (phase 2 of 3). */
    public void startLaunching() {
        this.isFiring = false;
        this.isLaunching = true;
        this.isReloading = false;
    }

    /** Finishes the launching sequence and begins reloading (phase 3 of 3). */
    public void setFinishedLaunching() {
        this.isFiring = false;
        this.isReloading = true;
        this.isLaunching = false;
        this.reloadingTicks = vehicle.currentReloadTicks;
        vehicle.vehicleType.playReloadSound(vehicle);
    }

    /** Server: a player pressed fire. Client: the server says the vehicle fires. */
    public void handleFireUpdate() {
        if (reloadingTicks <= 0 || vehicle.level().isClientSide) {
            boolean shouldFire = vehicle.ammoHelper.getCurrentAmmoCount() > 0 || vehicle.ammoHelper.doesntUseAmmo();
            if (shouldFire) {
                if (!vehicle.level().isClientSide) {
                    PacketDistributor.sendToPlayersTrackingEntity(vehicle, new VehicleEntityPayloads.FireUpdate(vehicle.getId()));
                }
                this.initiateLaunchSequence();
            }
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public void updateAim(Optional<Float> pitch, Optional<Float> yaw, Optional<Float> power) {
        boolean sendReply = false;
        if (pitch.filter(v -> !Float.isFinite(v)).isPresent() || yaw.filter(v -> !Float.isFinite(v)).isPresent()
                || power.filter(v -> !Float.isFinite(v)).isPresent()) {
            return; // a client sent NaN or infinity
        }
        if (pitch.isPresent()) {
            sendReply = true;
            vehicle.localTurretDestPitch = pitch.get();
        }
        if (yaw.isPresent()) {
            sendReply = true;
            vehicle.localTurretDestRot = yaw.get();
        }
        if (power.isPresent()) {
            sendReply = true;
            vehicle.localLaunchPower = power.get();
        }
        if (!vehicle.level().isClientSide && sendReply) {
            PacketDistributor.sendToPlayersTrackingEntity(vehicle, new VehicleEntityPayloads.AimUpdate(vehicle.getId(), pitch, yaw, power));
        }
    }

    /** CLIENT: the fire key was pressed. */
    public void handleFireInput() {
        if (isReadyToFire()) {
            PacketDistributor.sendToServer(new VehicleEntityPayloads.FireUpdate(vehicle.getId()));
        }
    }

    public boolean isReadyToFire() {
        return !isFiring && !isLaunching && !isReloading && hasAmmo();
    }

    private boolean hasAmmo() {
        return vehicle.ammoHelper.doesntUseAmmo() || (vehicle.ammoHelper.getCurrentAmmoCount() > 0 && vehicle.ammoHelper.getCurrentAmmoType() != null);
    }

    /** CLIENT: aim keys; changes the client side values and sends them to the server. */
    public void handleAimKeyInput(float pitch, float yaw) {
        boolean pitchUpdated = false;
        boolean powerUpdated = false;
        boolean yawUpdated = false;
        if (vehicle.canAimPitch()) {
            float pitchTest = this.clientTurretPitch + pitch;
            if (pitchTest < vehicle.currentTurretPitchMin) {
                pitchTest = vehicle.currentTurretPitchMin;
            } else if (pitchTest > vehicle.currentTurretPitchMax) {
                pitchTest = vehicle.currentTurretPitchMax;
            }
            if (!MathUtils.epsilonEquals(pitchTest, this.clientTurretPitch)) {
                pitchUpdated = true;
                this.clientTurretPitch = pitchTest;
            }
        } else if (vehicle.canAimPower()) {
            float powerTest = clientLaunchSpeed + pitch;
            if (powerTest < 0) {
                powerTest = 0;
            } else if (powerTest > getAdjustedMaxMissileVelocity()) {
                powerTest = getAdjustedMaxMissileVelocity();
            }
            if (!MathUtils.epsilonEquals(clientLaunchSpeed, powerTest)) {
                powerUpdated = true;
                this.clientLaunchSpeed = powerTest;
            }
        }
        if (vehicle.canAimRotate()) {
            yawUpdated = true;
            this.clientTurretYaw += yaw;
        }

        if (powerUpdated || pitchUpdated || yawUpdated) {
            Optional<Float> turretPitch = pitchUpdated ? Optional.of(clientTurretPitch) : Optional.empty();
            Optional<Float> turretYaw = yawUpdated ? Optional.of(clientTurretYaw) : Optional.empty();
            Optional<Float> power = powerUpdated ? Optional.of(clientLaunchSpeed) : Optional.empty();

            PacketDistributor.sendToServer(new VehicleEntityPayloads.AimUpdate(vehicle.getId(), turretPitch, turretYaw, power));
        }
    }

    /** CLIENT: updates the client desired pitch and yaw towards a target point and sends them to the server. */
    public void handleAimInput(Vec3 target) {
        boolean updated = false;
        boolean updatePitch = false;
        boolean updatePower = false;
        boolean updateYaw = false;
        Vec3 offset = vehicle.getMissileOffset();
        float x = (float) (vehicle.getX() + offset.x);
        float y = (float) (vehicle.getY() + offset.y);
        float z = (float) (vehicle.getZ() + offset.z);
        float tx = (float) (target.x - x);
        float ty = (float) (target.y - y);
        float tz = (float) (target.z - z);
        float range = Mth.sqrt(tx * tx + tz * tz);
        if (vehicle.canAimPitch()) {
            Tuple<Float, Float> angles = Trig.getLaunchAngleToHit(tx, ty, tz, vehicle.localLaunchPower);
            if (angles.getA().isNaN() || angles.getB().isNaN()) {
                // out of range, keep the pitch
            } else if (Trig.isAngleBetween(angles.getB(), vehicle.currentTurretPitchMin, vehicle.currentTurretPitchMax)) {
                if (!MathUtils.epsilonEquals(this.clientTurretPitch, angles.getB())) {
                    this.clientTurretPitch = angles.getB();
                    updated = true;
                    updatePitch = true;
                }
            } else if (Trig.isAngleBetween(angles.getA(), vehicle.currentTurretPitchMin, vehicle.currentTurretPitchMax)) {
                if (!MathUtils.epsilonEquals(clientTurretPitch, angles.getA())) {
                    this.clientTurretPitch = angles.getA();
                    updated = true;
                    updatePitch = true;
                }
            }
        } else if (vehicle.canAimPower()) {
            float power = Trig.iterativeSpeedFinder(tx, ty, tz, vehicle.localTurretPitch + vehicle.getXRot(), TRAJECTORY_ITERATIONS_CLIENT, isCurrentAmmoRocket());
            if (!MathUtils.epsilonEquals(clientLaunchSpeed, power) && power < getAdjustedMaxMissileVelocity()) {
                this.clientLaunchSpeed = power;
                updated = true;
                updatePower = true;
            }
        }
        if (vehicle.canAimRotate()) {
            float yaw = getAimYaw(target.x, target.z);
            if (!MathUtils.epsilonEquals(yaw, clientTurretYaw) && (vehicle.currentTurretRotationMax >= 180
                    || Trig.isAngleBetween(yaw, vehicle.localTurretRotationHome - vehicle.currentTurretRotationMax,
                    vehicle.localTurretRotationHome + vehicle.currentTurretRotationMax))) {
                if (Math.abs(yaw - clientTurretYaw) > 0.25f) {
                    this.clientTurretYaw = yaw;
                    updated = true;
                    updateYaw = true;
                }
            }
        }

        if (updated) {
            this.clientHitRange = range;
            Optional<Float> turretPitch = updatePitch ? Optional.of(clientTurretPitch) : Optional.empty();
            Optional<Float> turretYaw = updateYaw ? Optional.of(clientTurretYaw) : Optional.empty();
            Optional<Float> power = updatePower ? Optional.of(clientLaunchSpeed) : Optional.empty();
            PacketDistributor.sendToServer(new VehicleEntityPayloads.AimUpdate(vehicle.getId(), turretPitch, turretYaw, power));
        }
    }

    private boolean isCurrentAmmoRocket() {
        IAmmo ammo = vehicle.ammoHelper.getCurrentAmmoType();
        return ammo != null && ammo.isRocket();
    }

    public boolean isAtTarget() {
        return isAtTarget(0.35f);
    }

    private boolean isAtTarget(float range) {
        float yaw = Trig.wrapTo360(vehicle.localTurretRotation);
        float dest = Trig.wrapTo360(vehicle.localTurretDestRot);

        return MathUtils.epsilonEquals(vehicle.localTurretDestPitch, vehicle.localTurretPitch) && Math.abs(yaw - dest) < range;
    }

    public boolean isNearTarget() {
        return isAtTarget(5f);
    }

    /** SERVER: aims at a target position for an NPC rider. */
    public void handleSoldierTargetInput(double targetX, double targetY, double targetZ) {
        boolean updated = false;
        boolean updatePitch = false;
        boolean updatePower = false;
        boolean updateYaw = false;
        Vec3 offset = vehicle.getMissileOffset();
        float x = (float) (vehicle.getX() + offset.x);
        float y = (float) (vehicle.getY() + offset.y);
        float z = (float) (vehicle.getZ() + offset.z);
        float tx = (float) (targetX - x);
        float ty = (float) (targetY - y);
        float tz = (float) (targetZ - z);
        if (vehicle.canAimPitch()) {
            Tuple<Float, Float> angles = Trig.getLaunchAngleToHit(tx, ty, tz, vehicle.localLaunchPower);
            if (angles.getA().isNaN() || angles.getB().isNaN()) {
                // out of range, keep the pitch
            } else if (Trig.isAngleBetween(angles.getB(), vehicle.currentTurretPitchMin, vehicle.currentTurretPitchMax)) {
                if (!MathUtils.epsilonEquals(vehicle.localTurretDestPitch, angles.getB())) {
                    vehicle.localTurretDestPitch = angles.getB();
                    updated = true;
                    updatePitch = true;
                }
            } else if (Trig.isAngleBetween(angles.getA(), vehicle.currentTurretPitchMin, vehicle.currentTurretPitchMax)) {
                if (!MathUtils.epsilonEquals(vehicle.localTurretDestPitch, angles.getA())) {
                    vehicle.localTurretDestPitch = angles.getA();
                    updated = true;
                    updatePitch = true;
                }
            }
        } else if (vehicle.canAimPower()) {
            float power = Trig.iterativeSpeedFinder(tx, ty, tz, vehicle.localTurretPitch + vehicle.getXRot(), TRAJECTORY_ITERATIONS_CLIENT, isCurrentAmmoRocket());
            if (!MathUtils.epsilonEquals(vehicle.localLaunchPower, power) && power < getAdjustedMaxMissileVelocity()) {
                this.vehicle.localLaunchPower = power;
                updated = true;
                updatePower = true;
            }
        }
        if (vehicle.canAimRotate()) {
            float yaw = getAimYaw(targetX, targetZ);
            if (!MathUtils.epsilonEquals(yaw, vehicle.localTurretDestRot) && (vehicle.currentTurretRotationMax >= 180
                    || Trig.isAngleBetween(yaw, vehicle.localTurretRotationHome - vehicle.currentTurretRotationMax,
                    vehicle.localTurretRotationHome + vehicle.currentTurretRotationMax))) {
                this.vehicle.localTurretDestRot = yaw;
                updated = true;
                updateYaw = true;
            }
        }
        if (updated && !vehicle.level().isClientSide) {
            Optional<Float> turretPitch = updatePitch ? Optional.of(vehicle.localTurretDestPitch) : Optional.empty();
            Optional<Float> turretYaw = updateYaw ? Optional.of(vehicle.localTurretDestRot) : Optional.empty();
            Optional<Float> power = updatePower ? Optional.of(vehicle.localLaunchPower) : Optional.empty();
            PacketDistributor.sendToPlayersTrackingEntity(vehicle, new VehicleEntityPayloads.AimUpdate(vehicle.getId(), turretPitch, turretYaw, power));
        }
    }

    private float getAimYaw(double targetX, double targetZ) {
        Vec3 offset = vehicle.getMissileOffset();
        float vecX = (float) (vehicle.getX() + (vehicle.canTurretTurn() ? offset.x : 0) - targetX);
        float vecZ = (float) (vehicle.getZ() + (vehicle.canTurretTurn() ? offset.z : 0) - targetZ);
        //noinspection SuspiciousNameCombination
        return Trig.wrapTo360(Trig.toDegrees((float) Math.atan2(vecX, vecZ)));
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("rt", reloadingTicks);
        tag.putBoolean("f", this.isFiring);
        tag.putBoolean("r", this.isReloading);
        tag.putBoolean("l", this.isLaunching);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        this.reloadingTicks = tag.getInt("rt");
        this.isFiring = tag.getBoolean("f");
        this.isReloading = tag.getBoolean("r");
        this.isLaunching = tag.getBoolean("l");
    }

    public boolean isAimedAt(IAimTarget target) {
        return isPitchPointedAt(target) && isYawPointedAt(target) && isPowerSetToPointAt(target);
    }

    private boolean isYawPointedAt(IAimTarget target) {
        AABB box = target.getBoundingBox();
        float minYaw = getAimYaw(box.minX, box.minZ);
        float maxYaw = getAimYaw(box.maxX, box.maxZ);

        if (minYaw > maxYaw) {
            float temp = minYaw;
            minYaw = maxYaw;
            maxYaw = temp;
        }

        float vehicleRotation = vehicle.canAimRotate() ? vehicle.localTurretRotation : vehicle.getYRot();

        return Trig.isAngleBetween(vehicleRotation, minYaw, maxYaw);
    }

    private boolean isPitchPointedAt(IAimTarget target) {
        if (!vehicle.canAimPitch()) {
            return true;
        }
        AABB box = target.getBoundingBox();
        Vec3 offset = vehicle.getMissileOffset();
        float targetX = (float) box.minX - (float) (vehicle.getX() + offset.x);
        float targetY = (float) box.minY - (float) (vehicle.getY() + offset.y);
        float targetZ = (float) box.minZ - (float) (vehicle.getZ() + offset.z);

        Tuple<Float, Float> anglesMin = Trig.getLaunchAngleToHit(targetX, targetY, targetZ, vehicle.localLaunchPower);
        if (Math.abs(anglesMin.getB() - vehicle.localTurretPitch) < NEGLIGIBLE_ANGLE_DIFFERENCE
                || Math.abs(anglesMin.getA() - vehicle.localTurretPitch) < NEGLIGIBLE_ANGLE_DIFFERENCE) {
            return true;
        }

        targetX = (float) box.maxX - (float) (vehicle.getX() + offset.x);
        targetY = (float) box.maxY - (float) (vehicle.getY() + offset.y);
        targetZ = (float) box.maxZ - (float) (vehicle.getZ() + offset.z);

        Tuple<Float, Float> anglesMax = Trig.getLaunchAngleToHit(targetX, targetY, targetZ, vehicle.localLaunchPower);
        if (Math.abs(anglesMax.getB() - vehicle.localTurretPitch) < NEGLIGIBLE_ANGLE_DIFFERENCE
                || Math.abs(anglesMax.getA() - vehicle.localTurretPitch) < NEGLIGIBLE_ANGLE_DIFFERENCE) {
            return true;
        }

        return Trig.isAngleBetween(vehicle.localTurretPitch, anglesMin.getA(), anglesMax.getA())
                || Trig.isAngleBetween(vehicle.localTurretPitch, anglesMin.getB(), anglesMax.getB());
    }

    private boolean isPowerSetToPointAt(IAimTarget target) {
        if (!vehicle.canAimPower()) {
            return true;
        }
        AABB box = target.getBoundingBox();
        Vec3 offset = vehicle.getMissileOffset();
        float x = (float) (vehicle.getX() + offset.x);
        float y = (float) (vehicle.getY() + offset.y);
        float z = (float) (vehicle.getZ() + offset.z);

        float minDistY;
        float maxDistY;
        if (Math.abs(box.minY - y) < Math.abs(box.maxY - y)) {
            minDistY = (float) (box.minY - y);
            maxDistY = (float) (box.maxY - y);
        } else {
            minDistY = (float) (box.maxY - y);
            maxDistY = (float) (box.minY - y);
        }

        float powerMin = getPowerFor((float) box.minX - x, minDistY, (float) box.minZ - z);
        float powerMax = getPowerFor((float) box.maxX - x, maxDistY, (float) box.maxZ - z);

        if (powerMin > powerMax) {
            float temp = powerMin;
            powerMin = powerMax;
            powerMax = temp;
        }

        return vehicle.localLaunchPower >= powerMin && vehicle.localLaunchPower <= powerMax;
    }

    private float getPowerFor(float distX, float distY, float distZ) {
        return Trig.iterativeSpeedFinder(distX, distY, distZ, vehicle.localTurretPitch + vehicle.getXRot(), TRAJECTORY_ITERATIONS_CLIENT, isCurrentAmmoRocket());
    }

    public float getAimYaw(IAimTarget target) {
        return getAimYaw(target.getX(), target.getZ());
    }
}
