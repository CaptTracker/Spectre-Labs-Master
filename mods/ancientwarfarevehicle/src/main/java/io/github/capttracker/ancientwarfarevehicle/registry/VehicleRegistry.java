/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/registry/VehicleRegistry.
 */
package io.github.capttracker.ancientwarfarevehicle.registry;

import io.github.capttracker.ancientwarfarevehicle.config.AWVehicleStatics;
import io.github.capttracker.ancientwarfarevehicle.config.AWVehicleStatics.VehicleConfig;
import io.github.capttracker.ancientwarfarevehicle.entity.IVehicleType;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeBallistaMobile;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeBallistaMobileTurret;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeBallistaStand;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeBallistaStandTurret;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeBatteringRam;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeBoatBallista;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeBoatCatapult;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeBoatTransport;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeCannonMobileFixed;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeCannonStandFixed;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeCannonStandTurret;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeCatapultMobileFixed;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeCatapultMobileTurret;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeCatapultStandFixed;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeCatapultStandTurret;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeCart;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeCog;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeFishingBoat;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeMantlet;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeSiegeTowerLarge;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeSiegeTowerMedium;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeWarGalley;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeChestCart;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeHwacha;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeTrebuchetLarge;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeTrebuchetMobileFixed;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeTrebuchetStandFixed;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeTrebuchetStandTurret;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeWagon;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The 29 vehicle type descriptors (global indices 0 to 28, see {@link VehicleTypeIds}; 21 of the first generation, the carts, ships and siege works came later). They are plain data objects, not
 * items; the type constructors also enter themselves in {@code VehicleType.vehicleTypes}.
 *
 * <p>Creating them reads the common config (vehicle health, oversize ammo) and needs the ammo, armor and upgrade rule objects,
 * so the first access of this class must come after the common config has loaded. {@code AwVehicles} touches it at common
 * setup ({@link #registerVehicles()}); do not read these constants earlier (for example from a static initialiser that runs
 * during registration).
 */
public class VehicleRegistry {
    static {
        // the type constructors list their ammo, armor and upgrades (idempotent calls)
        AmmoRegistry.registerAmmo();
        ArmorRegistry.registerArmorTypes();
        UpgradeRegistry.registerUpgrades();
    }

    public static final IVehicleType CATAPULT_STAND_FIXED = new VehicleTypeCatapultStandFixed(VehicleTypeIds.CATAPULT_STAND_FIXED);
    public static final IVehicleType CATAPULT_STAND_TURRET = new VehicleTypeCatapultStandTurret(VehicleTypeIds.CATAPULT_STAND_TURRET);
    public static final IVehicleType CATAPULT_MOBILE_FIXED = new VehicleTypeCatapultMobileFixed(VehicleTypeIds.CATAPULT_MOBILE_FIXED);
    public static final IVehicleType CATAPULT_MOBILE_TURRET = new VehicleTypeCatapultMobileTurret(VehicleTypeIds.CATAPULT_MOBILE_TURRET);

    public static final IVehicleType BALLISTA_STAND_FIXED = new VehicleTypeBallistaStand(VehicleTypeIds.BALLISTA_STAND_FIXED);
    public static final IVehicleType BALLISTA_STAND_TURRET = new VehicleTypeBallistaStandTurret(VehicleTypeIds.BALLISTA_STAND_TURRET);
    public static final IVehicleType BALLISTA_MOBILE_FIXED = new VehicleTypeBallistaMobile(VehicleTypeIds.BALLISTA_MOBILE_FIXED);
    public static final IVehicleType BALLISTA_MOBILE_TURRET = new VehicleTypeBallistaMobileTurret(VehicleTypeIds.BALLISTA_MOBILE_TURRET);

    public static final IVehicleType BATTERING_RAM = new VehicleTypeBatteringRam(VehicleTypeIds.BATTERING_RAM);

    public static final IVehicleType CANNON_STAND_FIXED = new VehicleTypeCannonStandFixed(VehicleTypeIds.CANNON_STAND_FIXED);
    public static final IVehicleType CANNON_STAND_TURRET = new VehicleTypeCannonStandTurret(VehicleTypeIds.CANNON_STAND_TURRET);
    public static final IVehicleType CANNON_MOBILE_FIXED = new VehicleTypeCannonMobileFixed(VehicleTypeIds.CANNON_MOBILE_FIXED);

    public static final IVehicleType HWACHA = new VehicleTypeHwacha(VehicleTypeIds.HWACHA);

    public static final IVehicleType TREBUCHET_STAND_FIXED = new VehicleTypeTrebuchetStandFixed(VehicleTypeIds.TREBUCHET_STAND_FIXED);
    public static final IVehicleType TREBUCHET_STAND_TURRET = new VehicleTypeTrebuchetStandTurret(VehicleTypeIds.TREBUCHET_STAND_TURRET);
    public static final IVehicleType TREBUCHET_MOBILE_FIXED = new VehicleTypeTrebuchetMobileFixed(VehicleTypeIds.TREBUCHET_MOBILE_FIXED);
    public static final IVehicleType TREBUCHET_LARGE = new VehicleTypeTrebuchetLarge(VehicleTypeIds.TREBUCHET_LARGE);

    public static final IVehicleType CHEST_CART = new VehicleTypeChestCart(VehicleTypeIds.CHEST_CART);

    public static final IVehicleType BOAT_BALLISTA = new VehicleTypeBoatBallista(VehicleTypeIds.BOAT_BALLISTA);
    public static final IVehicleType BOAT_CATAPULT = new VehicleTypeBoatCatapult(VehicleTypeIds.BOAT_CATAPULT);
    public static final IVehicleType BOAT_TRANSPORT = new VehicleTypeBoatTransport(VehicleTypeIds.BOAT_TRANSPORT);

    public static final IVehicleType CART = new VehicleTypeCart(VehicleTypeIds.CART);
    public static final IVehicleType WAGON = new VehicleTypeWagon(VehicleTypeIds.WAGON);

    public static final IVehicleType FISHING_BOAT = new VehicleTypeFishingBoat(VehicleTypeIds.FISHING_BOAT);
    public static final IVehicleType COG = new VehicleTypeCog(VehicleTypeIds.COG);
    public static final IVehicleType WAR_GALLEY = new VehicleTypeWarGalley(VehicleTypeIds.WAR_GALLEY);

    public static final IVehicleType SIEGE_TOWER = new VehicleTypeSiegeTowerMedium(VehicleTypeIds.SIEGE_TOWER);
    public static final IVehicleType SIEGE_TOWER_LARGE = new VehicleTypeSiegeTowerLarge(VehicleTypeIds.SIEGE_TOWER_LARGE);
    public static final IVehicleType MANTLET = new VehicleTypeMantlet(VehicleTypeIds.MANTLET);

    /** All types, indexed by global type index. */
    private static final List<IVehicleType> BY_INDEX = List.of(
            CATAPULT_STAND_FIXED, CATAPULT_STAND_TURRET, CATAPULT_MOBILE_FIXED, CATAPULT_MOBILE_TURRET,
            BALLISTA_STAND_FIXED, BALLISTA_STAND_TURRET, BALLISTA_MOBILE_FIXED, BALLISTA_MOBILE_TURRET,
            BATTERING_RAM,
            CANNON_STAND_FIXED, CANNON_STAND_TURRET, CANNON_MOBILE_FIXED,
            HWACHA,
            TREBUCHET_STAND_FIXED, TREBUCHET_STAND_TURRET, TREBUCHET_MOBILE_FIXED, TREBUCHET_LARGE,
            CHEST_CART,
            BOAT_BALLISTA, BOAT_CATAPULT, BOAT_TRANSPORT,
            CART, WAGON,
            FISHING_BOAT, COG, WAR_GALLEY,
            SIEGE_TOWER, SIEGE_TOWER_LARGE, MANTLET);

    private VehicleRegistry() {}

    /** The type with this global index (the 1.12.2 spawner damage value), or null if there is none. */
    @Nullable
    public static IVehicleType getVehicleType(int globalTypeIndex) {
        return globalTypeIndex >= 0 && globalTypeIndex < BY_INDEX.size() ? BY_INDEX.get(globalTypeIndex) : null;
    }

    /** All 29 types in index order. */
    public static List<IVehicleType> getAllVehicleTypes() {
        return BY_INDEX;
    }

    /**
     * Applies the per-vehicle options of the common config ({@code e_vehicle_config}) to the types. Called once at common
     * setup, after the config has loaded; the base stats must not change afterwards.
     *
     * <p>1.12.2 had this code in a comment, so it never ran; a numeric option that is still at its "unset" value keeps the
     * stat built into the type. A disabled type stays in {@link #getAllVehicleTypes()}; {@code IVehicleType#isEnabled()} is false
     * (the original removed it from {@code VehicleType.vehicleTypes}, and {@code VehicleType.getVehicleForType} and the creative
     * list already test {@code isEnabled()}).
     */
    public static synchronized void registerVehicles() {
        for (IVehicleType vehicle : BY_INDEX) {
            VehicleConfig config = AWVehicleStatics.vehicleConfig(vehicle.getConfigName());
            vehicle.setEnabled(config.enabled());
            if (!vehicle.isEnabled()) {
                continue;
            }
            vehicle.setEnabledForLoot(config.addToChests());
            vehicle.setBaseAccuracy(VehicleConfig.or(config.accuracy(), vehicle.getBaseAccuracy()));
            vehicle.setBaseForwardSpeed(VehicleConfig.or(config.forwardSpeed(), vehicle.getBaseForwardSpeed()));
            vehicle.setBaseHealth(VehicleConfig.or(config.health(), vehicle.getBaseHealth()));
            vehicle.setBaseMissileVelocity(VehicleConfig.or(config.missileSpeed(), vehicle.getBaseMissileVelocityMax()));
            vehicle.setBasePitchMax(VehicleConfig.or(config.pitchMax(), vehicle.getBasePitchMax()));
            vehicle.setBasePitchMin(VehicleConfig.or(config.pitchMin(), vehicle.getBasePitchMin()));
            vehicle.setBaseStrafeSpeed(VehicleConfig.or(config.strafeSpeed(), vehicle.getBaseStrafeSpeed()));
            vehicle.setBaseTurretRotationAmount(VehicleConfig.or(config.turretRotation(), vehicle.getBaseTurretRotationAmount()));

            // remove ammo that is switched off (nothing switches ammo off in 1.12.2; kept as it was)
            vehicle.getValidAmmoTypes().removeIf(ammo -> !ammo.isEnabled());
        }
    }
}
