/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: the options of AWVehicleStatics (generalSettings, clientSettings.adjustMissilesForAccuracy
 * and the vehicle health values) that the vehicle entity code reads.
 */
package io.github.capttracker.ancientwarfarevehicle.entity;

/**
 * The configuration values the vehicle entity slice reads, with the 1.12.2 defaults. The config slice copies
 * its values into these fields whenever the config loads or reloads (see the port notes), so this slice does
 * not depend on it. The health values and {@link #oversizeAmmoEnabled} are read when a vehicle type is constructed.
 */
public final class VehicleSettings {
    private VehicleSettings() {}

    // generalSettings
    /** Whether the 30 kg and 45 kg ammo can be fired from the regular vehicles. */
    public static boolean oversizeAmmoEnabled = true;
    /** Whether player owned soldiers need ammo for firing vehicles. */
    public static boolean ownedSoldiersUseAmmo = true;
    public static boolean vehiclesTearUpGrass = true;
    public static boolean batteringRamBreaksBlocks = true;
    /** 1 to 100. */
    public static int batteringRamBlockBreakPercentageChance = 20;
    /** Was {@code clientSettings.adjustMissilesForAccuracy}, but the server reads it when it spawns missiles. */
    public static boolean adjustMissilesForAccuracy = true;

    // naval combat (rework phase 11), the section naval_combat of the common config
    public static boolean shipRamEnabled = true;
    public static double shipRamDamageScale = 1.0D;
    public static double shipRamRecoilFraction = 0.25D;
    public static int shipRamCooldownTicks = 30;
    public static double shipRamMinSpeed = 0.04D;
    public static double shipRamDeckKnock = 0.4D;
    public static boolean grappleEnabled = true;
    public static int grappleRange = 24;
    public static double grapplePullSpeed = 0.08D;
    public static int grappleMaxLines = 2;
    public static int grappleTimeoutTicks = 6000;
    public static int grappleSnapDistance = 40;
    public static double sinkingCargoFraction = 0.5D;
    public static int sinkingCrateTicks = 6000;
    public static int sinkingCrateMax = 8;

    // vehicleStats
    public static int vehicleBallistaHealth = 100;
    public static int vehicleBallistaBoatHealth = 100;
    public static int vehicleBatteringRamHealth = 100;
    public static int vehicleCatapultHealth = 100;
    public static int vehicleCatapultBoatHealth = 100;
    public static int vehicleCannonHealth = 100;
    public static int vehicleChestCartHealth = 100;
    public static int vehicleChestBoatHealth = 100;
    public static int vehicleHwachaHealth = 100;
    public static int vehicleTrebuchetHealth = 100;
    public static int vehicleGiantTrebuchetHealth = 175;
}
