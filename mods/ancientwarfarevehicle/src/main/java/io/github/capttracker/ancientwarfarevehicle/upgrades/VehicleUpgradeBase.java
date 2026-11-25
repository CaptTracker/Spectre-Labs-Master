/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/upgrades/VehicleUpgradeBase.
 */
package io.github.capttracker.ancientwarfarevehicle.upgrades;

import io.github.capttracker.ancientwarfarevehicle.AncientWarfareVehicles;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.resources.ResourceLocation;

public abstract class VehicleUpgradeBase implements IVehicleUpgradeType {
    private final ResourceLocation registryName;

    protected VehicleUpgradeBase(String name) {
        registryName = Ids.of(AncientWarfareVehicles.MOD_ID, name);
    }

    @Override
    public ResourceLocation getRegistryName() {
        return registryName;
    }
}
