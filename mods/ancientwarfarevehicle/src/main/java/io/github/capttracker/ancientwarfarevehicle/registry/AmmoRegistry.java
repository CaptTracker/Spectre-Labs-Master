/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/registry/AmmoRegistry.
 */
package io.github.capttracker.ancientwarfarevehicle.registry;

import io.github.capttracker.ancientwarfarevehicle.config.AWVehicleStatics;
import io.github.capttracker.ancientwarfarevehicle.init.AwVehicles;
import io.github.capttracker.ancientwarfarevehicle.item.ItemAmmo;
import io.github.capttracker.ancientwarfarevehicle.missiles.AmmoArrow;
import io.github.capttracker.ancientwarfarevehicle.missiles.AmmoBallShot;
import io.github.capttracker.ancientwarfarevehicle.missiles.AmmoBallistaBolt;
import io.github.capttracker.ancientwarfarevehicle.missiles.AmmoBallistaBoltExplosive;
import io.github.capttracker.ancientwarfarevehicle.missiles.AmmoBallistaBoltFlame;
import io.github.capttracker.ancientwarfarevehicle.missiles.AmmoBallistaBoltIron;
import io.github.capttracker.ancientwarfarevehicle.missiles.AmmoCanisterShot;
import io.github.capttracker.ancientwarfarevehicle.missiles.AmmoClusterShot;
import io.github.capttracker.ancientwarfarevehicle.missiles.AmmoExplosiveShot;
import io.github.capttracker.ancientwarfarevehicle.missiles.AmmoFlameShot;
import io.github.capttracker.ancientwarfarevehicle.missiles.AmmoGrapeShot;
import io.github.capttracker.ancientwarfarevehicle.missiles.AmmoHwachaRocket;
import io.github.capttracker.ancientwarfarevehicle.missiles.AmmoHwachaRocketAirburst;
import io.github.capttracker.ancientwarfarevehicle.missiles.AmmoHwachaRocketExplosive;
import io.github.capttracker.ancientwarfarevehicle.missiles.AmmoHwachaRocketFlame;
import io.github.capttracker.ancientwarfarevehicle.missiles.AmmoIronBallShot;
import io.github.capttracker.ancientwarfarevehicle.missiles.AmmoIronShot;
import io.github.capttracker.ancientwarfarevehicle.missiles.AmmoNapalmShot;
import io.github.capttracker.ancientwarfarevehicle.missiles.AmmoPebbleShot;
import io.github.capttracker.ancientwarfarevehicle.missiles.AmmoStoneShot;
import io.github.capttracker.ancientwarfarevehicle.missiles.IAmmo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The 51 ammunition rule objects by registry name. The 51 {@link ItemAmmo} items are registered by {@code AwVehicles}
 * under the same names (a mismatch is reported by {@link #registerAmmo()}); this class only creates the rule objects.
 *
 * <p>Procedure to make a new ammo type: create the ammo class, create the static instance below, register it here,
 * add its item name to {@code AwVehicles.AMMO_NAMES}, register the render, and add the ammo to the applicable vehicle
 * type constructors.
 *
 * <p>{@link #registerAmmo()} reads the cannon ball damages from the config, so it runs at common setup (when the common
 * config has loaded), not while the items are registered as in 1.12.2. It is idempotent, so any class may call it first.
 */
public class AmmoRegistry {
    public static IAmmo ammoBallShot;
    public static IAmmo ammoBallIronShot;
    public static IAmmo ammoStoneShot10;
    public static IAmmo ammoStoneShot15;
    public static IAmmo ammoStoneShot30;
    public static IAmmo ammoStoneShot45;
    public static IAmmo ammoFireShot10;
    public static IAmmo ammoFireShot15;
    public static IAmmo ammoFireShot30;
    public static IAmmo ammoFireShot45;
    public static IAmmo ammoExplosive10;
    public static IAmmo ammoExplosive15;
    public static IAmmo ammoExplosive30;
    public static IAmmo ammoExplosive45;
    public static IAmmo ammoHE10;
    public static IAmmo ammoHE15;
    public static IAmmo ammoHE30;
    public static IAmmo ammoHE45;
    public static IAmmo ammoNapalm10;
    public static IAmmo ammoNapalm15;
    public static IAmmo ammoNapalm30;
    public static IAmmo ammoNapalm45;
    public static IAmmo ammoClusterShot10;
    public static IAmmo ammoClusterShot15;
    public static IAmmo ammoClusterShot30;
    public static IAmmo ammoClusterShot45;
    public static IAmmo ammoPebbleShot10;
    public static IAmmo ammoPebbleShot15;
    public static IAmmo ammoPebbleShot30;
    public static IAmmo ammoPebbleShot45;
    public static IAmmo ammoIronShot5;
    public static IAmmo ammoIronShot10;
    public static IAmmo ammoIronShot15;
    public static IAmmo ammoIronShot25;
    public static IAmmo ammoCanisterShot5;
    public static IAmmo ammoCanisterShot10;
    public static IAmmo ammoCanisterShot15;
    public static IAmmo ammoCanisterShot25;
    public static IAmmo ammoGrapeShot5;
    public static IAmmo ammoGrapeShot10;
    public static IAmmo ammoGrapeShot15;
    public static IAmmo ammoGrapeShot25;
    public static IAmmo ammoArrow;
    public static IAmmo ammoBallistaBolt;
    public static IAmmo ammoBallistaBoltFlame;
    public static IAmmo ammoBallistaBoltExplosive;
    public static IAmmo ammoBallistaBoltIron;
    public static IAmmo ammoRocket;
    public static IAmmo ammoHwachaRocketFlame;
    public static IAmmo ammoHwachaRocketExplosive;
    public static IAmmo ammoHwachaRocketAirburst;

    private AmmoRegistry() {}

    private static final Map<ResourceLocation, IAmmo> ammoInstances = new HashMap<>();
    private static boolean registered = false;

    public static synchronized void registerAmmo() {
        if (registered) {
            return;
        }
        registered = true;

        ammoBallShot = registerAmmoType(new AmmoBallShot());
        ammoBallIronShot = registerAmmoType(new AmmoIronBallShot());
        ammoStoneShot10 = registerAmmoType(new AmmoStoneShot(10));
        ammoStoneShot15 = registerAmmoType(new AmmoStoneShot(15));
        ammoStoneShot30 = registerAmmoType(new AmmoStoneShot(30));
        ammoStoneShot45 = registerAmmoType(new AmmoStoneShot(45));
        ammoFireShot10 = registerAmmoType(new AmmoFlameShot(10));
        ammoFireShot15 = registerAmmoType(new AmmoFlameShot(15));
        ammoFireShot30 = registerAmmoType(new AmmoFlameShot(30));
        ammoFireShot45 = registerAmmoType(new AmmoFlameShot(45));
        ammoExplosive10 = registerAmmoType(new AmmoExplosiveShot(10, false));
        ammoExplosive15 = registerAmmoType(new AmmoExplosiveShot(15, false));
        ammoExplosive30 = registerAmmoType(new AmmoExplosiveShot(30, false));
        ammoExplosive45 = registerAmmoType(new AmmoExplosiveShot(45, false));
        ammoHE10 = registerAmmoType(new AmmoExplosiveShot(10, true));
        ammoHE15 = registerAmmoType(new AmmoExplosiveShot(15, true));
        ammoHE30 = registerAmmoType(new AmmoExplosiveShot(30, true));
        ammoHE45 = registerAmmoType(new AmmoExplosiveShot(45, true));
        ammoNapalm10 = registerAmmoType(new AmmoNapalmShot(10));
        ammoNapalm15 = registerAmmoType(new AmmoNapalmShot(15));
        ammoNapalm30 = registerAmmoType(new AmmoNapalmShot(30));
        ammoNapalm45 = registerAmmoType(new AmmoNapalmShot(45));
        ammoClusterShot10 = registerAmmoType(new AmmoClusterShot(10));
        ammoClusterShot15 = registerAmmoType(new AmmoClusterShot(15));
        ammoClusterShot30 = registerAmmoType(new AmmoClusterShot(30));
        ammoClusterShot45 = registerAmmoType(new AmmoClusterShot(45));
        ammoPebbleShot10 = registerAmmoType(new AmmoPebbleShot(10));
        ammoPebbleShot15 = registerAmmoType(new AmmoPebbleShot(15));
        ammoPebbleShot30 = registerAmmoType(new AmmoPebbleShot(30));
        ammoPebbleShot45 = registerAmmoType(new AmmoPebbleShot(45));
        ammoIronShot5 = registerAmmoType(new AmmoIronShot(5, AWVehicleStatics.vehicleStats.ammoCannonBall5kgDamage));
        ammoIronShot10 = registerAmmoType(new AmmoIronShot(10, AWVehicleStatics.vehicleStats.ammoCannonBall10kgDamage));
        ammoIronShot15 = registerAmmoType(new AmmoIronShot(15, AWVehicleStatics.vehicleStats.ammoCannonBall15kgDamage));
        ammoIronShot25 = registerAmmoType(new AmmoIronShot(25, AWVehicleStatics.vehicleStats.ammoCannonBall25kgDamage));
        ammoCanisterShot5 = registerAmmoType(new AmmoCanisterShot(5));
        ammoCanisterShot10 = registerAmmoType(new AmmoCanisterShot(10));
        ammoCanisterShot15 = registerAmmoType(new AmmoCanisterShot(15));
        ammoCanisterShot25 = registerAmmoType(new AmmoCanisterShot(25));
        ammoGrapeShot5 = registerAmmoType(new AmmoGrapeShot(5));
        ammoGrapeShot10 = registerAmmoType(new AmmoGrapeShot(10));
        ammoGrapeShot15 = registerAmmoType(new AmmoGrapeShot(15));
        ammoGrapeShot25 = registerAmmoType(new AmmoGrapeShot(25));
        ammoArrow = registerAmmoType(new AmmoArrow());
        ammoBallistaBolt = registerAmmoType(new AmmoBallistaBolt());
        ammoBallistaBoltFlame = registerAmmoType(new AmmoBallistaBoltFlame());
        ammoBallistaBoltExplosive = registerAmmoType(new AmmoBallistaBoltExplosive());
        ammoBallistaBoltIron = registerAmmoType(new AmmoBallistaBoltIron());
        ammoRocket = registerAmmoType(new AmmoHwachaRocket());
        ammoHwachaRocketFlame = registerAmmoType(new AmmoHwachaRocketFlame());
        ammoHwachaRocketExplosive = registerAmmoType(new AmmoHwachaRocketExplosive());
        ammoHwachaRocketAirburst = registerAmmoType(new AmmoHwachaRocketAirburst());
    }

    private static IAmmo registerAmmoType(IAmmo ammo) {
        ResourceLocation name = ammo.getRegistryName();
        if (!AwVehicles.AMMO_ITEMS.containsKey(name.getPath())) {
            // the item list of AwVehicles and the ammo classes disagree on a registry name
            throw new IllegalStateException("Ammo " + name + " has no item registered by AwVehicles");
        }
        ammoInstances.put(name, ammo);
        return ammo;
    }

    public static Optional<IAmmo> getAmmoForStack(ItemStack stack) {
        return Optional.ofNullable(ammoInstances.get(BuiltInRegistries.ITEM.getKey(stack.getItem())));
    }

    @Nullable
    public static IAmmo getAmmo(ResourceLocation registryName) {
        return ammoInstances.get(registryName);
    }

    @Nullable
    public static ItemAmmo getItemForAmmo(IAmmo ammo) {
        return getItem(ammo.getRegistryName());
    }

    /** The item of the ammo with this registry name, or null if there is none. */
    @Nullable
    public static ItemAmmo getItem(ResourceLocation ammoRegistryName) {
        if (!ammoRegistryName.getNamespace().equals(AwVehicles.MOD_ID)) {
            return null;
        }
        var item = AwVehicles.AMMO_ITEMS.get(ammoRegistryName.getPath());
        return item == null ? null : item.get();
    }
}
