/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/helpers/VehicleMoveHelper.
 */
package io.github.capttracker.ancientwarfarevehicle.helpers;

import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfare.util.Trig;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.hitch.CartHitch;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleMovementType;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSettings;
import io.github.capttracker.ancientwarfarevehicle.input.VehicleInputData;
import io.github.capttracker.ancientwarfarevehicle.network.VehicleEntityPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.WaterlilyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The custom kinematics of the vehicles: the control input, the server side motion (ground and water) and the
 * client side interpolation towards the positions the server sends.
 *
 * <p>Rework phase 4: the input is analog ({@link #handleInputData(VehicleInputData)}: throttle and steer scale the target speed and turn rate), and
 * the vehicle the local player drives is predicted on the client: it runs the same kinematics ({@link #stepKinematics}) each tick, keeps its last
 * {@value #HISTORY_TICKS} positions and, when the server's position arrives, compares it with the closest of them (the server's position is
 * a little in the past). More than {@value #SNAP_DISTANCE} blocks apart and the client is moved to the server's position; less than that is eased out.
 *
 * <p>The input bytes {@code powerInput} and {@code rotationInput} are stored and saved but, as in the original,
 * never read by the movement code.
 */
public class VehicleMoveHelper implements INBTSerializable<CompoundTag> {
    private static final int VEHICLE_MOVE_UPDATE_FREQUENCY = 3;
    /** Gravity per tick, the original's 9.81 * 0.05 * 0.05. */
    private static final double GRAVITY_PER_TICK = 9.81f * 0.05f * 0.05f;

    byte forwardInput;
    byte turnInput;
    byte powerInput;
    byte rotationInput;
    /** How much of the top speed / turn rate the input asks for, 0..1 (1 for the digital input of the AI). */
    float forwardMag = 1.f;
    float turnMag = 1.f;
    /** The camera angles of the rider, from the last input (degrees). */
    float riderAimYaw;
    float riderAimPitch;

    /** CLIENT, predicted vehicle only: false while the rider steers with the mouse, so the view is not turned along with the vehicle. */
    public boolean riderViewFollows = true;
    private static final int HISTORY_TICKS = 12;
    private static final double SNAP_DISTANCE = 0.5D;
    /** The last predicted states (x, y, z, yaw), oldest first. */
    private final java.util.ArrayDeque<double[]> history = new java.util.ArrayDeque<>();

    double destX;
    double destY;
    double destZ;
    float destYaw;
    float destPitch;

    int moveTicks = 0;
    int rotationTicks = 0;
    int pitchTicks = 0;

    public float forwardMotion = 0.f;
    protected float turnMotion = 0.f;
    /** Only used by the water code, which damps it; nothing sets it. */
    protected float strafeMotion = 0.f;
    /** Air vehicles only (none exist). */
    public float throttle = 0.f;

    protected float groundDrag = 0.96f;
    protected float groundStop = 0.02f;
    protected float rotationDrag = 0.94f;
    protected float rotationStop = 0.2f;

    protected float rotationSpeed = 0.f;
    protected float pitchSpeed = 0.f;

    protected boolean wasOnGround = true;

    protected final VehicleBase vehicle;

    public VehicleMoveHelper(VehicleBase vehicle) {
        this.vehicle = vehicle;
    }

    /** CLIENT: how far the yaw turned this tick; the riding player's view turns with it. */
    public float getRotationSpeed() {
        return this.rotationSpeed;
    }

    public void setForwardInput(byte in) {
        this.forwardInput = in;
        this.forwardMag = 1.f;
    }

    public void setStrafeInput(byte in) {
        this.turnInput = in;
        this.turnMag = 1.f;
    }

    /** The throttle of the last input, -1..1: what a horse-drawn cart passes on to its horse. */
    public float getDriveThrottle() {
        return forwardInput * forwardMag;
    }

    /** The steer of the last input, -1..1 (+1 is right). */
    public float getDriveSteer() {
        return turnInput * turnMag;
    }

    /** The camera yaw of the rider from the last input, in degrees. */
    public float getRiderAimYaw() {
        return riderAimYaw;
    }

    /** The camera pitch of the rider from the last input, in degrees. */
    public float getRiderAimPitch() {
        return riderAimPitch;
    }

    /** CLIENT: true if the local player drives this vehicle, which is then moved by the client too (see the class comment). */
    public boolean isPredicted() {
        if (!vehicle.level().isClientSide) {
            return false;
        }
        VehicleMovementType type = vehicle.vehicleType.getMovementType();
        // a horse-drawn cart follows its horse, which the client cannot predict
        return (type == VehicleMovementType.GROUND || type == VehicleMovementType.WATER) && !vehicle.vehicleType.isHorseDrawn() && vehicle.ship == null
                && vehicle.getControllingPassenger() instanceof net.minecraft.world.entity.player.Player player && player.isLocalPlayer();
    }

    /** CLIENT: the server sent a new position, yaw and pitch to interpolate towards. */
    public void updateMoveData(double posX, double posY, double posZ, boolean air, float motion, float yaw, float pitch) {
        if (!air && isPredicted()) {
            reconcile(posX, posY, posZ, yaw);
            return;
        }
        this.pitchTicks = VEHICLE_MOVE_UPDATE_FREQUENCY + 1;
        this.rotationTicks = VEHICLE_MOVE_UPDATE_FREQUENCY + 1;
        this.moveTicks = VEHICLE_MOVE_UPDATE_FREQUENCY + 1;

        this.destPitch = pitch;
        this.destYaw = yaw;
        this.destX = posX;
        this.destY = posY;
        this.destZ = posZ;
        if (air) {
            this.throttle = motion;
        } else {
            this.forwardMotion = motion;
        }
    }

    /** SERVER (and the predicted CLIENT vehicle): the riding player's input. The analog values decide the direction and how much of the speed and turn rate is asked for. */
    public void handleInputData(VehicleInputData data) {
        this.forwardInput = Math.abs(data.throttle()) > 0.02F ? (byte) (data.throttle() > 0 ? 1 : -1) : 0;
        this.forwardMag = Math.min(1.f, Math.abs(data.throttle()));
        this.turnInput = Math.abs(data.steer()) > 0.02F ? (byte) (data.steer() > 0 ? 1 : -1) : 0;
        this.turnMag = Math.min(1.f, Math.abs(data.steer()));
        this.powerInput = data.power();
        this.rotationInput = data.rotation();
        this.riderAimYaw = data.aimYaw();
        this.riderAimPitch = data.aimPitch();
    }

    /** The digital input (kept for callers that have only the four bytes). */
    public void handleInputData(byte forwardInput, byte turnInput, byte powerInput, byte rotationInput) {
        this.forwardInput = forwardInput;
        this.turnInput = turnInput;
        this.powerInput = powerInput;
        this.rotationInput = rotationInput;
        this.forwardMag = 1.f;
        this.turnMag = 1.f;
    }

    public void onUpdate() {
        if (vehicle.level().isClientSide) {
            if (isPredicted()) {
                onUpdatePredicted();
            } else {
                if (!history.isEmpty()) {
                    history.clear();
                    clearInputFromDismount();
                }
                // a ship's riders keep their own view (the ship turns slowly, and with mouse steering the ship follows the view)
                riderViewFollows = vehicle.ship == null;
                onUpdateClient();
            }
        } else {
            if (this.vehicle.getControllingPassenger() == null) {
                this.clearInputFromDismount();
            }
            onUpdateServer();
            this.vehicle.nav.onMovementUpdate();
        }
    }

    /** CLIENT: moves the predicted vehicle the way the server does, with collisions (the rider's own vehicle only). */
    protected void onUpdatePredicted() {
        vehicle.noPhysics = false;
        moveTicks = 0;
        rotationTicks = 0;
        pitchTicks = 0;
        pitchSpeed = 0;
        float yawBefore = vehicle.getYRot();
        stepKinematics();
        rotationSpeed = Mth.wrapDegrees(vehicle.getYRot() - yawBefore);
        vehicle.wheelRotationPrev = vehicle.wheelRotation;
        vehicle.wheelRotation += forwardMotion * (vehicle.vehicleType.getMovementType() == VehicleMovementType.WATER ? 64 : 60);
        history.addLast(new double[] {vehicle.getX(), vehicle.getY(), vehicle.getZ(), vehicle.getYRot()});
        while (history.size() > HISTORY_TICKS) {
            history.removeFirst();
        }
    }

    /** CLIENT: the server's position of the predicted vehicle arrived. */
    private void reconcile(double posX, double posY, double posZ, float yaw) {
        double[] nearest = null;
        double best = Double.MAX_VALUE;
        for (double[] state : history) {
            double dx = posX - state[0];
            double dy = posY - state[1];
            double dz = posZ - state[2];
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance < best) {
                best = distance;
                nearest = state;
            }
        }
        if (nearest == null || best > SNAP_DISTANCE || Math.abs(Mth.wrapDegrees(yaw - (float) nearest[3])) > 15.f) {
            // drifted too far: the server wins
            vehicle.setPos(posX, posY, posZ);
            vehicle.xo = posX;
            vehicle.yo = posY;
            vehicle.zo = posZ;
            vehicle.setYRot(yaw);
            vehicle.yRotO = yaw;
            history.clear();
            return;
        }
        if (best > 0.05D) {
            // a small difference: ease a fifth of it out each time (the history moves with the vehicle)
            double cx = (posX - nearest[0]) * 0.2D;
            double cy = (posY - nearest[1]) * 0.2D;
            double cz = (posZ - nearest[2]) * 0.2D;
            vehicle.setPos(vehicle.getX() + cx, vehicle.getY() + cy, vehicle.getZ() + cz);
            for (double[] state : history) {
                state[0] += cx;
                state[1] += cy;
                state[2] += cz;
            }
        }
    }

    protected void onUpdateClient() {
        // The client does not collide: it only follows the server's position. Vanilla position syncing is
        // switched off in VehicleBase (lerpTo and lerpMotion do nothing), this is the only source of position.
        vehicle.noPhysics = true;
        double motionX = 0;
        double motionY = 0;
        double motionZ = 0;
        rotationSpeed = 0;
        pitchSpeed = 0;
        if (this.rotationTicks > 0) {
            rotationSpeed = (this.destYaw - vehicle.getYRot()) / rotationTicks;
            vehicle.setYRot(vehicle.getYRot() + this.rotationSpeed);
            this.rotationTicks--;
        }
        if (this.pitchTicks > 0) {
            pitchSpeed = (this.destPitch - vehicle.getXRot()) / pitchTicks;
            vehicle.setXRot(vehicle.getXRot() + this.pitchSpeed);
            this.pitchTicks--;
        }
        if (moveTicks > 0) {
            motionX = destX - vehicle.getX();
            motionY = destY - vehicle.getY();
            motionZ = destZ - vehicle.getZ();
            moveTicks--;
        }
        vehicle.setDeltaMovement(motionX, motionY, motionZ);
        vehicle.wheelRotationPrev = vehicle.wheelRotation;
        VehicleMovementType movementType = vehicle.vehicleType.getMovementType();
        if (movementType == VehicleMovementType.AIR1 || movementType == VehicleMovementType.AIR2) {
            vehicle.wheelRotation += throttle;
        } else if (movementType == VehicleMovementType.WATER || movementType == VehicleMovementType.WATER2) {
            vehicle.wheelRotation += forwardMotion * 64;
        } else {
            vehicle.wheelRotation += forwardMotion * 60;
        }
        this.vehicle.move(MoverType.SELF, vehicle.getDeltaMovement());
    }

    /** One tick of the kinematics, on the server and on the predicted client vehicle: the motion from the input, then the move with collisions. */
    private void stepKinematics() {
        if (vehicle.ship != null) {
            stepShip();
            return;
        }
        if (vehicle.isMovementLocked()) {
            stepLocked();
            return;
        }
        if (vehicle.vehicleType.isHorseDrawn() && vehicle.hitch.isHitched()) {
            stepHorseDrawn();
            return;
        }
        VehicleMovementType move = vehicle.vehicleType.getMovementType();
        switch (move) {
            case GROUND -> this.applyGroundMotion();
            case WATER -> this.applyWaterMotion();
            default -> {
            }
        }
        if (move == VehicleMovementType.AIR1 || move == VehicleMovementType.AIR2) {
            vehicle.fallDistance = 0.f;
            if (vehicle.getControllingPassenger() != null) {
                vehicle.getControllingPassenger().fallDistance = 0.f;
            }
        }
        Vec3 current = vehicle.getDeltaMovement();
        vehicle.setDeltaMovement(Trig.sinDegrees(vehicle.getYRot()) * -forwardMotion, current.y, Trig.cosDegrees(vehicle.getYRot()) * -forwardMotion);
        this.vehicle.move(MoverType.SELF, vehicle.getDeltaMovement());
        this.wasOnGround = vehicle.onGround();
        if (vehicle.horizontalCollision) {
            // a siege tower or a mantlet stops dead at a wall (it climbs nothing), the others only lose speed
            forwardMotion *= vehicle.vehicleType.isPushed() ? 0.f : 0.65f;
        }
    }

    /** SERVER (rework phase 12): a siege tower with its bridge down (or somebody on its roof) stands still; it only falls to the ground. */
    private void stepLocked() {
        if (!vehicle.level().isClientSide && vehicle.siege != null && (Math.abs(getDriveThrottle()) > 0.05F || Math.abs(getDriveSteer()) > 0.05F)) {
            vehicle.siege.notifyLocked();
        }
        forwardMotion = 0.f;
        turnMotion = 0.f;
        addVerticalMotion(-GRAVITY_PER_TICK);
        vehicle.setDeltaMovement(0.0D, vehicle.getDeltaMovement().y, 0.0D);
        vehicle.move(MoverType.SELF, vehicle.getDeltaMovement());
        this.wasOnGround = vehicle.onGround();
    }

    /** SERVER: one tick of a ship, which has its own movement (see {@code ShipHelper}): the input is the sail and oar setting and the helm. */
    private void stepShip() {
        boolean helm = vehicle.getControllingPassenger() instanceof net.minecraft.world.entity.player.Player;
        float[] pilot = helm ? null : vehicle.ship.pilotInput();
        if (pilot != null) {
            vehicle.ship.step(pilot[0], pilot[1], false); // the autopilot (a worker or a dock) steers by waypoints
        } else {
            vehicle.ship.step(forwardInput * forwardMag, turnInput * turnMag, helm);
        }
        this.wasOnGround = false;
    }

    /** How hard the cart is pulled to the hitch point of its horse, per tick (the spring), and the longest step it makes in a tick. */
    private static final double HITCH_SPRING = 0.5D;
    private static final double HITCH_MAX_STEP = 0.9D;

    /**
     * SERVER: one tick of a cart that is hitched. The cart's hitch point (the front of its drawbar) is pulled towards the rear of the lead horse
     * as if by a spring. The cart turns to point from its middle at the horse's hitch point, at a rate that grows with its speed (so it cannot spin
     * on the spot), and never farther than the type's hitch angle from the horse's heading: past that the horse balks. A cart whose horse is
     * not loaded stands. When the horse gets more than {@link CartHitch#MAX_STRETCH} blocks from the hitch the rope snaps.
     */
    private void stepHorseDrawn() {
        CartHitch hitch = vehicle.hitch;
        AbstractHorse horse = hitch.primary();
        double startX = vehicle.getX();
        double startZ = vehicle.getZ();
        double moveX = 0.0D;
        double moveZ = 0.0D;
        turnMotion = 0.f;
        if (horse != null && horse.level() == vehicle.level()) {
            double length = vehicle.vehicleType.getHitchOffset();
            Vec3 point = CartHitch.hitchPoint(horse);
            float yaw = vehicle.getYRot();
            double yawRad = Math.toRadians(yaw);
            double stretch = Math.hypot(point.x - (startX - Math.sin(yawRad) * length), point.z - (startZ - Math.cos(yawRad) * length));
            if (stretch > CartHitch.MAX_STRETCH) {
                hitch.snap(horse);
            } else {
                double dx = point.x - startX;
                double dz = point.z - startZ;
                if (Math.hypot(dx, dz) > 0.5D) {
                    // forward is (-sin, -cos) of the yaw
                    float target = (float) Math.toDegrees(Math.atan2(-dx, -dz));
                    float horseYaw = 180.0F - horse.yBodyRot;
                    float limit = vehicle.vehicleType.getMaxHitchAngle();
                    float fromHorse = Mth.wrapDegrees(target - horseYaw);
                    if (Math.abs(fromHorse) > limit) {
                        target = horseYaw + Mth.clamp(fromHorse, -limit, limit);
                        hitch.balk(horse);
                    }
                    float maxTurn = 2.0F + 45.0F * Math.min(0.4F, Math.abs(forwardMotion));
                    float turn = Mth.clamp(Mth.wrapDegrees(target - yaw), -maxTurn, maxTurn);
                    vehicle.setYRot(yaw + turn);
                    yawRad = Math.toRadians(yaw + turn);
                }
                // the cart's middle is one drawbar length behind the horse's hitch point, along the cart's new heading
                moveX = (point.x + Math.sin(yawRad) * length - startX) * HITCH_SPRING;
                moveZ = (point.z + Math.cos(yawRad) * length - startZ) * HITCH_SPRING;
                double step = Math.hypot(moveX, moveZ);
                // a siege tower is slow: the horses are held to a walk of the type's pace (CartHitch scales their speed), and the tower is never pulled faster than a little over its top speed
                double cap = vehicle.vehicleType.isPushed() ? Math.max(0.02D, vehicle.currentForwardSpeedMax * 1.3D) : HITCH_MAX_STEP;
                if (step > cap) {
                    moveX *= cap / step;
                    moveZ *= cap / step;
                    if (vehicle.vehicleType.isPushed() && stretch > 2.0D) {
                        hitch.balk(horse);
                    }
                }
            }
        }
        addVerticalMotion(-GRAVITY_PER_TICK);
        vehicle.setDeltaMovement(moveX, vehicle.getDeltaMovement().y, moveZ);
        vehicle.move(MoverType.SELF, vehicle.getDeltaMovement());
        this.wasOnGround = vehicle.onGround();
        double yawNow = Math.toRadians(vehicle.getYRot());
        forwardMotion = (float) ((vehicle.getX() - startX) * -Math.sin(yawNow) + (vehicle.getZ() - startZ) * -Math.cos(yawNow));
    }

    protected void onUpdateServer() {
        VehicleMovementType move = vehicle.vehicleType.getMovementType();
        stepKinematics();
        this.tearUpGrass();
        Vec3 motion = vehicle.getDeltaMovement();
        boolean sendUpdate = motion.x != 0 || motion.y != 0 || motion.z != 0
                || vehicle.getYRot() != vehicle.yRotO || vehicle.getXRot() != vehicle.xRotO;
        sendUpdate = sendUpdate || vehicle.getControllingPassenger() != null;
        sendUpdate = sendUpdate || this.vehicle.tickCount % 60 == 0;
        if (sendUpdate) {
            boolean air = move == VehicleMovementType.AIR1 || move == VehicleMovementType.AIR2;
            float motionValue = air ? throttle : forwardMotion;
            PacketDistributor.sendToPlayersTrackingEntity(vehicle, new VehicleEntityPayloads.VehicleMove(vehicle.getId(),
                    vehicle.getX(), vehicle.getY(), vehicle.getZ(), air, motionValue, vehicle.getYRot(), vehicle.getXRot()));
        }
    }

    protected void applyGroundMotion() {
        this.applyTurnInput(0.05f);
        this.applyForwardInput(0.0125f, true);
        addVerticalMotion(-GRAVITY_PER_TICK);
    }

    protected void applyWaterMotion() {
        this.applyTurnInput(0.05f);
        if (this.handleBoatBob(true) < 0) {
            this.forwardMotion *= 0.85f;
            this.strafeMotion *= 0.85f;
        }
        this.applyForwardInput(0.0125f, false);
    }

    private void addVerticalMotion(double amount) {
        Vec3 motion = vehicle.getDeltaMovement();
        vehicle.setDeltaMovement(motion.x, motion.y + amount, motion.z);
    }

    protected void applyForwardInput(float inputFactor, boolean slowReverse) {
        if (vehicle.currentForwardSpeedMax <= 0.f) {
            forwardMotion = 0.f;
            return;
        }
        float weightAdjust = 1.f;
        if (vehicle.currentWeight > vehicle.baseWeight) {
            weightAdjust = vehicle.baseWeight / vehicle.currentWeight;
        }
        if (vehicle.vehicleType.isPushed() && pushFactor() <= 0.f) {
            // nobody pushes: it rolls to a stop at once
            forwardMotion = Math.abs(forwardMotion) < 0.005f ? 0.f : forwardMotion * 0.6f;
            return;
        }
        if (forwardInput != 0) {
            boolean reverse = (forwardInput == -1 && forwardMotion > 0) || (forwardInput == 1 && forwardMotion < 0);
            float maxSpeed = vehicle.currentForwardSpeedMax * weightAdjust * forwardMag * rowerFactor() * pushFactor();
            float maxReverse = -maxSpeed * (slowReverse ? 0.6f : 1.f);
            float percent = 1 - (forwardMotion >= 0 ? (forwardMotion / maxSpeed) : (forwardMotion / maxReverse));
            percent = percent > 0.25f ? 0.25f : Math.max(percent, -0.5f);
            percent = reverse ? 0.25f : percent;
            float changeFactor = percent * forwardInput * inputFactor;
            if (reverse) {
                changeFactor *= 2;
            }
            forwardMotion += changeFactor;
            if (forwardMotion > maxSpeed) {
                forwardMotion = maxSpeed;
            }
            if (forwardMotion < maxReverse) {
                forwardMotion = maxReverse;
            }
        } else {
            // a heavy pushed vehicle stops quickly when the pushers let go
            forwardMotion *= vehicle.vehicleType.isPushed() ? 0.8f : groundDrag;
        }
        if (Math.abs(forwardMotion) < (vehicle.vehicleType.isPushed() ? 0.004f : groundStop) && forwardInput == 0) {
            forwardMotion = 0.f;
        }
    }

    /**
     * A horse-drawn cart that gets here has no horse hitched: only its driver's slow push moves it. A pushed vehicle (siege tower, mantlet) gets the share of its speed
     * that its pushers give, none at all with nobody pushing.
     */
    private float pushFactor() {
        if (vehicle.vehicleType.isPushed()) {
            return vehicle.getPushFactor();
        }
        return vehicle.vehicleType.isHorseDrawn() ? CartHitch.PUSH_FACTOR : 1.f;
    }

    /** The speed factor of the rowers aboard (a water vehicle only): +15% of the top speed for each. */
    private float rowerFactor() {
        if (vehicle.vehicleType.getMovementType() != VehicleMovementType.WATER) {
            return 1.f;
        }
        return 1.f + 0.15f * vehicle.getRowerCount();
    }

    protected void applyTurnInput(float inputFactor) {
        float weightAdjust = 1.f;
        if (vehicle.currentWeight > vehicle.baseWeight) {
            weightAdjust = vehicle.baseWeight / vehicle.currentWeight;
        }
        // a pushed vehicle turns at the rate of its pushers but at least at 40% of it (the turn is a swing of the push bars), and not at all with nobody pushing
        float turnScale = vehicle.vehicleType.isPushed() ? (pushFactor() > 0.f ? Math.max(pushFactor(), 0.4f) : 0.f) : Math.max(pushFactor(), 0.4f);
        if (turnScale <= 0.f) {
            // nobody pushes a siege tower or a mantlet: it does not turn either
            turnMotion = 0.f;
            return;
        }
        if (turnInput != 0) {
            boolean reverse = (turnInput == -1 && turnMotion > 0) || (turnInput == 1 && turnMotion < 0);
            float maxSpeed = vehicle.currentStrafeSpeedMax * weightAdjust * turnMag * turnScale;
            float percent = 1 - (Math.abs(turnMotion) / maxSpeed);
            percent = reverse ? 1.f : Math.max(percent, -1.f);
            float changeFactor = percent * (turnInput * 2) * inputFactor;
            if (reverse) {
                changeFactor *= 2;
            }
            turnMotion += changeFactor;
            if (turnMotion > maxSpeed) {
                turnMotion = maxSpeed;
            }
            if (turnMotion < -maxSpeed) {
                turnMotion = -maxSpeed;
            }
        } else {
            turnMotion *= rotationDrag;
        }
        if (Math.abs(turnMotion) < rotationStop && turnInput == 0) {
            turnMotion = 0.f;
        }
        this.vehicle.setYRot(this.vehicle.getYRot() - this.turnMotion);
    }

    /**
     * Sets the vertical motion of a surface or subsurface water vehicle: the number of consecutive submerged
     * fifths of the bounding box from the bottom, minus 2, decides between sinking and floating up.
     *
     * @param floats if the vehicle should return to the surface when it is underwater
     * @return the adjusted number of submerged slices
     */
    protected int handleBoatBob(boolean floats) {
        float bitHeight = vehicle.getBbHeight() * 0.2f;
        AABB box = vehicle.getBoundingBox();
        int submergedBits = 0;
        for (int i = 0; i < 5; i++) {
            AABB slice = new AABB(box.minX, box.minY + (i * bitHeight), box.minZ, box.maxX, box.minY + ((1 + i) * bitHeight), box.maxZ);
            if (isWaterInBox(slice)) {
                submergedBits++;
            } else {
                break;
            }
        }
        submergedBits -= 2;
        if (!floats && submergedBits > 0) {
            submergedBits = 0;
        }
        if (submergedBits < 0) {
            addVerticalMotion(-GRAVITY_PER_TICK);
        } else {
            Vec3 motion = vehicle.getDeltaMovement();
            vehicle.setDeltaMovement(motion.x, (motion.y + (float) submergedBits * 0.02f) * 0.8f, motion.z);
        }
        return submergedBits;
    }

    /**
     * Replaces {@code World.isMaterialInBB(bb, Material.WATER)}: true if any water in the box has its surface
     * at or above the bottom of the box (a block of water of fill {@code n/8} has its surface at {@code y + n/8}).
     */
    private boolean isWaterInBox(AABB bb) {
        int minX = Mth.floor(bb.minX);
        int maxX = Mth.ceil(bb.maxX);
        int minY = Mth.floor(bb.minY);
        int maxY = Mth.ceil(bb.maxY);
        int minZ = Mth.floor(bb.minZ);
        int maxZ = Mth.ceil(bb.maxZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    FluidState fluid = vehicle.level().getFluidState(pos.set(x, y, z));
                    if (fluid.is(FluidTags.WATER) && y + fluid.getAmount() / 8.0 >= bb.minY) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    protected void tearUpGrass() {
        if (vehicle.level().isClientSide || !vehicle.onGround() || !VehicleSettings.vehiclesTearUpGrass) {
            return;
        }
        for (int corner = 0; corner < 4; ++corner) {
            int x = Mth.floor(vehicle.getX() + ((double) (corner % 2) - 0.5D) * 0.8D);
            int y = Mth.floor(vehicle.getY());
            int z = Mth.floor(vehicle.getZ() + ((double) (corner / 2) - 0.5D) * 0.8D);
            // check the top/upper blocks (riding through)
            BlockPos breakPos = new BlockPos(x, y, z);
            if (isTrampable(vehicle.level().getBlockState(breakPos))) {
                BlockTools.breakBlockAndDrop(vehicle.level(), breakPos);
            }
            // check the lower blocks (riding on)
            if (vehicle.level().getBlockState(breakPos.below()).is(Blocks.GRASS_BLOCK)) {
                vehicle.level().setBlock(breakPos.below(), Blocks.DIRT.defaultBlockState(), 3);
            }
        }
    }

    /**
     * Was: any {@code IPlantable} block plus snow, dead bush, tall grass, flowers and mushrooms. {@code IPlantable}
     * is gone from NeoForge; {@link BushBlock} covers the same plants (lily pads are left alone).
     */
    private static boolean isTrampable(BlockState state) {
        return (state.getBlock() instanceof BushBlock && !(state.getBlock() instanceof WaterlilyBlock)) || state.is(Blocks.SNOW);
    }

    /** Steers towards a point: turns until within 5 degrees, drives forward within 10 degrees and farther than 0.25 blocks. */
    public void setMoveTo(double x, double y, double z) {
        float yawDiff = Trig.getYawTowardsTarget(vehicle.getX(), vehicle.getZ(), x, z, vehicle.getYRot());
        byte fMot = 0;
        byte sMot = 0;
        if (Math.abs(yawDiff) > 5) {
            // more than 5 degrees off, correct the yaw first, then move forwards
            sMot = (byte) (yawDiff < 0 ? 1 : -1);
        }
        if (Math.abs(yawDiff) < 10 && Trig.getVelocity(x - vehicle.getX(), y - vehicle.getY(), z - vehicle.getZ()) >= 0.25f) {
            fMot = 1;
        }
        this.forwardInput = fMot;
        this.turnInput = sMot;
        this.forwardMag = 1.f;
        this.turnMag = 1.f;
    }

    public void stopMotion() {
        this.clearInputFromDismount();
        vehicle.setDeltaMovement(Vec3.ZERO);
        turnMotion = 0;
    }

    public void clearInputFromDismount() {
        this.forwardInput = 0;
        this.turnInput = 0;
        this.forwardMag = 1.f;
        this.turnMag = 1.f;
        this.powerInput = 0;
        this.rotationInput = 0;
        this.throttle = 0;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putByte("fi", forwardInput);
        tag.putByte("si", turnInput);
        tag.putByte("pi", powerInput);
        tag.putByte("ri", rotationInput);
        tag.putFloat("tr", throttle);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        this.forwardInput = tag.getByte("fi");
        this.turnInput = tag.getByte("si");
        this.powerInput = tag.getByte("pi");
        this.rotationInput = tag.getByte("ri");
        this.throttle = tag.getFloat("tr");
    }
}
