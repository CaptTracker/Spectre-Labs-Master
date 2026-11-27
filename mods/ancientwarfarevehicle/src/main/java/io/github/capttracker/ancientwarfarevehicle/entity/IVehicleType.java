/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/IVehicleType.
 */
package io.github.capttracker.ancientwarfarevehicle.entity;

import io.github.capttracker.ancientwarfarevehicle.armors.IVehicleArmor;
import io.github.capttracker.ancientwarfarevehicle.entity.materials.IVehicleMaterial;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleFiringVarsHelper;
import io.github.capttracker.ancientwarfarevehicle.missiles.IAmmo;
import io.github.capttracker.ancientwarfarevehicle.upgrades.IVehicleUpgradeType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The descriptor of one vehicle type: size, speeds, aim ranges, offsets, bays and the valid ammo, upgrades and armor.
 * Changes from 1.12.2: {@code getIconTexture} and the client-only {@code getLocalizedName} are gone (the name is a
 * translation key, see {@link #getDisplayComponent()}).
 */
public interface IVehicleType {
    float getWidth();

    float getHeight();

    float getBaseWeight();

    /* adjustable stats, set at time of vehicle registration. DO NOT CHANGE DURING RUN-TIME: changes will not propagate properly */

    /** Base max health, before any material adjustments. */
    float getBaseHealth();

    void setBaseHealth(float val);

    float getBaseForwardSpeed();

    void setBaseForwardSpeed(float val);

    float getBaseStrafeSpeed();

    void setBaseStrafeSpeed(float val);

    float getBasePitchMin();

    void setBasePitchMin(float val);

    float getBasePitchMax();

    void setBasePitchMax(float val);

    /** Max rotation from a center point. 180 or more means the turret can spin around completely. */
    float getBaseTurretRotationAmount();

    void setBaseTurretRotationAmount(float val);

    /** Base missile velocity, before materials or upgrades. */
    float getBaseMissileVelocityMax();

    void setBaseMissileVelocity(float val);

    float getBaseAccuracy();

    void setBaseAccuracy(float val);

    /** The texture for the given material quality level. */
    ResourceLocation getTextureForMaterialLevel(int level);

    /** The translation key of the type's name. */
    String getDisplayName();

    default Component getDisplayComponent() {
        return Component.translatable(getDisplayName());
    }

    /** Translation keys of the tooltip lines. */
    List<String> getDisplayTooltip();

    /** The numeric type id, the index in {@code VehicleType.vehicleTypes}. */
    int getGlobalVehicleType();

    /** Wood or iron: the material applies adjustments to the base stats, before upgrades. */
    IVehicleMaterial getMaterialType();

    /** Should allow mounting. */
    boolean isMountable();

    /** Should check movement input (turning counts, so the stands are drivable too). */
    boolean isDrivable();

    /** Should check non-movement input. */
    boolean isCombatEngine();

    /** Can NPCs pilot this vehicle. */
    boolean canSoldiersPilot();

    /** Can the aim yaw be adjusted independently of the vehicle yaw. */
    boolean canAdjustYaw();

    /** Can the aim pitch be adjusted (should be either pitch or power). */
    boolean canAdjustPitch();

    /** Can the shot velocity be adjusted (should be either pitch or power). */
    boolean canAdjustPower();

    /** The offset in the turret yaw direction from the turret position. */
    float getMissileForwardsOffset();

    /** The offset in the turret yaw + 90 direction from the turret position. */
    float getMissileHorizontalOffset();

    /** The offset in the y+ direction from the turret position. */
    float getMissileVerticalOffset();

    /** The offset of the turret from 0,0 at vehicle rotation 0. */
    float getTurretPosX();

    float getTurretPosY();

    float getTurretPosZ();

    /** The offset from 0,0 or the turret position of the rider. */
    float getRiderForwardsOffset();

    float getRiderHorizontalOffset();

    /** Spelled {@code getRiderVerticalOffest} in the original. */
    float getRiderVerticalOffset();

    /**
     * The seats of the vehicle (rework phase 4). The first one is always the DRIVER seat and has the offsets of the old single rider; the
     * others are extra GUNNER, PASSENGER and ROWER seats.
     */
    List<VehicleSeat> getSeats();

    /** How far behind the vehicle the chase camera sits at the default zoom, in blocks. */
    float getCameraDistance();

    /** Used by soldiers to determine when to get off a vehicle. */
    float getMinAttackDistance();

    /** Should the rider be seated while riding. */
    boolean shouldRiderSit();

    /** Should the position of the rider follow the turret rather than the vehicle. */
    boolean moveRiderWithTurret();

    /** Does not determine whether it can be fired, only whether it can be placed into the inventory. */
    boolean isAmmoValidForInventory(IAmmo ammo);

    boolean isUpgradeValid(IVehicleUpgradeType upgrade);

    boolean isArmorValid(IVehicleArmor armor);

    List<IAmmo> getValidAmmoTypes();

    List<IVehicleUpgradeType> getValidUpgrades();

    List<IVehicleArmor> getValidArmors();

    int getStorageBaySize();

    int getAmmoBaySize();

    int getArmorBaySize();

    int getUpgradeBaySize();

    /** In kg, adjusted by material. Any additional missile weight over this reduces the max launch speed by a ratio. */
    float getMaxMissileWeight();

    /** The spawner item stack for this type at the given material level (empty until the item slice installs {@link VehicleBridge}). */
    ItemStack getStackForLevel(int level);

    /** What ammo soldiers use if the owned soldiers do not use ammo. */
    IAmmo getAmmoForSoldierRank(int rank);

    VehicleFiringVarsHelper getFiringVarsHelper(VehicleBase vehicle);

    VehicleMovementType getMovementType();

    void setEnabled(boolean val);

    /** Determined via config, used to add recipes and to loot tables. */
    boolean isEnabled();

    void playReloadSound(VehicleBase vehicle);

    void playFiringSound(VehicleBase vehicle);

    boolean isEnabledForLoot();

    boolean isEnabledForCrafting();

    void setEnabledForLoot(boolean val);

    /** The name used to load config settings for this vehicle. */
    String getConfigName();

    // ---- horse-drawn carts (rework phase 4) ----

    /** True for the carts and wagons that a horse, donkey or mule pulls (see {@code CartHitch}). */
    default boolean isHorseDrawn() {
        return false;
    }

    /** How many horses may be hitched at once (0 for a vehicle that is not horse-drawn). */
    default int getMaxHorses() {
        return 0;
    }

    /** Distance from the centre of the vehicle to the point where the rear of the horse is hitched, in blocks. */
    default float getHitchOffset() {
        return 0.f;
    }

    /** The most the vehicle may bend away from the heading of the horse, in degrees. */
    default float getMaxHitchAngle() {
        return 70.f;
    }

    /** True if a player's attack breaks the vehicle into its item the way it breaks a boat (a few hits, at once in creative). */
    default boolean breaksOnAttack() {
        return false;
    }

    // ---- ships (rework phase 4) ----

    /** The design of a big ship (hull of part entities, deck, sails, hardpoints), or null for every other vehicle. */
    @javax.annotation.Nullable
    default io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipSpec getShipSpec() {
        return null;
    }

    /** True for the ships: they are spawned as the ship entity type and move with {@code ShipHelper}. */
    default boolean isShip() {
        return getShipSpec() != null;
    }

    // ---- siege works (rework phase 12) ----

    /** True for the siege towers and the mantlet: ground vehicles that riders push from behind (see {@link SeatRole#PUSHER}); with nobody pushing they do not move. */
    default boolean isPushed() {
        return false;
    }

    /** How many pushers (the driver counts as one) move a pushed vehicle at its full speed; every pusher adds an equal share of it. */
    default int getPushersForFullSpeed() {
        return 1;
    }

    /** The height of the top platform of a siege tower over the ground, in blocks; 0 for every other vehicle. */
    default float getSiegeTopHeight() {
        return 0.f;
    }

    /** True for the siege towers: they are spawned as the siege tower entity type (it carries the part entities of the drop bridge) and have a ladder and a bridge. */
    default boolean isSiegeTower() {
        return getSiegeTopHeight() > 0.f;
    }

    /** True if projectiles that fly into the front of the vehicle are stopped by it (the mantlet); they are tested against its shield, not against its box. */
    default boolean blocksProjectiles() {
        return false;
    }

    /** The highest step the vehicle climbs in blocks (vehicles climb 1.12; a siege tower climbs none). */
    default float getMaxUpStep() {
        return 1.12f;
    }

    /** The fraction of its walking speed a horse hitched to the vehicle is held to (a siege tower is slow). */
    default float getTowSpeedScale() {
        return 1.f;
    }

    /** How far the box of the vehicle counts as bigger when the player aims at it (the mantlet is wider than its box). */
    default float getPickRadius() {
        return 0.f;
    }

    /** What the vehicle makes of damage before armor: the siege tower takes little from arrows and a lot from fire. */
    default float modifyIncomingDamage(VehicleBase vehicle, DamageSource source, float amount) {
        return amount;
    }
}
