/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeChestCart.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSettings;
import io.github.capttracker.ancientwarfarevehicle.entity.materials.VehicleMaterial;
import io.github.capttracker.ancientwarfarevehicle.entity.varhelpers.DummyVehicleHelper;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleFiringVarsHelper;
import io.github.capttracker.ancientwarfarevehicle.registry.ArmorRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.UpgradeRegistry;
import net.minecraft.resources.ResourceLocation;

/**
 * Type 17: the chest cart, a mobile cart with 216 storage slots and no weapon. Unfinished in the original
 * ("TODO implement chest cart"): its firing state helper did nothing, which is what {@link DummyVehicleHelper} does.
 */
public class VehicleTypeChestCart extends VehicleType {
    public VehicleTypeChestCart(int typeNum) {
        super(typeNum);
        this.configName = "chest_cart";
        this.vehicleMaterial = VehicleMaterial.materialWood;
        this.materialCount = 3;
        baseHealth = VehicleSettings.vehicleChestCartHealth;
        this.validArmors.add(ArmorRegistry.armorStone);
        this.validArmors.add(ArmorRegistry.armorObsidian);
        this.validArmors.add(ArmorRegistry.armorIron);
        this.validUpgrades.add(UpgradeRegistry.speedUpgrade);
        this.width = 2.7f;
        this.height = 1.8f;
        this.mountable = true;
        this.drivable = true;
        this.combatEngine = false;
        this.riderSits = false;
        this.pilotableBySoldiers = false;
        this.riderVerticalOffset = 0.35f;
        this.riderForwardsOffset = 2.85f;
        addSeat(SeatRole.PASSENGER, 0.9f, 0.f, 0.9f);
        this.baseForwardSpeed = 3.7f * 0.05f;
        this.baseStrafeSpeed = 1.75f;
        this.ammoBaySize = 0;
        this.upgradeBaySize = 6;
        this.armorBaySize = 6;
        this.storageBaySize = 54 * 4;
        this.displayName = nameKey();
        this.displayTooltip.add(tooltipKey("noweapon"));
        this.displayTooltip.add(tooltipKey("mobile"));
        this.displayTooltip.add(tooltipKey("noturret"));
        this.displayTooltip.add(tooltipKey("storage"));
    }

    @Override
    public VehicleFiringVarsHelper getFiringVarsHelper(VehicleBase veh) {
        return new DummyVehicleHelper(veh);
    }

    @Override
    public ResourceLocation getTextureForMaterialLevel(int level) {
        return vehicleTexture("chest_cart", level);
    }
}
