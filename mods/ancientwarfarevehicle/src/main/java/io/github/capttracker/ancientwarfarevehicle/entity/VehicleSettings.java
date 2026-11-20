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
