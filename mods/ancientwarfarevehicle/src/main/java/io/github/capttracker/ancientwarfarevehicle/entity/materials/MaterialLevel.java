/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/materials/MaterialLevel.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.materials;

/**
 * One row of a material table. An all-zero instance is what {@link VehicleMaterial} returns for an out of range
 * level (which would give a vehicle zero health, so callers must keep the level in range).
 */
public class MaterialLevel {
    public float hpFactor;
    public float speedForwardFactor;
    public float speedStrafeFactor;
    public float weightFactor;
    public float accuracyFactor;
    /** Not a translation key: the original shows these English names as they are. */
    String displayName = "";
}
