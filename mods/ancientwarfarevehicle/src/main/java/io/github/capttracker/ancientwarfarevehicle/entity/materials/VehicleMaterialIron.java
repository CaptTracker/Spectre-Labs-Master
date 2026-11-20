/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/materials/VehicleMaterialIron.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.materials;

/** The iron table: five levels, numerically identical to the wood table (only the display names differ). */
public class VehicleMaterialIron extends VehicleMaterial {
    public VehicleMaterialIron() {
        super(5);
        // Local on purpose: VehicleMaterial creates this class in its own static initialiser, so this class
        // must not rely on static state of its own.
        float[] accuracy = {0.85f, 0.9f, 0.93f, 0.96f, 1.f};
        float[] hp = {0.75f, 1.f, 1.2f, 1.4f, 2.f};
        float[] speed = {0.85f, 1.f, 1.15f, 1.05f, 0.95f};
        float[] weight = {1.25f, 1.f, 1.1f, 1.3f, 1.7f};
        String[] names = {"Rough Iron", "Fine Iron", "Tempered Iron", "Minor Alloy", "Major Alloy"};
        for (int i = 0; i < 5; i++) {
            MaterialLevel level = new MaterialLevel();
            level.accuracyFactor = accuracy[i];
            level.hpFactor = hp[i];
            level.speedForwardFactor = speed[i];
            level.speedStrafeFactor = speed[i];
            level.weightFactor = weight[i];
            level.displayName = names[i];
            this.materialLevels[i] = level;
        }
    }
}
