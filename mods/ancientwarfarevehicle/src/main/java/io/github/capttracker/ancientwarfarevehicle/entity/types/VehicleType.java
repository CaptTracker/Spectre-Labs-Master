/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleType.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.AncientWarfareVehicles;
import io.github.capttracker.ancientwarfarevehicle.armors.IVehicleArmor;
import io.github.capttracker.ancientwarfarevehicle.entity.IVehicleType;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBridge;
import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleMovementType;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSeat;
import io.github.capttracker.ancientwarfarevehicle.entity.materials.IVehicleMaterial;
import io.github.capttracker.ancientwarfarevehicle.missiles.IAmmo;
import io.github.capttracker.ancientwarfarevehicle.upgrades.IVehicleUpgradeType;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A first-tier data construct describing a vehicle. Each vehicle references one of these, where it gets its base
 * stats from. The tiers are: the vehicle type itself, the vehicle material (see {@link IVehicleMaterial}), and
 * the on-board upgrades.
 *
 * <p>The types are constructed (once) by {@code VehicleRegistry}, after the ammo, upgrade and armor registries
 * are filled and the config has loaded: the constructors read all of those.
 *
 * <p>Changes from 1.12.2: the display name is the translation key of the type's spawner item, the tooltip lines
 * are the 1.12.2 keys {@code item.vehicleSpawner.tooltip.*}, and the textures are in this mod's namespace.
 */
public abstract class VehicleType implements IVehicleType {
    /** Self registry: the types by their numeric id. */
    public static final IVehicleType[] vehicleTypes = new IVehicleType[1024];

    public float width = 2;
    public float height = 2;
    /** kg */
    public float weight = 1000;

    private final int vehicleType;
    @Nullable
    public IVehicleMaterial vehicleMaterial = null;
    public boolean mountable = false;
    public boolean drivable = false;
    public boolean combatEngine = false;
    public boolean yawAdjustable = false;
    public boolean pitchAdjustable = false;
    public boolean powerAdjustable = false;
    public boolean pilotableBySoldiers = true;

    public float turretForwardsOffset = 0.f;
    public float turretHorizontalOffset = 0.f;
    public float turretVerticalOffset = 0.f;
    public float missileForwardsOffset = 0.f;
    public float missileHorizontalOffset = 0.f;
    public float missileVerticalOffset = 0.f;
    public float riderForwardsOffset = 0.f;
    public float riderHorizontalOffset = 0.f;
    public float riderVerticalOffset = 0.f;
    public boolean riderSits = true;
    public boolean riderMovesWithTurret = false;

    public float minAttackDistance = 5.f;

    /** The seats after the driver seat; a type adds them in its constructor with {@link #addSeat}. */
    private final List<VehicleSeat> extraSeats = new ArrayList<>();
    @Nullable
    private List<VehicleSeat> seats = null;
    /** The chase camera distance; 0 means the default from the size of the vehicle. */
    public float cameraDistance = 0.f;

    /** Horse-drawn carts (rework phase 4): see {@link IVehicleType#isHorseDrawn()}. */
    public boolean horseDrawn = false;
    public int maxHorses = 0;
    public float hitchOffset = 0.f;
    public float maxHitchAngle = 70.f;
    public boolean breaksOnAttack = false;

    /** Ships (rework phase 4): the design of the hull; null for every other type. */
    @Nullable
    public io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipSpec shipSpec = null;

    public float baseForwardSpeed;
    public float baseStrafeSpeed;

    public float basePitchMin;
    public float basePitchMax;

    public float turretRotationMax;
    public float baseMissileVelocityMax;
    public float baseHealth = 100;

    public float maxMissileWeight = 10;

    public float accuracy = 1.f;

    /** A translation key. */
    public String displayName = "AWVehicleBase";
    /** Translation keys. */
    public List<String> displayTooltip = new ArrayList<>();

    public List<IAmmo> validAmmoTypes = new ArrayList<>();
    public List<IVehicleUpgradeType> validUpgrades = new ArrayList<>();
    public List<IVehicleArmor> validArmors = new ArrayList<>();
    public Map<Integer, IAmmo> ammoBySoldierRank = new HashMap<>();

