/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/VehicleBase.
 */
package io.github.capttracker.ancientwarfarevehicle.entity;

import io.github.capttracker.ancientwarfare.owner.IOwnable;
import io.github.capttracker.ancientwarfare.owner.Owner;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfare.util.MathUtils;
import io.github.capttracker.ancientwarfare.util.Trig;
import io.github.capttracker.ancientwarfarevehicle.AncientWarfareVehicles;
import io.github.capttracker.ancientwarfarevehicle.armors.IVehicleArmor;
import io.github.capttracker.ancientwarfarevehicle.entity.hitch.CartHitch;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipHelper;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipSpec;
import io.github.capttracker.ancientwarfarevehicle.entity.materials.IVehicleMaterial;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleType;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeBatteringRam;
import io.github.capttracker.ancientwarfarevehicle.entity.varhelpers.DummyVehicleHelper;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleAmmoHelper;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleFiringHelper;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleFiringVarsHelper;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleMoveHelper;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleUpgradeHelper;
import io.github.capttracker.ancientwarfarevehicle.init.AwVehicles;
import io.github.capttracker.ancientwarfarevehicle.inventory.VehicleInventory;
import io.github.capttracker.ancientwarfarevehicle.missiles.IAmmo;
import io.github.capttracker.ancientwarfarevehicle.pathing.Navigator;
import io.github.capttracker.ancientwarfarevehicle.pathing.Node;
import io.github.capttracker.ancientwarfarevehicle.pathing.PathWorldAccessEntity;
import io.github.capttracker.ancientwarfarevehicle.registry.VehicleRegistry;
import io.github.capttracker.ancientwarfarevehicle.upgrades.IVehicleUpgradeType;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The one entity class of all 21 vehicle types: catapults, ballistas, cannons, trebuchets, the hwacha, the battering
 * ram, the chest cart and the boats. The {@link IVehicleType} in {@link #vehicleType} decides size, stats and behaviour.
 *
 * <p>Changes from 1.12.2:
 * <ul>
 *   <li>The size of the entity comes from {@link #getDimensions(Pose)} (the type's width and height, eye height 1.6);
 *       {@link #setVehicleType} calls {@code refreshDimensions()} instead of {@code setSize}.</li>
 *   <li>The custom kinematics are kept: the server moves the vehicle ({@link VehicleMoveHelper}) and sends
 *       {@code VehicleMove} payloads; the client ignores vanilla position syncing ({@link #lerpTo} and
 *       {@link #lerpMotion} do nothing) and follows those payloads. {@link #isControlledByLocalInstance} is false so
 *       the riding player's client does not send vanilla vehicle-move packets that would overwrite the server's position.</li>
 *   <li>Riders are placed by {@link #positionRider}. The {@code allowFlying} permission hack is gone (a rider is exempt
 *       from the fly checks), and so is the change of the seated rider's height and bounding box.</li>
 *   <li>Damage types are data driven: the armor resistance is chosen by damage type tags (see
 *       {@link VehicleUpgradeHelper#getScaledDamage}); the ram's damage is the {@code ancientwarfarevehicle:battering}
 *       damage type (data/ancientwarfarevehicle/damage_type).</li>
 *   <li>Removed dead code: the unused synced input parameters, {@code assignedRider} and the unused material misfire chance.</li>
 *   <li>{@code getEffectiveRange} had sine and cosine swapped against the missile's own launch maths; fixed.</li>
 *   <li>The {@code writeToNBT} override that moved passengers onto the vehicle is gone: 1.21 saves passengers inside the
 *       vehicle's own tag, so the chunk problem it worked around does not exist.</li>
 * </ul>
 */
public class VehicleBase extends Entity implements IEntityWithComplexSpawn, IMissileHitCallback, IPathableEntity, IOwnable {
    private static final EntityDataAccessor<Float> VEHICLE_HEALTH = SynchedEntityData.defineId(VehicleBase.class, EntityDataSerializers.FLOAT);
    /** Which rider sits in which seat: the entity ids per seat, -1 for a free one, as "12,-1,45" (rework phase 4). */
    private static final EntityDataAccessor<String> SEAT_DATA = SynchedEntityData.defineId(VehicleBase.class, EntityDataSerializers.STRING);

    /** The entity ids of the horses a horse-drawn cart is hitched to, "12,-1" (-1: not loaded), as {@link CartHitch} publishes them (rework phase 4). */
    private static final EntityDataAccessor<String> HITCH_DATA = SynchedEntityData.defineId(VehicleBase.class, EntityDataSerializers.STRING);

    /** The damage type of the battering ram (a datapack entry: {@code data/ancientwarfarevehicle/damage_type/battering.json}). */
    public static final ResourceKey<DamageType> BATTERING_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Ids.of(AncientWarfareVehicles.MOD_ID, "battering"));

    private static final float ENTITY_COLLISION_REDUCTION = 0.9f;
    private static final float EYE_HEIGHT = 1.6F;

    /*
     * The current max stats, set from setVehicleType(): local cached bases, after application of the material factors.
     * Should not be altered at all after the vehicle is first initialized.
     */
    public float baseForwardSpeed;
    public float baseStrafeSpeed;
    public float basePitchMin;
    public float basePitchMax;
    private float baseTurretRotationMax;
    private float baseLaunchSpeedMax;
    public float baseHealth = 100;
    private float baseAccuracy = 1.f;
    /** kg */
    public float baseWeight = 1000;
    private static final int BASE_RELOAD_TICKS = 100;
    private final float baseGenericResist = 0.f;
    private final float baseFireResist = 0.f;
    private final float baseExplosionResist = 0.f;
    private int hurtInvulTicks = 0;

    /* Local current stats, fully updated and modified from upgrades and the like. Should not be altered aside from upgrades and armor. */
    public float currentForwardSpeedMax = 0.42f;
    public float currentStrafeSpeedMax = 2.0f;

    /** How many ticks a reload cycle is at the current upgrade status. The current reload status is stored in the firing helper. */
    public int currentReloadTicks = 100;
    public float currentTurretPitchMin = 0.f;
    public float currentTurretPitchMax = 90.f;
    public float currentLaunchSpeedPowerMax = 32.321f;
    public float currentGenericResist = 0.f;
    public float currentFireResist = 0.f;
    public float currentExplosionResist = 0.f;
    public float currentWeight = 1000.f;
    public float currentTurretPitchSpeed = 0.f;
    public float currentTurretYawSpeed = 0.f;
    public float currentAccuracy = 1.f;
    public float currentTurretRotationMax = 45.f;

    /* Local variables ("local" means the entity's own state, not client side), may be altered by input and the like. */
    public float localTurretRotationHome = 0.f;
    public float localTurretRotation = 0.f;
    public float localTurretDestRot = 0.f;
    private final float localTurretRotInc = 1.f;
    public float localTurretPitch = 45.f;
    public float localTurretDestPitch = 45.f;
    private final float localTurretPitchInc = 1.f;
    public float localLaunchPower = 31.321f;

    /**
     * Set by the move helper on the movement update. Used during client rendering to update the wheel rotation and
     * other movement speed based animations.
     */
    public float wheelRotation = 0.f;
    public float wheelRotationPrev = 0.f;

    /** Used to determine whether it should allow interaction (the setup time after the vehicle is placed). */
    private boolean isSettingUp = false;
    private int setupTicks = 0;

    /** Set client side when incoming damage is taken. */
    public int hitAnimationTicks = 0;

    /** Complex stat tracking helpers: move, ammo, upgrades, firing. */
    public final VehicleAmmoHelper ammoHelper;
    public final VehicleUpgradeHelper upgradeHelper;
    public final VehicleMoveHelper moveHelper;
    public final VehicleFiringHelper firingHelper;
    public VehicleFiringVarsHelper firingVarsHelper;
    public final VehicleInventory inventory;
    public final Navigator nav;
    public final PathWorldAccessEntity worldAccess;
    /** Set to the catapult stand type until the real one is set, so it is never null. */
    public IVehicleType vehicleType = VehicleRegistry.CATAPULT_STAND_FIXED;
    /** The current material level of this vehicle. Should be read or set before calling updateBaseStats. */
    public int vehicleMaterialLevel = 0;
    private Owner owner = Owner.EMPTY;
    private boolean packed = false;
    /** The entity id of the rider of each seat of the type, -1 if the seat is free. The server sets it, the client reads it from {@link #SEAT_DATA}. */
    private int[] seatOccupants = new int[0];
    /** SERVER: seats of riders read from the saved data that have not re-attached yet (rider UUID to seat index); see {@link #restoreSeat}. */
    private final java.util.Map<java.util.UUID, Integer> pendingSeats = new java.util.HashMap<>();
    /** SERVER: ticks left in which the seats in {@link #pendingSeats} are kept free for their riders. */
    private int pendingSeatTicks = 0;
    private static final int PENDING_SEAT_TICKS = 100;
    /** The horses of a horse-drawn cart (empty for every other type). */
    public final CartHitch hitch = new CartHitch(this);
    /** The ship machinery (hull parts, deck, sails, turrets), non null for an entity of the ship entity type (rework phase 4). */
    @Nullable
    public final ShipHelper ship;
    /** CLIENT: the ids of the hitched horses, from {@link #HITCH_DATA}. */
    private int[] hitchedIds = new int[0];
    /** Damage that a player's blows have done to a cart, as with a boat: it breaks above 40 (a blow is its damage times 10) and heals 1 a tick. */
    private float breakDamage = 0.f;
    /** The role the entity that is boarding right now asked for (see {@link #boardAs}); null for the default order. */
    @Nullable
    private SeatRole boardingRole = null;
    /** True while {@link #boardStrict} boards: only seats of {@link #boardingRole} are acceptable, with no fall back to another role. */
    private boolean boardingStrict = false;

    public VehicleBase(EntityType<VehicleBase> entityType, Level level) {
        super(entityType, level);
        this.upgradeHelper = new VehicleUpgradeHelper(this);
        this.moveHelper = new VehicleMoveHelper(this);
        this.ammoHelper = new VehicleAmmoHelper(this);
        this.firingHelper = new VehicleFiringHelper(this);
        this.firingVarsHelper = new DummyVehicleHelper(this);
        this.inventory = new VehicleInventory(this);
        this.worldAccess = new PathWorldAccessEntity(level, this);
        this.nav = new Navigator(this);
        this.nav.setStuckCheckTicks(100);
        this.ship = entityType == AwVehicles.SHIP.get() ? new ShipHelper(this) : null;
        if (this.ship != null) {
            // the parts take the ids after the ship's, so no other entity may get them (what the ender dragon does, MC-158205)
            setId(ENTITY_COUNTER.getAndAdd(ShipSpec.MAX_PARTS + 1) + 1);
        }
    }

    /** A new vehicle of the default type; give it its type with {@link #setVehicleType}. */
    public static VehicleBase create(Level level) {
        return new VehicleBase(AwVehicles.VEHICLE.get(), level);
    }

    /** A new vehicle of the entity type that suits the vehicle type: the ship type for a ship, the ordinary one for the rest. */
    public static VehicleBase create(Level level, IVehicleType type) {
        return new VehicleBase(type.isShip() ? AwVehicles.SHIP.get() : AwVehicles.VEHICLE.get(), level);
    }

    /** True for a ship whose design is known. */
    public boolean isShip() {
        return ship != null && ship.hasSpec();
    }

    /** True if the entity sits in a gunner seat that works the turret mount of a ship. */
    public boolean isGunnerOf(Entity entity, int hardpoint) {
        return ship != null && ship.hardpointOf(entity) == hardpoint;
    }

    // ---- ship hull as a multipart entity (rework phase 4) ----

    @Override
    public boolean isMultipartEntity() {
        return ship != null;
    }

    @Override
    public net.neoforged.neoforge.entity.PartEntity<?>[] getParts() {
        return ship != null ? ship.getParts() : super.getParts();
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        if (ship != null) {
            ship.assignPartIds(id);
        }
    }

    @Override
    public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);
        if (ship != null) {
            ship.updateParts();
        }
    }

    /** The hull of a ship is far bigger than the box of the entity: it must not be culled when only its middle is out of sight. */
    @Override
    public AABB getBoundingBoxForCulling() {
        if (ship != null && ship.spec() != null) {
            double radius = Math.max(ship.spec().length(), ship.spec().width()) / 2.0D + 2.0D;
            return new AABB(getX() - radius, getY() - 1.0D, getZ() - radius, getX() + radius, getY() + ship.spec().deckTop() + 8.0D, getZ() + radius);
        }
        return super.getBoundingBoxForCulling();
    }

    @Override
    public boolean isPushedByFluid() {
        return ship == null;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(VEHICLE_HEALTH, 100f);
        builder.define(SEAT_DATA, "");
        builder.define(HITCH_DATA, "");
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SEAT_DATA.equals(key) && level().isClientSide) {
            seatOccupants = parseSeats(entityData.get(SEAT_DATA));
        }
        if (HITCH_DATA.equals(key) && level().isClientSide) {
            hitchedIds = parseSeats(entityData.get(HITCH_DATA));
        }
    }

    /** SERVER: {@link CartHitch} publishes the entity ids of its horses. */
    public void setHitchData(String ids) {
        entityData.set(HITCH_DATA, ids);
    }

    /** True if a horse is hitched to this cart (on the client, as far as the entity data has arrived). */
    public boolean isHitched() {
        return level().isClientSide ? hitchedIds.length > 0 : hitch.isHitched();
    }

    /** CLIENT: the entity ids of the hitched horses, -1 for one that is not loaded. */
    public int[] getHitchedIds() {
        return hitchedIds;
    }

    private static int[] parseSeats(String data) {
        if (data.isEmpty()) {
            return new int[0];
        }
        String[] parts = data.split(",");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                result[i] = -1;
            }
        }
        return result;
    }

    private void publishSeats() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < seatOccupants.length; i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(seatOccupants[i]);
        }
        entityData.set(SEAT_DATA, text.toString());
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        IVehicleType type = this.vehicleType;
        if (type == null) {
            // called before the field was set (nothing does that today)
            return super.getDimensions(pose);
        }
        return EntityDimensions.scalable(type.getWidth(), type.getHeight()).withEyeHeight(EYE_HEIGHT);
    }

    /** Vehicles climb one block (1.12.2: {@code stepHeight = 1.12f}). */
    @Override
    public float maxUpStep() {
        return 1.12f;
    }

    @Nullable
    @Override
    public ItemStack getPickResult() {
        ItemStack stack = this.vehicleType.getStackForLevel(vehicleMaterialLevel);
        return stack.isEmpty() ? null : stack;
    }

    @Override
    protected Component getTypeName() {
        return this.vehicleType.getDisplayComponent();
    }

    public void setHealth(float health) {
        if (health > this.baseHealth) {
            health = this.baseHealth;
        }
        this.entityData.set(VEHICLE_HEALTH, health);
    }

    public boolean canTurretTurn() {
        return !MathUtils.epsilonEquals(baseTurretRotationMax, 0);
    }

    public float getHealth() {
        return this.entityData.get(VEHICLE_HEALTH);
    }

    /** All the type dependent setup: size, helpers, valid ammo, upgrades and armor, inventory sizes, base and current stats. */
    public void setVehicleType(IVehicleType vehicle, int materialLevel) {
        this.vehicleType = vehicle;
        this.vehicleMaterialLevel = materialLevel;
        VehicleFiringVarsHelper help = vehicle.getFiringVarsHelper(this);
        if (help != null) {
            this.firingVarsHelper = help;
        }
        this.refreshDimensions();
        if (ship != null) {
            ship.setSpec(vehicle.getShipSpec());
        }
        this.seatOccupants = new int[vehicle.getSeats().size()];
        java.util.Arrays.fill(this.seatOccupants, -1);
        if (level().isClientSide) {
            this.seatOccupants = parseSeats(entityData.get(SEAT_DATA));
        } else {
            publishSeats();
        }
        for (IAmmo ammo : vehicleType.getValidAmmoTypes()) {
            this.ammoHelper.addUseableAmmo(ammo);
        }
        for (IVehicleUpgradeType up : this.vehicleType.getValidUpgrades()) {
            this.upgradeHelper.addValidUpgrade(up);
        }
        for (IVehicleArmor armor : this.vehicleType.getValidArmors()) {
            this.upgradeHelper.addValidArmor(armor);
        }
        this.inventory.setInventorySizes(vehicle.getUpgradeBaySize(), vehicle.getAmmoBaySize(), vehicle.getArmorBaySize(), vehicle.getStorageBaySize());
        this.updateBaseStats();
        this.resetCurrentStats();

        if (this.localTurretPitch < currentTurretPitchMin) {
            this.localTurretPitch = currentTurretPitchMin;
        } else if (this.localTurretPitch > currentTurretPitchMax) {
            this.localTurretPitch = currentTurretPitchMax;
        }
        this.localLaunchPower = this.firingHelper.getAdjustedMaxMissileVelocity();
        if (!this.canAimRotate()) {
            this.localTurretRotation = this.getYRot();
        }
        this.nav.setCanGoOnLand(vehicleType.getMovementType() == VehicleMovementType.GROUND);
    }

    public void setSetupState(boolean state, int ticks) {
        this.isSettingUp = state;
        this.setupTicks = state ? ticks : 0;
    }

    public void setInitialHealth() {
        this.setHealth(this.baseHealth);
    }

    private void updateBaseStats() {
        IVehicleMaterial material = vehicleType.getMaterialType();
        int level = this.vehicleMaterialLevel;
        baseForwardSpeed = vehicleType.getBaseForwardSpeed() * material.getSpeedForwardFactor(level);
        baseStrafeSpeed = vehicleType.getBaseStrafeSpeed() * material.getSpeedStrafeFactor(level);
        basePitchMin = vehicleType.getBasePitchMin();
        basePitchMax = vehicleType.getBasePitchMax();
        baseTurretRotationMax = vehicleType.getBaseTurretRotationAmount();
        baseLaunchSpeedMax = vehicleType.getBaseMissileVelocityMax();
        baseHealth = vehicleType.getBaseHealth() * material.getHPFactor(level);
        baseAccuracy = vehicleType.getBaseAccuracy() * material.getAccuracyFactor(level);
        baseWeight = vehicleType.getBaseWeight() * material.getWeightFactor(level);
        if (getHealth() > baseHealth) {
            this.setHealth(baseHealth);
        }
    }

    /** The spawner item stack tagged with this vehicle's level and current health. */
    private ItemStack getItemForVehicle() {
        return VehicleBridge.createStack(this.vehicleType, vehicleMaterialLevel, getHealth());
    }

    public boolean isAimable() {
        return !this.isSettingUp && vehicleType.isCombatEngine();
    }

    public boolean canAimRotate() {
        return !this.isSettingUp && vehicleType.canAdjustYaw();
    }

    public boolean canAimPitch() {
        return !this.isSettingUp && vehicleType.canAdjustPitch();
    }

    public boolean canAimPower() {
        return !this.isSettingUp && vehicleType.canAdjustPower();
    }

    /** Used by the input handler to determine whether it should check the movement input keys and send them to the server. */
    public boolean isDrivable() {
        return !this.isSettingUp && vehicleType.isDrivable();
    }

    public boolean isMountable() {
        return !this.isSettingUp && vehicleType.isMountable();
    }

    public boolean isSettingUp() {
        return isSettingUp;
    }

    public int getSetupTicks() {
        return setupTicks;
    }

    public float getRiderVerticalOffset() {
        return vehicleType.getRiderVerticalOffset();
    }

    /**
     * The maximum range in which a point at the given vertical offset can be hit; varies by vehicle type (power and
     * angle, missile offset) and the current ammo. An approximation used by the soldier AI.
     */
    public float getEffectiveRange(float verticalOffset) {
        if (vehicleType instanceof VehicleTypeBatteringRam) {
            // TODO(original): ugly hack
            return 1.5f;
        }
        float angle;
        if (currentTurretPitchMin < 45 && currentTurretPitchMax > 45) {
            // if the angle straddles 45, use 45
            angle = 45;
        } else if (currentTurretPitchMin < 45 && currentTurretPitchMax < 45) {
            // else if both are below 45, use the largest
            angle = currentTurretPitchMax;
        } else {
            // else use the lowest
            angle = currentTurretPitchMin;
        }
        IAmmo ammo = ammoHelper.getCurrentAmmoType();
        return getEffectiveRange(verticalOffset, angle, firingHelper.getAdjustedMaxMissileVelocity(), ammo != null && ammo.isRocket());
    }

    private float getEffectiveRange(float y, float angle, float velocity, boolean rocket) {
        // Obvious fix: the original used the sine for the horizontal and the cosine for the vertical speed, the
        // opposite of MissileBase.setMissileParams2 (cosine of the pitch horizontally, sine vertically), so its
        // result was only right at 45 degrees.
        float motX = Trig.cosDegrees(angle) * velocity * 0.05f;
        float motY = Trig.sinDegrees(angle) * velocity * 0.05f;
        float rocketX = 0;
        float rocketY = 0;
        if (rocket) {
            int rocketBurnTime = (int) (velocity * Trig.ROCKET_BURN_TIME_FACTOR);
            float motX0 = (motX / (velocity * 0.05f)) * Trig.ROCKET_ACCELERATION_FACTOR;
            float motY0 = (motY / (velocity * 0.05f)) * Trig.ROCKET_ACCELERATION_FACTOR;
            motX = motX0;
            motY = motY0;
            while (rocketBurnTime > 0) {
                rocketX += motX;
                rocketY += motY;
                rocketBurnTime--;
                motX += motX0;
                motY += motY0;
            }
            y -= rocketY;
        }
        motX *= 20.f;
        motY *= 20.f;
        float gravity = 9.81f;
        float t = motY / gravity;
        float tQ = Mth.sqrt(((motY * motY) / (gravity * gravity)) - ((2 * y) / gravity));
        float tPlus = t + tQ;
        float tMinus = t - tQ;
        t = Math.max(tPlus, tMinus);
        return (motX * t) + rocketX;
    }

    /** A fully translated offset position for the missile spawn at the current aim and vehicle parameters. */
    public Vec3 getMissileOffset() {
        float x1 = this.vehicleType.getTurretPosX();
        float y1 = this.vehicleType.getTurretPosY();
        float z1 = this.vehicleType.getTurretPosZ();
        float angle;
        float len;
        if (x1 != 0 || z1 != 0) {
            angle = Trig.toDegrees((float) Math.atan2(z1, x1));
            len = Mth.sqrt(x1 * x1 + z1 * z1);
            angle += this.getYRot();
            x1 = Trig.cosDegrees(angle) * len;
            z1 = -Trig.sinDegrees(angle) * len;
        }

        float x = this.vehicleType.getMissileHorizontalOffset();
        float y = this.vehicleType.getMissileVerticalOffset();
        float z = this.vehicleType.getMissileForwardsOffset();
        if (x != 0 || z != 0 || y != 0) {
            angle = Trig.toDegrees((float) Math.atan2(z, x));
            len = Mth.sqrt(x * x + z * z + y * y);
            angle += this.localTurretRotation;
            x = Trig.cosDegrees(angle) * Trig.sinDegrees(localTurretPitch + getXRot()) * len;
            z = -Trig.sinDegrees(angle) * Trig.sinDegrees(localTurretPitch + getXRot()) * len;
            y = Trig.cosDegrees(localTurretPitch + getXRot()) * len;
        }
        x += x1;
        z += z1;
        y += y1;
        return new Vec3(x, y, z);
    }

    /** Called on every tick that the vehicle is 'firing' to update the firing animation and to start launching at the launch point. */
    public void onFiringUpdate() {
        this.firingVarsHelper.onFiringUpdate();
    }

    /** Called every tick after the vehicle has fired, until the reload timer is complete, to update the animations. */
    public void onReloadUpdate() {
        this.firingVarsHelper.onReloadUpdate();
    }

    /** Called every tick after startLaunching() is called, until setFinishedLaunching() is called. */
    public void onLaunchingUpdate() {
        this.firingVarsHelper.onLaunchingUpdate();
    }

    /**
     * Vanilla only lets boats destroy lily pads; vehicles do too (1.12.2: the {@code doBlockCollisions} override with
     * {@code onInsideBlock}). Server side only.
     */
    @Override
    protected void checkInsideBlocks() {
        super.checkInsideBlocks();
        if (level().isClientSide) {
            return;
        }
        AABB box = this.getBoundingBox().deflate(0.001D);
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        if (!level().hasChunksAt(min, max)) {
            return;
        }
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level().getBlockState(pos).is(Blocks.LILY_PAD)) {
                level().destroyBlock(pos.immutable(), true);
            }
        }
    }

    /** Resets all upgradeable stats back to the base for this vehicle. */
    public void resetCurrentStats() {
        this.currentForwardSpeedMax = this.baseForwardSpeed;
        this.currentStrafeSpeedMax = this.baseStrafeSpeed;
        this.currentTurretPitchMin = this.basePitchMin;
        this.currentTurretPitchMax = this.basePitchMax;
        this.currentTurretRotationMax = this.baseTurretRotationMax;
        this.currentReloadTicks = BASE_RELOAD_TICKS;
        this.currentLaunchSpeedPowerMax = this.baseLaunchSpeedMax;
        this.currentExplosionResist = this.baseExplosionResist;
        this.currentFireResist = this.baseFireResist;
        this.currentGenericResist = this.baseGenericResist;
        this.currentWeight = this.baseWeight;
        this.currentAccuracy = this.baseAccuracy;
    }

    private void dropInventories() {
        Level level = level();
        InventoryTools.dropItemsInWorld(level, inventory.ammoInventory, getX(), getY(), getZ());
        InventoryTools.dropItemsInWorld(level, inventory.armorInventory, getX(), getY(), getZ());
        InventoryTools.dropItemsInWorld(level, inventory.upgradeInventory, getX(), getY(), getZ());
        InventoryTools.dropItemsInWorld(level, inventory.storageInventory, getX(), getY(), getZ());
    }

    /** The vehicle drops its inventories when it is destroyed (health at or below 0), not when it is packed or unloaded (1.12.2: {@code setDead}). */
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && !isRemoved() && !packed && getHealth() <= 0 && reason.shouldDestroy()) {
            dropInventories();
        }
        if (!level().isClientSide && !isRemoved()) {
            if (reason.shouldDestroy()) {
                // broken or packed: the horses are let go and get their leads back (an unloaded cart keeps its hitch in its saved data)
                hitch.unhitchAll(true);
            } else {
                hitch.release();
            }
        }
        super.remove(reason);
    }

    /** The rider of the DRIVER seat (seat 0): the one that steers, and that also fires when the vehicle has no gunner. */
    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        if (seatOccupants.length == 0) {
            // the seats are not known yet (a client that has not had the entity data): the old rule
            return getFirstPassenger() instanceof LivingEntity living ? living : null;
        }
        return occupantAt(0) instanceof LivingEntity living ? living : null;
    }

    // ---- seats (rework phase 4) ----

    public int getSeatCount() {
        return vehicleType.getSeats().size();
    }

    public VehicleSeat getSeat(int index) {
        return vehicleType.getSeats().get(index);
    }

    /** The rider of the seat, or null if it is free (or the rider is gone). */
    @Nullable
    public Entity occupantAt(int seat) {
        if (seat < 0 || seat >= seatOccupants.length || seatOccupants[seat] < 0) {
            return null;
        }
        // looked up in the passenger list, not the level: a vehicle that is loading has its riders before any of them is in the level
        int id = seatOccupants[seat];
        for (Entity rider : getPassengers()) {
            if (rider.getId() == id) {
                return rider;
            }
        }
        return null;
    }

    /** The seat the entity sits in, -1 if it is not a rider. */
    public int seatOf(Entity rider) {
        for (int i = 0; i < seatOccupants.length; i++) {
            if (seatOccupants[i] == rider.getId()) {
                return i;
            }
        }
        if (seatOccupants.length == 0) {
            int index = getPassengers().indexOf(rider);
            return index < getSeatCount() ? index : -1;
        }
        return -1;
    }

    public boolean hasPassenger(Entity rider) {
        return getPassengers().contains(rider);
    }

    /** True if the entity rides a DRIVER or GUNNER seat: it may aim, fire and pick the ammo. */
    public boolean isWeaponCrew(Entity rider) {
        int seat = seatOf(rider);
        return seat >= 0 && seat < getSeatCount() && getSeat(seat).role().operatesWeapon();
    }

    /** The rider that fires: the driver, or else the first gunner. Null for an empty vehicle. */
    @Nullable
    public LivingEntity getFiringCrew() {
        LivingEntity driver = getControllingPassenger();
        if (driver != null) {
            return driver;
        }
        for (int i = 1; i < getSeatCount(); i++) {
            if (getSeat(i).role() == SeatRole.GUNNER && occupantAt(i) instanceof LivingEntity gunner) {
                return gunner;
            }
        }
        return null;
    }

    /** How many of the seats are taken by rowers. */
    public int getRowerCount() {
        int count = 0;
        for (int i = 0; i < getSeatCount(); i++) {
            if (getSeat(i).role() == SeatRole.ROWER && occupantAt(i) != null) {
                count++;
            }
        }
        return count;
    }

    private boolean isSeatFree(int seat) {
        return seat >= 0 && seat < seatOccupants.length && occupantAt(seat) == null;
    }

    /** True if a free seat has one of the roles. */
    public boolean hasFreeSeat(SeatRole... roles) {
        for (int i = 0; i < getSeatCount() && i < seatOccupants.length; i++) {
            if (isSeatFree(i)) {
                for (SeatRole role : roles) {
                    if (getSeat(i).role() == role) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** The roles an entity takes, in order: the wanted one first. NPC crew only take the seats that work the weapon, anyone else any seat. */
    private static SeatRole[] roleOrder(Entity entity, @Nullable SeatRole wanted, boolean strict) {
        if (strict && wanted != null) {
            return new SeatRole[] {wanted};
        }
        SeatRole[] order = entity instanceof IVehicleCrew
                ? new SeatRole[] {SeatRole.DRIVER, SeatRole.GUNNER}
                : new SeatRole[] {SeatRole.DRIVER, SeatRole.GUNNER, SeatRole.PASSENGER, SeatRole.ROWER};
        if (wanted == null) {
            return order;
        }
        SeatRole[] result = new SeatRole[order.length + 1];
        result[0] = wanted;
        System.arraycopy(order, 0, result, 1, order.length);
        return result;
    }

    /** The seat the entity would get now, -1 if there is none. */
    public int findSeatFor(Entity entity, @Nullable SeatRole wanted) {
        int seat = findSeat(entity, wanted, true);
        return seat >= 0 ? seat : findSeat(entity, wanted, false);
    }

    /** @param honourReserved skip the seats kept for riders that are still re-attaching after a load */
    private int findSeat(Entity entity, @Nullable SeatRole wanted, boolean honourReserved) {
        for (SeatRole role : roleOrder(entity, wanted, boardingStrict)) {
            for (int i = 0; i < getSeatCount() && i < seatOccupants.length; i++) {
                if (getSeat(i).role() == role && isSeatFree(i) && !(honourReserved && isReservedForOther(i, entity))) {
                    return i;
                }
            }
        }
        return -1;
    }

    private boolean isReservedForOther(int seat, Entity entity) {
        if (pendingSeats.isEmpty()) {
            return false;
        }
        for (java.util.Map.Entry<java.util.UUID, Integer> e : pendingSeats.entrySet()) {
            if (e.getValue() == seat && !e.getKey().equals(entity.getUUID())) {
                return true;
            }
        }
        return false;
    }

    /** SERVER: the seat a rider had when the vehicle was saved, if it is still free and the rider has not been given another one; else -1. */
    private int restoreSeat(Entity passenger) {
        Integer saved = pendingSeats.remove(passenger.getUUID());
        if (saved != null && saved >= 0 && saved < seatOccupants.length && saved < getSeatCount() && isSeatFree(saved)) {
            return saved;
        }
        return -1;
    }

    /** True if the entity could board now (a free seat it may take). */
    public boolean hasFreeSeatFor(Entity entity) {
        return findSeatFor(entity, null) >= 0;
    }

    /** Boards the entity into a free seat of the role (null: the first free one of its usual order). Server side. @return false if there was no seat */
    public boolean boardAs(Entity entity, @Nullable SeatRole wanted) {
        boardingRole = wanted;
        try {
            return entity.startRiding(this);
        } finally {
            boardingRole = null;
        }
    }

    /**
     * Boards the entity into a free seat of exactly this role, or not at all (no fall back to another role, which {@link #boardAs} has). It is how an NPC is put
     * on a ship as a rower, gunner or passenger: NPC crew would otherwise only be given the driver and gunner seats. Server side.
     *
     * @return false if there was no free seat of the role (or the entity could not ride)
     */
    public boolean boardStrict(Entity entity, SeatRole role) {
        boardingRole = role;
        boardingStrict = true;
        try {
            return entity.startRiding(this);
        } finally {
            boardingRole = null;
            boardingStrict = false;
        }
    }

    /** Moves a rider to another free seat (the number keys). Server side. @return false if the seat does not exist, is taken, or the rider does not ride here */
    public boolean switchSeat(Entity rider, int seat) {
        int current = seatOf(rider);
        if (level().isClientSide || current < 0 || seat < 0 || seat >= seatOccupants.length || seat == current || !isSeatFree(seat)) {
            return false;
        }
        if (rider instanceof IVehicleCrew && !getSeat(seat).role().operatesWeapon()) {
            return false;
        }
        seatOccupants[current] = -1;
        seatOccupants[seat] = rider.getId();
        publishSeats();
        return true;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        if (level().isClientSide) {
            return getPassengers().size() < getSeatCount();
        }
        return findSeatFor(passenger, boardingRole) >= 0;
    }

    /** Position syncing is the custom move payload's job, vanilla's entity tracker updates are ignored. */
    @Override
    public boolean isControlledByLocalInstance() {
        return false;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
    }

    @Override
    protected MovementEmission getMovementEmission() {
        // 1.12.2: canTriggerWalking() false
        return MovementEmission.NONE;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.onUpdateClient();
        }
        this.updateTurretPitch();
        this.updateTurretRotation();
        if (!level().isClientSide && pendingSeatTicks > 0 && --pendingSeatTicks == 0) {
            pendingSeats.clear();
        }
        if (!level().isClientSide && vehicleType.isHorseDrawn()) {
            this.hitch.tick();
            if (this.breakDamage > 0.f) {
                this.breakDamage = Math.max(0.f, this.breakDamage - 1.f);
            }
        }
        if (ship != null) {
            ship.preTick();
        }
        this.moveHelper.onUpdate();
        if (ship != null) {
            ship.postTick();
        }
        this.firingHelper.onTick();
        this.firingVarsHelper.onTick();
        if (this.hitAnimationTicks > 0) {
            this.hitAnimationTicks--;
        }
        if (this.isSettingUp) {
            this.setupTicks--;
            if (this.setupTicks <= 0) {
                this.isSettingUp = false;
            }
        }
        if (this.hurtInvulTicks > 0) {
            this.hurtInvulTicks--;
        }
    }

    /** Client side updates: an NPC rider is repositioned every tick. */
    private void onUpdateClient() {
        LivingEntity rider = getControllingPassenger();
        if (rider instanceof IVehicleCrew) {
            this.positionRider(rider);
        }
        for (Entity other : getPassengers()) {
            if (other != rider && other instanceof IVehicleCrew) {
                this.positionRider(other);
            }
        }
    }

    /**
     * Where a rider that gets off lands (1.12.2: {@code handleDismount}): the first solid or water block, searched
     * around and below the vehicle, with two blocks of air above it. Vanilla calls this when a player sneaks off.
     */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        if (ship != null && ship.spec() != null) {
            // a ship's rider steps out onto the deck beside his seat
            int seatIndex = seatOf(passenger);
            VehicleSeat seat = getSeat(seatIndex >= 0 && seatIndex < getSeatCount() ? seatIndex : 0);
            return ship.toWorld(seat.forward(), seat.horizontal(), ship.spec().deckTop() + 0.05F);
        }
        int xMin = Mth.floor(this.getX() - this.getBbWidth() / 2);
        int zMin = Mth.floor(this.getZ() - this.getBbWidth() / 2);
        int yMin = Mth.floor(getY()) - 2;
        int size = (int) getBbWidth();

        for (int y = yMin; y <= yMin + 3; y++) {
            for (int x = xMin; x <= xMin + size; x++) {
                for (int z = zMin; z <= zMin + size; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level().getBlockState(pos);
                    if (state.isFaceSturdy(level(), pos, Direction.UP) || state.getFluidState().is(FluidTags.WATER)) {
                        if (level().isEmptyBlock(pos.above()) && level().isEmptyBlock(pos.above(2))) {
                            return new Vec3(x + 0.5d, y + 1, z + 0.5d);
                        }
                    }
                }
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    private void updateTurretPitch() {
        float prevPitch = this.localTurretPitch;
        if (!Trig.isAngleBetween(localTurretPitch, currentTurretPitchMin, currentTurretPitchMax)) {
            localTurretPitch = currentTurretPitchMin;
        }

        if (!Trig.isAngleBetween(localTurretDestPitch, currentTurretPitchMin, currentTurretPitchMax)) {
            localTurretDestPitch = currentTurretPitchMin;
        }

        if (!canAimPitch()) {
            localTurretDestPitch = localTurretPitch;
        }

        if (!Trig.anglesEqual(localTurretPitch, localTurretDestPitch)) {
            if (Math.abs(Trig.getAngleDiffSigned(localTurretDestPitch, localTurretPitch)) < localTurretPitchInc) {
                localTurretPitch = localTurretDestPitch;
            } else {
                localTurretPitch += Trig.getAngleDiffSigned(localTurretPitch, localTurretDestPitch) > 0 ? localTurretPitchInc : -localTurretPitchInc;
            }
        }
        this.currentTurretPitchSpeed = prevPitch - this.localTurretPitch;
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (!level().isClientSide) {
            // a rider that comes back after a load gets the seat it had; anyone else the first free seat of the usual order
            int seat = restoreSeat(passenger);
            if (seat < 0) {
                seat = findSeatFor(passenger, boardingRole);
            }
            if (seat >= 0 && seat < seatOccupants.length) {
                seatOccupants[seat] = passenger.getId();
                publishSeats();
            }
        }
        if (passenger instanceof IVehicleCrew crew && crew.isFactionSiegeEngineer()) {
            currentTurretPitchMin = vehicleType.getBasePitchMin() - 4 * 3;
            currentTurretPitchMax = vehicleType.getBasePitchMax() + 4 * 3;
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide) {
            for (int i = 0; i < seatOccupants.length; i++) {
                if (seatOccupants[i] == passenger.getId()) {
                    seatOccupants[i] = -1;
                    publishSeats();
                }
            }
        }
        if (passenger instanceof IVehicleCrew crew && crew.isFactionSiegeEngineer()) {
            upgradeHelper.updateUpgradeStats();
        }
    }

    private void updateTurretRotation() {
        float prevYaw = this.localTurretRotation;
        this.localTurretRotationHome = Trig.wrapTo360(this.getYRot());
        if (!canAimRotate()) {
            localTurretRotation = Trig.wrapTo360(this.getYRot());
            localTurretDestRot = localTurretRotation;
        }
        if (Math.abs(localTurretDestRot - localTurretRotation) > localTurretRotInc) {
            while (localTurretRotation < 0) {
                localTurretRotation += 360;
                prevYaw += 360;
            }
            while (localTurretRotation >= 360) {
                localTurretRotation -= 360;
                prevYaw -= 360;
            }
            localTurretDestRot = Trig.wrapTo360(localTurretDestRot);
            float curMod = localTurretRotation;
            float destMod = localTurretDestRot;
            float diff = curMod > destMod ? curMod - destMod : destMod - curMod;
            float turnDir = 0;
            if (curMod > destMod) {
                turnDir = diff < 180 ? -1 : 1;
            } else if (curMod < destMod) {
                turnDir = diff < 180 ? 1 : -1;
            }
            localTurretRotation += localTurretRotInc * turnDir;
        } else {
            localTurretRotation = localTurretDestRot;
        }
        if (Math.abs(localTurretDestRot - localTurretRotation) < localTurretRotInc) {
            localTurretRotation = localTurretDestRot;
        }
        this.currentTurretYawSpeed = this.localTurretRotation - prevYaw;
        if (this.currentTurretYawSpeed > 180) {
            this.currentTurretYawSpeed -= 360.f;
        }
        if (this.currentTurretYawSpeed < -180) {
            this.currentTurretYawSpeed += 360.f;
        }
    }

    public void updateTurretAngles(float pitch, float rotation) {
        this.localTurretPitch = pitch;
        this.localTurretRotation = rotation;
        this.localTurretDestPitch = this.localTurretPitch;
        this.localTurretDestRot = this.localTurretRotation;
    }

    /** Spits the inventories out into the world, packs the vehicle into an item, also spat into the world, and removes it. */
    public void packVehicle() {
        if (!this.level().isClientSide && !isRemoved()) {
            packed = true;
            InventoryTools.dropItemInWorld(level(), getItemForVehicle(), getX(), getY(), getZ());
            dropInventories();
            this.discard();
        }
    }

    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        if (isInvulnerableTo(damageSource) || (ship != null && ship.isSinking())) {
            return false;
        }
        if (!damageSource.is(DamageTypeTags.IS_EXPLOSION)) {
            if (hurtInvulTicks > 0) {
                return false;
            }
            hurtInvulTicks = 10;
        }

        if (this.level().isClientSide) {
            hitAnimationTicks = 20;
            return false;
        }
        if (vehicleType.breaksOnAttack() && damageSource.getEntity() instanceof Player attacker && damageSource.getDirectEntity() == attacker) {
            // a player's blow breaks a cart the way it breaks a boat
            markHurt();
            level().broadcastDamageEvent(this, damageSource);
            breakDamage += amount * 10.f;
            if (attacker.getAbilities().instabuild) {
                packed = true;
                dropInventories();
                discard();
            } else if (breakDamage > 40.f) {
                packVehicle();
            }
            return true;
        }
        markHurt();
        level().broadcastDamageEvent(this, damageSource);
        float adjDmg = upgradeHelper.getScaledDamage(damageSource, amount);
        this.setHealth(getHealth() - adjDmg);
        if (getHealth() <= 0) {
            if (ship != null) {
                // a ship goes down slowly and drops its cargo on the way (ShipHelper)
                ship.startSinking();
                return true;
            }
            kill();
            return false;
        }
        return true;
    }

    /** Clients get the damage event the server broadcasts in {@link #hurt}. */
    @Override
    public void handleDamageEvent(DamageSource damageSource) {
        hitAnimationTicks = 20;
    }

    /** A gentle push of other entities (and of the vehicle by them): the original's 0.9 collision reduction. Riders and NPC crew are not pushed. */
    @Override
    public void push(Entity entity) {
        if (entity != getControllingPassenger() && !(entity instanceof IVehicleCrew)) {
            double xDiff = entity.getX() - this.getX();
            double zDiff = entity.getZ() - this.getZ();
            double entityDistance = Mth.absMax(xDiff, zDiff);

            if (entityDistance >= 0.009999999776482582D) {
                entityDistance = Math.sqrt(entityDistance);
                xDiff /= entityDistance;
                zDiff /= entityDistance;
                double normalizeToDistance = 1.0D / entityDistance;

                if (normalizeToDistance > 1.0D) {
                    normalizeToDistance = 1.0D;
                }

                xDiff *= normalizeToDistance;
                zDiff *= normalizeToDistance;
                xDiff *= 0.05000000074505806D;
                zDiff *= 0.05000000074505806D;
                xDiff *= (double) (1.0F - ENTITY_COLLISION_REDUCTION);
                zDiff *= (double) (1.0F - ENTITY_COLLISION_REDUCTION);
                this.push(-xDiff, 0.0D, -zDiff);
                entity.push(xDiff, 0.0D, zDiff);
            }
        }
    }

    public ResourceLocation getTexture() {
        return vehicleType.getTextureForMaterialLevel(vehicleMaterialLevel);
    }

    /**
     * Places a rider from the type's offsets: vertical offset on y, the forward offset along the (negative)
     * yaw direction, the horizontal offset along yaw + 90; the turret yaw replaces the vehicle yaw if the rider moves
     * with the turret. The feet land where 1.12.2 put them (players 0.35 below the offset position).
     */
    @Override
    protected void positionRider(Entity passenger, MoveFunction callback) {
        int seatIndex = seatOf(passenger);
        VehicleSeat seat = getSeat(seatIndex >= 0 && seatIndex < getSeatCount() ? seatIndex : 0);
        float forward = seat.forward();
        float horizontal = seat.horizontal();
        double posX = this.getX();
        double posY = this.getY() + seat.vertical();
        double posZ = this.getZ();

        float yaw = this.vehicleType.moveRiderWithTurret() && seat.role().operatesWeapon() ? localTurretRotation : getYRot();
        posX += Trig.sinDegrees(yaw) * -forward;
        posX += Trig.sinDegrees(yaw + 90) * horizontal;
        posZ += Trig.cosDegrees(yaw) * -forward;
        posZ += Trig.cosDegrees(yaw + 90) * horizontal;
        double yOffset = passenger instanceof Player ? -0.35D : 0.D;
        callback.accept(passenger, posX, posY + yOffset, posZ);
        if (passenger instanceof IVehicleCrew) {
            float crewVehicleYaw = localTurretRotation;
            int mount = ship != null && ship.hasSpec() ? ship.hardpointOf(passenger) : -1;
            if (mount >= 0) {
                // the gunner of a ship faces where his mount points
                crewVehicleYaw = getYRot() + ship.hardpoint(mount).yaw;
            }
            float crewYaw = 180 - crewVehicleYaw;
            passenger.setYRot(crewYaw);
            passenger.setYBodyRot(crewYaw);
        } else if (moveHelper.riderViewFollows) {
            passenger.setYRot(passenger.getYRot() - this.moveHelper.getRotationSpeed());
        }
    }

    @Override
    public boolean shouldRiderSit() {
        return this.vehicleType.shouldRiderSit();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.isSettingUp) {
            if (!player.level().isClientSide) {
                player.sendSystemMessage(Component.translatable("message.ancientwarfarevehicle.vehicle_setting_up", setupTicks));
            }
            return InteractionResult.PASS;
        }
        if (vehicleType.isHorseDrawn() && player.getItemInHand(hand).is(Items.LEAD)) {
            return interactWithLead(player);
        }
        return this.firingVarsHelper.interact(player) ? InteractionResult.sidedSuccess(level().isClientSide) : InteractionResult.PASS;
    }

    /**
     * A lead used on a horse-drawn cart: while the player leads a horse, donkey or mule it is hitched (the animal's own lead is taken, the lead in
     * the hand stays); a sneaking use unhitches the horse that was hitched last and gives its lead to the player.
     */
    private InteractionResult interactWithLead(Player player) {
        if (level().isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }
        if (player.isShiftKeyDown()) {
            List<AbstractHorse> horses = hitch.horses();
            if (horses.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.ancientwarfarevehicle.hitch.none"), true);
            } else {
                AbstractHorse horse = horses.get(horses.size() - 1);
                hitch.unhitch(horse, false);
                if (!player.getAbilities().instabuild) {
                    ItemStack lead = new ItemStack(Items.LEAD);
                    if (!player.getInventory().add(lead)) {
                        player.drop(lead, false);
                    }
                }
                player.displayClientMessage(Component.translatable("message.ancientwarfarevehicle.hitch.unhitched"), true);
            }
            return InteractionResult.SUCCESS;
        }
        AbstractHorse led = null;
        for (Mob mob : level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(12.0D), m -> m instanceof AbstractHorse && CartHitch.isLedBy(m, player))) {
            if (led == null || mob.distanceToSqr(this) < led.distanceToSqr(this)) {
                led = (AbstractHorse) mob;
            }
        }
        if (led == null) {
            player.displayClientMessage(Component.translatable("message.ancientwarfarevehicle.hitch.no_led"), true);
        } else {
            CartHitch.Result result = hitch.hitch(led);
            player.displayClientMessage(Component.translatable("message.ancientwarfarevehicle.hitch." + result.name().toLowerCase(java.util.Locale.ROOT)), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public String toString() {
        return String.format("%s::%s @ %.2f, %.2f, %.2f  -- y:%.2f p:%.2f -- m: %s", this.vehicleType.getDisplayName(), this.getId(),
                this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot(), this.getDeltaMovement());
    }

    /** Players and mobs can stand on a vehicle (1.12.2: {@code getCollisionBoundingBox} and {@code canBeCollidedWith}). */
    @Override
    public boolean canBeCollidedWith() {
        // the hull of a ship is its parts; the box of the entity itself is not solid
        return ship == null;
    }

    /** Vehicles can be targeted and hit (1.12.2 used canBeCollidedWith for that too). */
    @Override
    public boolean isPickable() {
        return true;
    }

    // ---- spawn data, saved data ----

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        var registries = level().registryAccess();
        buffer.writeFloat(this.getHealth());
        buffer.writeInt(this.vehicleType.getGlobalVehicleType());
        buffer.writeInt(this.vehicleMaterialLevel);
        buffer.writeNbt(upgradeHelper.serializeNBT(registries));
        buffer.writeNbt(ammoHelper.serializeNBT(registries));
        buffer.writeNbt(moveHelper.serializeNBT(registries));
        buffer.writeNbt(firingHelper.serializeNBT(registries));
        buffer.writeNbt(firingVarsHelper.serializeNBT(registries));
        buffer.writeFloat(localLaunchPower);
        buffer.writeFloat(localTurretPitch);
        buffer.writeFloat(localTurretRotation);
        buffer.writeFloat(localTurretDestPitch);
        buffer.writeFloat(localTurretDestRot);
        owner.serializeToBuffer(buffer);
        buffer.writeFloat(localTurretRotationHome);
        buffer.writeBoolean(this.isSettingUp);
        if (this.isSettingUp) {
            buffer.writeInt(this.setupTicks);
        }
        if (ship != null) {
            ship.writeSpawn(buffer);
        }
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
        var registries = level().registryAccess();
        float health = additionalData.readFloat();
        IVehicleType type = VehicleType.getVehicleType(additionalData.readInt());
        int materialLevel = additionalData.readInt();
        if (type != null) {
            // refreshes the dimensions, which also rebuilds the bounding box around the position
            this.setVehicleType(type, materialLevel);
        }
        this.setHealth(health);
        this.upgradeHelper.deserializeNBT(registries, readTag(additionalData));
        this.ammoHelper.deserializeNBT(registries, readTag(additionalData));
        this.moveHelper.deserializeNBT(registries, readTag(additionalData));
        this.firingHelper.deserializeNBT(registries, readTag(additionalData));
        this.firingVarsHelper.deserializeNBT(registries, readTag(additionalData));
        this.localLaunchPower = additionalData.readFloat();
        this.localTurretPitch = additionalData.readFloat();
        this.localTurretRotation = additionalData.readFloat();
        this.localTurretDestPitch = additionalData.readFloat();
        this.localTurretDestRot = additionalData.readFloat();
        this.firingHelper.clientLaunchSpeed = localLaunchPower;
        this.firingHelper.clientTurretPitch = localTurretPitch;
        this.firingHelper.clientTurretYaw = localTurretRotation;
        this.upgradeHelper.updateUpgradeStats();
        owner = new Owner(additionalData);
        this.localTurretRotationHome = additionalData.readFloat();
        this.isSettingUp = additionalData.readBoolean();
        if (this.isSettingUp) {
            this.setupTicks = additionalData.readInt();
        }
        if (ship != null) {
            ship.readSpawn(additionalData);
        }
    }

    private static CompoundTag readTag(RegistryFriendlyByteBuf buffer) {
        CompoundTag tag = buffer.readNbt();
        return tag == null ? new CompoundTag() : tag;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        var registries = level().registryAccess();
        IVehicleType vehType = VehicleType.getVehicleType(tag.getInt("vehType"));
        if (vehType == null) {
            return;
        }
        int level = tag.getInt("matLvl");
        this.setVehicleType(vehType, level);
        this.setHealth(tag.getFloat("health"));
        this.localTurretRotationHome = tag.getFloat("turHome");
        this.inventory.readFromNBT(tag, registries);
        this.upgradeHelper.deserializeNBT(registries, tag.getCompound("upgrades"));
        this.ammoHelper.deserializeNBT(registries, tag.getCompound("ammo"));
        this.moveHelper.deserializeNBT(registries, tag.getCompound("move"));
        this.firingHelper.deserializeNBT(registries, tag.getCompound("fire"));
        this.firingVarsHelper.deserializeNBT(registries, tag.getCompound("vars"));
        this.localLaunchPower = tag.getFloat("lc");
        this.localTurretPitch = tag.getFloat("tp");
        this.localTurretDestPitch = tag.getFloat("tpd");
        this.localTurretRotation = tag.getFloat("tr");
        this.localTurretDestRot = tag.getFloat("trd");
        // rebuild the derived state from the inventory contents
        this.upgradeHelper.updateUpgrades();
        this.ammoHelper.updateAmmoCounts();
        owner = Owner.deserializeFromNBT(tag);
        this.isSettingUp = tag.getBoolean("setup");
        if (this.isSettingUp) {
            this.setupTicks = tag.getInt("sTick");
        }
        this.hitch.load(tag.getList("hitch", Tag.TAG_COMPOUND));
        // the riders attach after the vehicle has loaded: remember who sat where and give the seats back as they arrive
        this.pendingSeats.clear();
        for (Tag entry : tag.getList("seats", Tag.TAG_COMPOUND)) {
            CompoundTag seatTag = (CompoundTag) entry;
            if (seatTag.hasUUID("u")) {
                this.pendingSeats.put(seatTag.getUUID("u"), seatTag.getInt("s"));
            }
        }
        this.pendingSeatTicks = this.pendingSeats.isEmpty() ? 0 : PENDING_SEAT_TICKS;
        if (ship != null) {
            ship.load(tag);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        var registries = level().registryAccess();
        tag.putInt("vehType", this.vehicleType.getGlobalVehicleType());
        tag.putInt("matLvl", this.vehicleMaterialLevel);
        tag.putFloat("health", this.getHealth());
        tag.putFloat("turHome", this.localTurretRotationHome);
        this.inventory.writeToNBT(tag, registries);
        tag.put("upgrades", this.upgradeHelper.serializeNBT(registries));
        tag.put("ammo", this.ammoHelper.serializeNBT(registries));
        tag.put("move", this.moveHelper.serializeNBT(registries));
        tag.put("fire", this.firingHelper.serializeNBT(registries));
        tag.put("vars", this.firingVarsHelper.serializeNBT(registries));
        tag.putFloat("lc", localLaunchPower);
        tag.putFloat("tp", localTurretPitch);
        tag.putFloat("tpd", localTurretDestPitch);
        tag.putFloat("tr", localTurretRotation);
        tag.putFloat("trd", localTurretDestRot);
        owner.serializeToNBT(tag);
        if (this.hitch.isHitched()) {
            tag.put("hitch", this.hitch.save());
        }
        net.minecraft.nbt.ListTag seatList = new net.minecraft.nbt.ListTag();
        java.util.Set<java.util.UUID> written = new java.util.HashSet<>();
        for (int i = 0; i < seatOccupants.length; i++) {
            Entity rider = occupantAt(i);
            if (rider != null) {
                CompoundTag seatTag = new CompoundTag();
                seatTag.putInt("s", i);
                seatTag.putUUID("u", rider.getUUID());
                seatList.add(seatTag);
                written.add(rider.getUUID());
            }
        }
        // riders that have not re-attached yet keep their seat if the vehicle is saved again meanwhile
        for (java.util.Map.Entry<java.util.UUID, Integer> e : pendingSeats.entrySet()) {
            if (written.add(e.getKey())) {
                CompoundTag seatTag = new CompoundTag();
                seatTag.putInt("s", e.getValue());
                seatTag.putUUID("u", e.getKey());
                seatList.add(seatTag);
            }
        }
        if (!seatList.isEmpty()) {
            tag.put("seats", seatList);
        }
        tag.putBoolean("setup", this.isSettingUp);
        if (this.isSettingUp) {
            tag.putInt("sTick", this.setupTicks);
        }
        if (ship != null) {
            ship.save(tag);
        }
    }

    // ---- missile callbacks ----

    @Override
    public void onMissileImpact(Level level, double x, double y, double z) {
        if (getVehicle() instanceof IMissileHitCallback callback) {
            callback.onMissileImpact(level, x, y, z);
        }
    }

    @Override
    public void onMissileImpactEntity(Level level, Entity entity) {
        if (getVehicle() instanceof IMissileHitCallback callback) {
            callback.onMissileImpactEntity(level, entity);
        }
    }

    // ---- IPathableEntity ----

    @Override
    public void setMoveTo(double x, double y, double z, float moveSpeed) {
        this.moveHelper.setMoveTo(x, y, z);
    }

    @Override
    public boolean isPathableEntityOnLadder() {
        return false;
    }

    @Override
    public Entity getEntity() {
        return this;
    }

    @Override
    public void setPath(List<Node> path) {
        this.nav.forcePath(path);
    }

    public void clearPath() {
        this.nav.clearPath();
    }

    @Override
    public float getDefaultMoveSpeed() {
        return this.currentForwardSpeedMax;
    }

    @Override
    public void onStuckDetected() {
        if (getControllingPassenger() instanceof IVehicleCrew crew) {
            crew.onVehicleStuck();
        }
    }

    // ---- IOwnable ----

    @Override
    public void setOwner(Player player) {
        owner = new Owner(player);
    }

    @Override
    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    @Override
    public Owner getOwner() {
        return owner;
    }

    @Override
    public boolean isOwner(Player player) {
        return owner.isOwnerOrSameTeamOrFriend(player);
    }

    public boolean isAmmoLoaded() {
        return vehicleType.getValidAmmoTypes().stream().anyMatch(a -> ammoHelper.getCountOf(a) > 0);
    }

    /** The ram's damage source: the {@code ancientwarfarevehicle:battering} damage type without an attacker, as in the original. */
    public DamageSource createBatteringDamage() {
        return new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BATTERING_DAMAGE));
    }
}
