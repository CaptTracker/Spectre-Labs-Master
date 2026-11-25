/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/missiles/MissileBase.
 */
package io.github.capttracker.ancientwarfarevehicle.missiles;

import io.github.capttracker.ancientwarfare.util.Trig;
import io.github.capttracker.ancientwarfarevehicle.entity.IMissileHitCallback;
import io.github.capttracker.ancientwarfarevehicle.registry.AmmoRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The projectile of all 51 ammo types; the {@link IAmmo} selects the behaviour (flight type, impact effects, model).
 *
 * <p>Flight (same numbers as 1.12.2): the launch velocity is in blocks per second, so the per tick velocity is
 * {@code velocity * 0.05} along yaw and pitch. Gravity is {@code 9.81 * 0.05 * 0.05} per tick. Rockets accelerate for
 * {@code speed * 20 * 3} ticks instead. Every tick, on both sides, the block ray from the position to the next position is
 * traced with {@link net.minecraft.world.level.BlockGetter#clip}; on the server entities in the swept box are tested with
 * {@link ProjectileUtil#getEntityHitResult} (entity hit wins over a block hit). A non persistent missile is discarded on impact,
 * a persistent one (arrows, bolts, most rockets) stays in the block it hit until that block changes.
 *
 * <p>The simulation is deterministic, so clients run it too; the client does not apply impact effects (they are server only) and
 * ignores the rotation of position corrections ({@link #lerpTo}). The server tells the client when the missile is stuck or released
 * with the synched {@code inGround} flag.
 *
 * <p>Saved data: {@code ammoRegistryName, inGround, persistentBlockPos, persistentBlock, ticks, mX, mY, mZ} as before, plus
 * {@code burn} (the remaining rocket burn time, which 1.12.2 forgot). The shooter, launcher and callback are not saved.
 */
public class MissileBase extends Entity implements IEntityWithComplexSpawn {

    /** 5 minute timer max for missiles (6000 ticks). */
    public static final int MAX_LIFETIME_TICKS = 6000;
    /** The proximity check of proximity ammo starts after this many ticks. */
    private static final int PROXIMITY_START_TICKS = 20;

    /** Server authoritative: is the missile stuck in the ground? The client follows it. */
    private static final EntityDataAccessor<Boolean> DATA_IN_GROUND = SynchedEntityData.defineId(MissileBase.class, EntityDataSerializers.BOOLEAN);

    /**
     * Must be set after the missile is constructed, but before it is spawned server side ({@link #setMissileParams2}). Client side
     * it is set by {@link #readSpawnData}. The ammo type is responsible for many tick qualities, the effects of impact, and the
     * model/render instance used.
     */
    public IAmmo ammoType;
    /** The vehicle that launched this missile (server side; the client gets it from the spawn data). */
    @Nullable
    public Entity launcher = null;
    /** The entity credited with the damage (the vehicle's controlling passenger). */
    @Nullable
    public Entity shooterLiving;
    @Nullable
    private IMissileHitCallback shooter = null;
    private int rocketBurnTime = 0;

    private BlockPos persistentBlockPos = BlockPos.ZERO;
    private BlockState persistentBlock = Blocks.AIR.defaultBlockState();

    /** Initial velocities, used by the rocket for its acceleration factor. */
    private float mX;
    private float mY;
    private float mZ;

    public MissileBase(EntityType<MissileBase> type, Level level) {
        super(type, level);
        AmmoRegistry.registerAmmo();
        this.ammoType = AmmoRegistry.ammoArrow;
    }

    // ---- setup ----

    private void setMissileParams(IAmmo type, float x, float y, float z, float mx, float my, float mz) {
        this.ammoType = type;
        this.setPos(x, y, z);
        this.setOldPosAndRot();
        this.setDeltaMovement(mx, my, mz);
        this.mX = mx;
        this.mY = my;
        this.mZ = mz;
        if (this.ammoType.updateAsArrow()) {
            this.onUpdateArrowRotation();
        }
        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
        if (this.ammoType.isRocket()) {
            // use the launch power to determine the rocket burn time
            float temp = Mth.sqrt(mx * mx + my * my + mz * mz);
            if (temp > 0.f) {
                this.rocketBurnTime = (int) (temp * 20.f * AmmoHwachaRocket.BURN_TIME_FACTOR);
                this.mX = (mx / temp) * AmmoHwachaRocket.ACCELERATION_FACTOR;
                this.mY = (my / temp) * AmmoHwachaRocket.ACCELERATION_FACTOR;
                this.mZ = (mz / temp) * AmmoHwachaRocket.ACCELERATION_FACTOR;
                this.setDeltaMovement(this.mX, this.mY, this.mZ);
            }
        }
    }

    /**
     * Sets the ammo, the position and the launch velocity (blocks per second) from yaw and pitch in degrees.
     * Call before the missile is added to the level.
     */
    public void setMissileParams2(IAmmo ammo, float x, float y, float z, float yaw, float angle, float velocity) {
        float vX = -Trig.sinDegrees(yaw) * Trig.cosDegrees(angle) * velocity * 0.05f;
        float vY = Trig.sinDegrees(angle) * velocity * 0.05f;
        float vZ = -Trig.cosDegrees(yaw) * Trig.cosDegrees(angle) * velocity * 0.05f;
        this.setMissileParams(ammo, x, y, z, vX, vY, vZ);
    }

    public void setShooter(@Nullable Entity shooter) {
        this.shooterLiving = shooter;
    }

    public void setLaunchingEntity(@Nullable Entity entity) {
        this.launcher = entity;
    }

    public void setMissileCallback(@Nullable IMissileHitCallback callback) {
        this.shooter = callback;
    }

    public IAmmo getAmmoType() {
        return ammoType;
    }

    public int getRocketBurnTime() {
        return rocketBurnTime;
    }

    public boolean isInGround() {
        return this.entityData.get(DATA_IN_GROUND);
    }

    private void setInGround(boolean inGround) {
        this.entityData.set(DATA_IN_GROUND, inGround);
    }

    /** The model texture of the ammo, for the renderer. */
    public ResourceLocation getTexture() {
        return ammoType.getModelTexture();
    }

    // ---- impacts ----

    /** An entity was hit at the missile's position {@code x, y, z}. Server side only. */
    public void onImpactEntity(Entity entity, float x, float y, float z) {
        if (level().isClientSide) {
            return;
        }
        if (Ammo.shouldEffectEntity(level(), entity, this)) {
            this.ammoType.onImpactEntity(level(), entity, x, y, z, this);
            if (this.shooter != null) {
                this.shooter.onMissileImpactEntity(level(), entity);
            }
        }
    }

    /** A block was hit. Server side only: every effect of the ammo is a server effect (1.12.2 ran this on both sides and napalm wrote lava into the client world). */
    public void onImpactWorld(BlockHitResult hit) {
        if (level().isClientSide) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        this.ammoType.onImpactWorld(level(), pos.getX(), pos.getY(), pos.getZ(), this, hit);
        if (this.shooter != null) {
            this.shooter.onMissileImpact(level(), pos.getX(), pos.getY(), pos.getZ());
        }
    }

    // ---- entity behaviour ----

    @Override
    public boolean displayFireAnimation() {
        return this.ammoType.isFlaming();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
        // NOOP: missiles are not pushed by entities
    }

    /** Server position corrections only move the missile (1.12.2 {@code setPositionAndRotationDirect}); the rotation is simulated. */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.setPos(x, y, z);
    }

    @Override
    public void tick() {
        super.tick();
        this.onMovementTick();
        if (!this.level().isClientSide && this.tickCount > MAX_LIFETIME_TICKS) {
            this.discard();
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_IN_GROUND.equals(key) && this.level().isClientSide && this.isInGround()) {
            this.setDeltaMovement(Vec3.ZERO);
        }
    }

    private boolean canHitEntity(Entity entity) {
        if (!entity.canBeHitByProjectile() || entity.isSpectator()) {
            return false;
        }
        if (this.launcher != null) {
            if (entity instanceof io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipPart part && part.getParent() == this.launcher) {
                return false; // not the hull of the launching ship
            }
            // not the launching vehicle, its rider or the shooter (as in 1.12.2 only while the launcher is known)
            return entity != this.launcher && entity != this.launcher.getControllingPassenger() && entity != this.shooterLiving
                    && !(this.shooter instanceof Entity callbackEntity && entity == callbackEntity);
        }
        return true;
    }

    private void checkProximity() {
        if (this.getDeltaMovement().y > 0) {
            return; // don't bother checking when travelling upwards, wait until the downward swing
        }
        Level level = this.level();
        // check ground
        int x = Mth.floor(this.getX());
        int y = Mth.floor(this.getY());
        int z = Mth.floor(this.getZ());
        boolean impacted = false;
        if (ammoType.groundProximity() > 0) {
            int groundDiff = 0;
            while (groundDiff <= ammoType.groundProximity()) {
                groundDiff++;
                BlockPos below = new BlockPos(x, y - groundDiff, z);
                if (level.isLoaded(below) && !level.isEmptyBlock(below)) {
                    BlockPos here = new BlockPos(x, y, z);
                    this.onImpactWorld(new BlockHitResult(new Vec3(x, y, z), Direction.DOWN, here, false));
                    impacted = true;
                    break;
                }
            }
        }
        // check entities if not detonated by ground
        if (!impacted && ammoType.entityProximity() > 0) {
            float entProx = ammoType.entityProximity();
            List<Entity> entities = level.getEntities(this, new AABB(getX() - entProx, getY() - entProx, getZ() - entProx,
                    getX() + entProx, getY() + entProx, getZ() + entProx));
            for (Entity entity : entities) {
                if (entity != null && entity.getClass() != MissileBase.class
                        && !(entity instanceof io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipPart part && part.getParent() == this.launcher)) { // don't collide with missiles
                    if (this.distanceTo(entity) < entProx) {
                        this.onImpactEntity(entity, (float) getX(), (float) getY(), (float) getZ());
                        break;
                    }
                }
            }
        }
    }

    private void onMovementTick() {
        Level level = this.level();
        boolean serverSide = !level.isClientSide;
        if (serverSide && this.isInGround() && this.persistentBlock != level.getBlockState(this.persistentBlockPos)) {
            // the block it was stuck in has changed: release
            this.setDeltaMovement(Vec3.ZERO);
            this.setInGround(false);
        }
        if (this.isInGround()) {
            return;
        }

        Vec3 position = this.position();
        Vec3 motion = this.getDeltaMovement();
        Vec3 moveVector = position.add(motion);

        // block hit: liquids are ignored, blocks without a collision box are ignored
        BlockHitResult blockHit = level.clip(new ClipContext(position, moveVector, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));

        // entities are tested on the server only; the swept box covers the whole move (1.12.2 only the end of it)
        EntityHitResult entityHit = null;
        if (serverSide) {
            AABB sweep = this.getBoundingBox().expandTowards(motion).inflate(1.0D);
            entityHit = ProjectileUtil.getEntityHitResult(level, this, position, moveVector, sweep, this::canHitEntity, 0.3F);
        }

        if (entityHit != null) {
            this.onImpactEntity(entityHit.getEntity(), (float) position.x, (float) position.y, (float) position.z);
            if (!this.ammoType.isPenetrating()) {
                this.discard(); // server only: entities are tested on the server only
                return;
            }
            motion = motion.scale(0.65f);
        } else if (blockHit.getType() == HitResult.Type.BLOCK) {
            this.onImpactWorld(blockHit);
            if (!this.ammoType.isPenetrating()) {
                if (serverSide && this.isRemoved()) {
                    return; // the impact effect removed the missile (airburst)
                }
                // snap to the hit point (backed off a little) and stop
                Vec3 toHit = blockHit.getLocation().subtract(position);
                double length = toHit.length();
                if (length > 1.0E-6D) {
                    position = position.subtract(toHit.scale(0.05D / length));
                }
                motion = toHit;
                if (!this.ammoType.isPersistent()) {
                    if (serverSide) {
                        this.discard();
                        return;
                    }
                    // the client waits for the server to remove it
                    this.setInGround(true);
                } else {
                    this.persistentBlockPos = blockHit.getBlockPos();
                    this.persistentBlock = level.getBlockState(this.persistentBlockPos);
                    this.setInGround(true);
                }
            } else {
                motion = motion.scale(0.65f);
            }
        }

        if (serverSide && this.ammoType.isProximityAmmo() && this.tickCount > PROXIMITY_START_TICKS) {
            this.setDeltaMovement(motion);
            this.checkProximity();
            if (this.isRemoved()) {
                return;
            }
        }

        position = position.add(motion);

        if (this.ammoType.isRocket() && this.rocketBurnTime > 0) {
            // if it is a rocket, accelerate while it is still burning
            this.rocketBurnTime--;
            motion = motion.add(this.mX, this.mY, this.mZ);
            if (level.isClientSide) {
                level.addParticle(ParticleTypes.SMOKE, position.x, position.y, position.z, 0.0D, 0.0D, 0.0D);
            }
        } else {
            motion = motion.add(0.0D, -(double) this.ammoType.getGravityFactor(), 0.0D);
        }
        this.setDeltaMovement(motion);
        this.setPos(position.x, position.y, position.z);
        if (this.ammoType.updateAsArrow()) {
            this.onUpdateArrowRotation();
        }
    }

    /** Points the missile along its flight (the angle convention of the 1.12.2 arrow model). */
    private void onUpdateArrowRotation() {
        Vec3 motion = this.getDeltaMovement();
        double motionSpeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        this.setYRot(Trig.toDegrees((float) Math.atan2(motion.x, motion.z)) - 90);
        this.setXRot(Trig.toDegrees((float) Math.atan2(motion.y, motionSpeed)) - 90);
        while (this.getXRot() - this.xRotO < -180.0F) {
            this.xRotO -= 360.0F;
        }
        while (this.getXRot() - this.xRotO >= 180.0F) {
            this.xRotO += 360.0F;
        }
        while (this.getYRot() - this.yRotO < -180.0F) {
            this.yRotO -= 360.0F;
        }
        while (this.getYRot() - this.yRotO >= 180.0F) {
            this.yRotO += 360.0F;
        }
    }

    // ---- synched data, spawn data and saved data ----

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_IN_GROUND, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        IAmmo ammo = null;
        ResourceLocation ammoName = ResourceLocation.tryParse(tag.getString("ammoRegistryName"));
        if (ammoName != null) {
            ammo = AmmoRegistry.getAmmo(ammoName);
        }
        this.ammoType = ammo != null ? ammo : AmmoRegistry.ammoArrow; // an unknown ammo name falls back to the arrow
        this.setInGround(tag.getBoolean("inGround"));
        this.persistentBlockPos = BlockPos.of(tag.getLong("persistentBlockPos"));
        this.persistentBlock = NbtUtils.readBlockState(level().holderLookup(Registries.BLOCK), tag.getCompound("persistentBlock"));
        this.tickCount = tag.getInt("ticks");
        this.mX = tag.getFloat("mX");
        this.mY = tag.getFloat("mY");
        this.mZ = tag.getFloat("mZ");
        this.rocketBurnTime = tag.getInt("burn");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("ammoRegistryName", ammoType.getRegistryName().toString());
        tag.putBoolean("inGround", this.isInGround());
        tag.putLong("persistentBlockPos", persistentBlockPos.asLong());
        tag.put("persistentBlock", NbtUtils.writeBlockState(persistentBlock));
        tag.putInt("ticks", this.tickCount);
        tag.putFloat("mX", this.mX);
        tag.putFloat("mY", this.mY);
        tag.putFloat("mZ", this.mZ);
        tag.putInt("burn", this.rocketBurnTime);
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(ammoType.getRegistryName().toString());
        buffer.writeFloat(getYRot());
        buffer.writeFloat(getXRot());
        buffer.writeBoolean(isInGround());
        buffer.writeLong(persistentBlockPos.asLong());
        buffer.writeVarInt(Block.getId(persistentBlock));
        buffer.writeInt(rocketBurnTime);
        // new: the rocket acceleration, which the client simulation needs (1.12.2 left it at zero on the client)
        buffer.writeFloat(mX);
        buffer.writeFloat(mY);
        buffer.writeFloat(mZ);
        buffer.writeBoolean(this.launcher != null);
        if (this.launcher != null) {
            buffer.writeVarInt(this.launcher.getId());
        }
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        ResourceLocation ammoName = ResourceLocation.tryParse(buffer.readUtf(64));
        IAmmo ammo = ammoName == null ? null : AmmoRegistry.getAmmo(ammoName);
        this.ammoType = ammo != null ? ammo : AmmoRegistry.ammoArrow;
        this.setYRot(buffer.readFloat());
        this.setXRot(buffer.readFloat());
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        this.setInGround(buffer.readBoolean());
        this.persistentBlockPos = BlockPos.of(buffer.readLong());
        this.persistentBlock = Block.stateById(buffer.readVarInt());
        this.rocketBurnTime = buffer.readInt();
        this.mX = buffer.readFloat();
        this.mY = buffer.readFloat();
        this.mZ = buffer.readFloat();
        if (buffer.readBoolean()) {
            this.launcher = level().getEntity(buffer.readVarInt());
        }
    }
}