    int storageBaySize = 0;
    int ammoBaySize = 6;
    int upgradeBaySize = 3;
    int armorBaySize = 3;

    /** Informational only; nothing reads it (the material table has five levels whatever this says). */
    public int materialCount = 1;
    protected String configName = "none";
    protected boolean enabled = true;
    protected boolean enabledForCrafting = true;
    protected boolean enabledForLoot = true;

    protected VehicleMovementType movementType = VehicleMovementType.GROUND;

    public VehicleType(int typeNum) {
        this.vehicleType = typeNum;
        vehicleTypes[typeNum] = this;
    }

    /** The translation key of the type's name: the name of its spawner item, {@code item.ancientwarfarevehicle.<config name>_spawner}. Call after {@code configName} is set. */
    protected String nameKey() {
        return "item." + AncientWarfareVehicles.MOD_ID + "." + configName + "_spawner";
    }

    /** The translation key of a tooltip line; the keys keep their 1.12.2 names. */
    protected static String tooltipKey(String name) {
        return "item.vehicleSpawner.tooltip." + name;
    }

    /**
     * The texture of one material level: {@code textures/model/vehicle/<baseName>_<level + 1>.png}, level 1 for a
     * level outside 0 to 4.
     */
    protected static ResourceLocation vehicleTexture(String baseName, int level) {
        int number = level >= 0 && level <= 4 ? level + 1 : 1;
        return Ids.of(AncientWarfareVehicles.MOD_ID, "textures/model/vehicle/" + baseName + "_" + number + ".png");
    }

    @Override
    public VehicleMovementType getMovementType() {
        return this.movementType;
    }

    @Nullable
    @Override
    public IAmmo getAmmoForSoldierRank(int rank) {
        if (this.ammoBySoldierRank.containsKey(rank)) {
            return this.ammoBySoldierRank.get(rank);
        }
        List<IAmmo> ammos = this.getValidAmmoTypes();
        if (!ammos.isEmpty()) {
            return ammos.get(0);
        }
        return null;
    }

    /** Adds a seat after the driver seat (call from the constructor, after the rider offsets are set; the driver seat is made from the rider offsets). */
    protected void addSeat(SeatRole role, float forward, float horizontal, float vertical) {
        extraSeats.add(new VehicleSeat(role, forward, horizontal, vertical));
        seats = null;
    }

    /** As {@link #addSeat(SeatRole, float, float, float)} for a gunner seat: it works the turret mount {@code turret} of a ship. */
    protected void addSeat(SeatRole role, float forward, float horizontal, float vertical, int turret) {
        extraSeats.add(new VehicleSeat(role, forward, horizontal, vertical, turret));
        seats = null;
    }

    /** The driver seat (the old single rider, with the rider offsets) and then the extra seats. Built on first use, when the constructors are done. */
    @Override
    public List<VehicleSeat> getSeats() {
        List<VehicleSeat> list = seats;
        if (list == null) {
            List<VehicleSeat> built = new ArrayList<>(1 + extraSeats.size());
            built.add(new VehicleSeat(SeatRole.DRIVER, riderForwardsOffset, riderHorizontalOffset, riderVerticalOffset));
            built.addAll(extraSeats);
            list = List.copyOf(built);
            seats = list;
        }
        return list;
    }

    @Override
    public boolean isHorseDrawn() {
        return horseDrawn;
    }

    @Override
    public int getMaxHorses() {
        return maxHorses;
    }

    @Override
    public float getHitchOffset() {
        return hitchOffset;
    }

    @Override
    public float getMaxHitchAngle() {
        return maxHitchAngle;
    }

    @Nullable
    @Override
    public io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipSpec getShipSpec() {
        return shipSpec;
    }

    @Override
    public boolean breaksOnAttack() {
        return breaksOnAttack;
    }

    @Override
    public float getCameraDistance() {
        return cameraDistance > 0 ? cameraDistance : Math.max(4.0F, 2.5F + Math.max(width, height) * 1.5F);
    }

