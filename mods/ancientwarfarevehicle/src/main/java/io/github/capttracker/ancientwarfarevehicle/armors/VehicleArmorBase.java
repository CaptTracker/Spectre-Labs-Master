/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/armors/VehicleArmorBase.
 */
package io.github.capttracker.ancientwarfarevehicle.armors;

import io.github.capttracker.ancientwarfarevehicle.AncientWarfareVehicles;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.resources.ResourceLocation;

public abstract class VehicleArmorBase implements IVehicleArmor {
    protected float general = 0.f;
    protected float explosive = 0.f;
    protected float fire = 0.f;
    protected float weight = 50.f;
    private final ResourceLocation registryName;

    protected VehicleArmorBase(String regName) {
        registryName = Ids.of(AncientWarfareVehicles.MOD_ID, regName);
    }

    @Override
    public ResourceLocation getRegistryName() {
        return registryName;
    }

    @Override
    public float getGeneralDamageReduction() {
        return general;
    }

    @Override
    public float getExplosiveDamageReduction() {
        return explosive;
    }

    @Override
    public float getFireDamageReduction() {
        return fire;
    }

    @Override
    public float getArmorWeight() {
        return weight;
    }
}
