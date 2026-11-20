/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/materials/IVehicleMaterial.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.materials;

/**
 * The stat factors of a vehicle's material, by quality level. Applied to the type's base stats when the
 * vehicle is set up, before upgrades.
 *
 * <p>The misfire chance of the original ({@code getMisfireChance}) was never called anywhere and is not ported.
 */
public interface IVehicleMaterial {
    int getNumOfLevels();

    float getHPFactor(int level);

    float getSpeedForwardFactor(int level);

    float getSpeedStrafeFactor(int level);

    float getWeightFactor(int level);

    float getAccuracyFactor(int level);

    String getDisplayName(int level);
}
