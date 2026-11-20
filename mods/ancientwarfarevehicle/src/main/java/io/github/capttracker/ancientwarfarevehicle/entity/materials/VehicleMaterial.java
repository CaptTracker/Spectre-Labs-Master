/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/materials/VehicleMaterial.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.materials;

import io.github.capttracker.ancientwarfarevehicle.AncientWarfareVehicles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VehicleMaterial implements IVehicleMaterial {
    private static final Logger LOGGER = LoggerFactory.getLogger(AncientWarfareVehicles.MOD_ID);

    public static final VehicleMaterial materialWood = new VehicleMaterialWood();
    public static final VehicleMaterial materialIron = new VehicleMaterialIron();

    public final MaterialLevel[] materialLevels;

    public VehicleMaterial(int levelCount) {
        this.materialLevels = new MaterialLevel[levelCount];
    }

    private MaterialLevel getLevel(int num) {
        if (num < 0 || num >= this.materialLevels.length) {
            LOGGER.error("out of range vehicle material requested. num: {} current levels: {}", num, this.materialLevels.length);
            return new MaterialLevel();
        }
        return this.materialLevels[num];
    }

    @Override
    public int getNumOfLevels() {
        return this.materialLevels.length;
    }

    @Override
    public float getHPFactor(int level) {
        return getLevel(level).hpFactor;
    }

    @Override
    public float getSpeedForwardFactor(int level) {
        return getLevel(level).speedForwardFactor;
    }

    @Override
    public float getSpeedStrafeFactor(int level) {
        return getLevel(level).speedStrafeFactor;
    }

    @Override
    public float getWeightFactor(int level) {
        return getLevel(level).weightFactor;
    }

    @Override
    public float getAccuracyFactor(int level) {
        return getLevel(level).accuracyFactor;
    }

    @Override
    public String getDisplayName(int level) {
        return getLevel(level).displayName;
    }
}
