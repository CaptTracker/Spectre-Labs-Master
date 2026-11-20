/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/registry/ArmorRegistry.
 */
package io.github.capttracker.ancientwarfarevehicle.registry;

import io.github.capttracker.ancientwarfarevehicle.armors.IVehicleArmor;
import io.github.capttracker.ancientwarfarevehicle.armors.VehicleArmorIron;
import io.github.capttracker.ancientwarfarevehicle.armors.VehicleArmorObsidian;
import io.github.capttracker.ancientwarfarevehicle.armors.VehicleArmorStone;
import io.github.capttracker.ancientwarfarevehicle.init.AwVehicles;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry name to armor rule object. The three items ({@code armor_stone}, {@code armor_iron}, {@code armor_obsidian}) are
 * registered by {@code AwVehicles}; {@link #registerArmorTypes()} (idempotent) only creates the rule objects, at common setup.
 */
public class ArmorRegistry {
    private ArmorRegistry() {}

    public static IVehicleArmor armorStone;
    public static IVehicleArmor armorIron;
    public static IVehicleArmor armorObsidian;

    private static final Map<ResourceLocation, IVehicleArmor> armorInstances = new HashMap<>();
    private static boolean registered = false;

    public static synchronized void registerArmorTypes() {
        if (registered) {
            return;
        }
        registered = true;
        armorStone = registerArmorType(new VehicleArmorStone());
        armorIron = registerArmorType(new VehicleArmorIron());
        armorObsidian = registerArmorType(new VehicleArmorObsidian());
    }

    private static IVehicleArmor registerArmorType(IVehicleArmor armor) {
        if (!AwVehicles.ARMOR_ITEMS.containsKey(armor.getRegistryName().getPath())) {
            throw new IllegalStateException("Armor " + armor.getRegistryName() + " has no item registered by AwVehicles");
        }
        armorInstances.put(armor.getRegistryName(), armor);
        return armor;
    }

    public static Optional<IVehicleArmor> getArmorType(ResourceLocation registryName) {
        return Optional.ofNullable(armorInstances.get(registryName));
    }

    public static Optional<IVehicleArmor> getArmorForStack(ItemStack stack) {
        return Optional.ofNullable(armorInstances.get(BuiltInRegistries.ITEM.getKey(stack.getItem())));
    }
}