    @Override
    public boolean canSoldiersPilot() {
        return this.pilotableBySoldiers;
    }

    @Override
    public void setEnabled(boolean val) {
        this.enabled = val;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getConfigName() {
        return configName;
    }

    @Override
    public int getGlobalVehicleType() {
        return this.vehicleType;
    }

    @Override
    public IVehicleMaterial getMaterialType() {
        return this.vehicleMaterial;
    }

    @Override
    public boolean isMountable() {
        return this.mountable;
    }

    @Override
    public boolean isDrivable() {
        return this.drivable;
    }

    @Override
    public boolean isCombatEngine() {
        return this.combatEngine;
    }

    @Override
    public boolean canAdjustYaw() {
        return this.yawAdjustable;
    }

    @Override
    public boolean canAdjustPitch() {
        return pitchAdjustable;
    }

    @Override
    public boolean canAdjustPower() {
        return powerAdjustable;
    }

    @Override
    public float getRiderForwardsOffset() {
        return this.riderForwardsOffset;
    }

    @Override
    public float getRiderHorizontalOffset() {
        return this.riderHorizontalOffset;
    }

    @Override
    public float getRiderVerticalOffset() {
        return this.riderVerticalOffset;
    }

    @Override
    public float getMinAttackDistance() {
        return this.minAttackDistance;
    }

    @Override
    public float getBaseForwardSpeed() {
        return this.baseForwardSpeed;
    }

    @Override
    public float getBaseStrafeSpeed() {
        return this.baseStrafeSpeed;
    }

    @Override
    public float getBasePitchMin() {
        return this.basePitchMin;
    }

    @Override
    public float getBasePitchMax() {
        return this.basePitchMax;
    }

    @Override
    public float getBaseHealth() {
        return this.baseHealth;
    }

    @Override
    public float getWidth() {
        return this.width;
    }

    @Override
    public float getHeight() {
        return this.height;
    }

    @Override
    public ResourceLocation getTextureForMaterialLevel(int level) {
        return Ids.of(AncientWarfareVehicles.MOD_ID, "foo");
    }

    @Override
    public float getMissileForwardsOffset() {
        return this.missileForwardsOffset;
    }

    @Override
    public float getMissileHorizontalOffset() {
        return this.missileHorizontalOffset;
    }

    @Override
    public float getMissileVerticalOffset() {
        return this.missileVerticalOffset;
    }

    @Override
    public float getBaseWeight() {
        return this.weight;
    }

    @Override
    public float getBaseTurretRotationAmount() {
        return this.turretRotationMax;
    }

    @Override
    public float getBaseMissileVelocityMax() {
        return this.baseMissileVelocityMax;
    }

    @Override
    public boolean isAmmoValidForInventory(IAmmo ammo) {
        return this.validAmmoTypes.contains(ammo);
    }

    @Override
    public boolean isUpgradeValid(IVehicleUpgradeType upgrade) {
        return this.validUpgrades.contains(upgrade);
    }

    @Override
    public float getBaseAccuracy() {
        return this.accuracy;
    }

    @Override
    public List<IAmmo> getValidAmmoTypes() {
        return this.validAmmoTypes;
    }

    @Override
    public List<IVehicleUpgradeType> getValidUpgrades() {
        return this.validUpgrades;
    }

    @Override
    public boolean isArmorValid(IVehicleArmor armor) {
        return this.validArmors.contains(armor);
    }

    @Override
    public List<IVehicleArmor> getValidArmors() {
        return this.validArmors;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public List<String> getDisplayTooltip() {
        return displayTooltip;
    }

    @Override
    public ItemStack getStackForLevel(int level) {
        return VehicleBridge.createStack(this, level, null);
    }

    @Override
    public int getStorageBaySize() {
        return this.storageBaySize;
    }

    @Override
    public int getAmmoBaySize() {
        return this.ammoBaySize;
    }

    @Override
    public int getArmorBaySize() {
        return this.armorBaySize;
    }

    @Override
    public int getUpgradeBaySize() {
        return this.upgradeBaySize;
    }

    @Override
    public float getMaxMissileWeight() {
        return this.maxMissileWeight;
    }

    @Override
    public boolean shouldRiderSit() {
        return this.riderSits;
    }

    // ---- registry methods ----

    @Nullable
    public static IVehicleType getVehicleType(int num) {
        if (num >= 0 && num < vehicleTypes.length) {
            return vehicleTypes[num];
        }
        return null;
    }

    /** A new vehicle of the given type and material level at full health (used by the spawner item and tooltips). */
    public static Optional<VehicleBase> getVehicleForType(@Nullable Level level, int type, int materialLevel) {
        if (level != null && type >= 0 && type < vehicleTypes.length && vehicleTypes[type] != null && vehicleTypes[type].isEnabled()) {
            IVehicleType vehType = getVehicleType(type);
            VehicleBase vehicle = VehicleBase.create(level, vehType);
            vehicle.setVehicleType(vehType, materialLevel);
            vehicle.setInitialHealth();
            return Optional.of(vehicle);
        }
        return Optional.empty();
    }

    @Nullable
    private static List<ItemStack> displayItemCache = null;

    /** One spawner stack per enabled type and material level, for the creative tab. Empty until the item slice installs its stack factory. */
    public static List<ItemStack> getCreativeDisplayItems() {
        if (displayItemCache != null) {
            return displayItemCache;
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (IVehicleType type : vehicleTypes) {
            if (type == null || type.getMaterialType() == null || !type.isEnabled()) {
                continue;
            }
            for (int i = 0; i < type.getMaterialType().getNumOfLevels(); i++) {
                ItemStack stack = type.getStackForLevel(i);
                if (!stack.isEmpty()) {
                    stacks.add(stack);
                }
            }
        }
        displayItemCache = stacks;
        return stacks;
    }

    @Override
    public boolean moveRiderWithTurret() {
        return this.riderMovesWithTurret;
    }

    @Override
    public float getTurretPosX() {
        return turretHorizontalOffset;
    }

    @Override
    public float getTurretPosY() {
        return turretVerticalOffset;
    }

    @Override
    public float getTurretPosZ() {
        return turretForwardsOffset;
    }

    @Override
    public String toString() {
        return "AWVehicleType: " + this.displayName;
    }

    @Override
    public void setBaseHealth(float val) {
        if (val < 0) {
            val = 0;
        }
        this.baseHealth = val;
    }

    @Override
    public void setBaseForwardSpeed(float val) {
        if (val < 0) {
            val = 0;
        }
        this.baseForwardSpeed = val;
    }

    @Override
    public void setBaseStrafeSpeed(float val) {
        if (val < 0) {
            val = 0;
        }
        this.baseStrafeSpeed = val;
    }

    @Override
    public void setBasePitchMin(float val) {
        if (val > 90) {
            val = 90;
        }
        if (val < 0) {
            val = 0;
        }
        this.basePitchMin = val;
    }

    @Override
    public void setBasePitchMax(float val) {
        if (val > 90) {
            val = 90;
        }
        if (val < 0) {
            val = 0;
        }
        this.basePitchMax = val;
    }

    @Override
    public void setBaseTurretRotationAmount(float val) {
        if (val > 180) {
            val = 180.f;
        }
        if (val < 0) {
            val = 0.f;
        }
        this.turretRotationMax = val;
    }

    @Override
    public void setBaseMissileVelocity(float val) {
        if (val < 0) {
            val = 0;
        }
        this.baseMissileVelocityMax = val;
    }

    @Override
    public void setBaseAccuracy(float val) {
        if (val < 0) {
            val = 0.f;
        }
        if (val > 1.f) {
            val = 1.f;
        }
        this.accuracy = val;
    }

    @Override
    public boolean isEnabledForLoot() {
        return this.enabledForLoot;
    }

    @Override
    public boolean isEnabledForCrafting() {
        return this.enabledForCrafting;
    }

    @Override
    public void setEnabledForLoot(boolean val) {
        this.enabledForLoot = val;
    }

    @Override
    public void playReloadSound(VehicleBase vehicleBase) {
    }

    @Override
    public void playFiringSound(VehicleBase vehicleBase) {
    }
}
