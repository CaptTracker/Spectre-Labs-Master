/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/armors/IVehicleArmor (getNeededResearch and the unused ArmorType enum were dropped: research was removed from the mod).
 */
package io.github.capttracker.ancientwarfarevehicle.armors;

import net.minecraft.resources.ResourceLocation;

/** Stat effects of one vehicle armor plate item. Reductions are percent; the registry name is the item's registry name. */
public interface IVehicleArmor {
    ResourceLocation getRegistryName();

    float getGeneralDamageReduction();

    float getExplosiveDamageReduction();

    float getFireDamageReduction();

    /** Weight in kg added to the vehicle. */
    float getArmorWeight();
}
