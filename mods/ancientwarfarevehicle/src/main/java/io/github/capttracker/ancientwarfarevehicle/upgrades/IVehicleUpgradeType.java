/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/upgrades/IVehicleUpgradeType.
 */
package io.github.capttracker.ancientwarfarevehicle.upgrades;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import net.minecraft.resources.ResourceLocation;

/**
 * The effect of one vehicle upgrade item. The registry name is also the registry name of the item
 * ({@code ancientwarfarevehicle:vehicle_upgrade_<name>}); see {@code UpgradeRegistry}.
 */
public interface IVehicleUpgradeType {
    ResourceLocation getRegistryName();

    /** Applied to the "current" stats of the vehicle, which are reset to the base values before every pass. */
    void applyVehicleEffects(VehicleBase vehicle);
}
