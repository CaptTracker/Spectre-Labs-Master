/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/registry/UpgradeRegistry.
 */
package io.github.capttracker.ancientwarfarevehicle.registry;

import io.github.capttracker.ancientwarfarevehicle.init.AwVehicles;
import io.github.capttracker.ancientwarfarevehicle.upgrades.IVehicleUpgradeType;
import io.github.capttracker.ancientwarfarevehicle.upgrades.VehicleUpgradeAim;
import io.github.capttracker.ancientwarfarevehicle.upgrades.VehicleUpgradePitchDown;
import io.github.capttracker.ancientwarfarevehicle.upgrades.VehicleUpgradePitchUp;
import io.github.capttracker.ancientwarfarevehicle.upgrades.VehicleUpgradePower;
import io.github.capttracker.ancientwarfarevehicle.upgrades.VehicleUpgradeReload;
import io.github.capttracker.ancientwarfarevehicle.upgrades.VehicleUpgradeSpeed;
import io.github.capttracker.ancientwarfarevehicle.upgrades.VehicleUpgradeTurretPitch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry name to upgrade rule object. The seven items are registered by {@code AwVehicles};
 * {@link #registerUpgrades()} (idempotent) only creates the rule objects, at common setup.
 */
public class UpgradeRegistry {
    public static IVehicleUpgradeType speedUpgrade;
    public static IVehicleUpgradeType aimUpgrade;
    public static IVehicleUpgradeType reloadUpgrade;

    public static IVehicleUpgradeType powerUpgrade;
    public static IVehicleUpgradeType pitchExtUpgrade;
    public static IVehicleUpgradeType pitchUpUpgrade;
    public static IVehicleUpgradeType pitchDownUpgrade;

    private static final Map<ResourceLocation, IVehicleUpgradeType> upgradeInstances = new LinkedHashMap<>();
    private static boolean registered = false;

    private static final UpgradeRegistry INSTANCE = new UpgradeRegistry();

    private UpgradeRegistry() {}

    public static UpgradeRegistry instance() {
        return INSTANCE;
    }

    public Collection<IVehicleUpgradeType> getUpgradeList() {
        return upgradeInstances.values();
    }

    public static synchronized void registerUpgrades() {
        if (registered) {
            return;
        }
        registered = true;
        speedUpgrade = registerUpgrade(new VehicleUpgradeSpeed());
        aimUpgrade = registerUpgrade(new VehicleUpgradeAim());
        reloadUpgrade = registerUpgrade(new VehicleUpgradeReload());
        powerUpgrade = registerUpgrade(new VehicleUpgradePower());
        pitchExtUpgrade = registerUpgrade(new VehicleUpgradeTurretPitch());
        pitchUpUpgrade = registerUpgrade(new VehicleUpgradePitchUp());
        pitchDownUpgrade = registerUpgrade(new VehicleUpgradePitchDown());
    }

    private static IVehicleUpgradeType registerUpgrade(IVehicleUpgradeType upgrade) {
        if (!AwVehicles.UPGRADE_ITEMS.containsKey(upgrade.getRegistryName().getPath())) {
            throw new IllegalStateException("Upgrade " + upgrade.getRegistryName() + " has no item registered by AwVehicles");
        }
        upgradeInstances.put(upgrade.getRegistryName(), upgrade);
        return upgrade;
    }

    public static Optional<IVehicleUpgradeType> getUpgrade(ResourceLocation type) {
        return Optional.ofNullable(upgradeInstances.get(type));
    }

    public static Optional<IVehicleUpgradeType> getUpgrade(ItemStack stack) {
        return Optional.ofNullable(upgradeInstances.get(BuiltInRegistries.ITEM.getKey(stack.getItem())));
    }
}
