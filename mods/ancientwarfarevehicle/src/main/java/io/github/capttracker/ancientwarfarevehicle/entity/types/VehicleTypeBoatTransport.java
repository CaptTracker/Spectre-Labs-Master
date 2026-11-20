/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeBoatTransport.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleMovementType;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSettings;
import io.github.capttracker.ancientwarfarevehicle.entity.materials.VehicleMaterial;
import io.github.capttracker.ancientwarfarevehicle.entity.varhelpers.DummyVehicleHelper;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleFiringVarsHelper;
import io.github.capttracker.ancientwarfarevehicle.registry.ArmorRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.UpgradeRegistry;
import net.minecraft.resources.ResourceLocation;

/** Type 20: a transport boat with 216 storage slots; it has no weapon. */
public class VehicleTypeBoatTransport extends VehicleType {
    public VehicleTypeBoatTransport(int typeNum) {
        super(typeNum);
        this.configName = "boat_transport";
        baseHealth = VehicleSettings.vehicleChestBoatHealth;
        this.vehicleMaterial = VehicleMaterial.materialWood;
        this.movementType = VehicleMovementType.WATER;
        this.materialCount = 5;
        this.validArmors.add(ArmorRegistry.armorStone);
        this.validArmors.add(ArmorRegistry.armorObsidian);
        this.validArmors.add(ArmorRegistry.armorIron);
        this.width = 2.7f;
        this.height = 1.4f;
        this.mountable = true;
        this.drivable = true;
        this.combatEngine = false;
        this.riderSits = true;
        this.riderForwardsOffset = 1.4f;
        this.riderVerticalOffset = 0.55f;
        addSeat(SeatRole.PASSENGER, -0.2f, 0.f, 0.55f);
        addSeat(SeatRole.PASSENGER, -1.0f, 0.f, 0.55f);
        this.baseStrafeSpeed = 2.f;
        this.baseForwardSpeed = 6.2f * 0.05f;
        this.ammoBaySize = 0;
        this.upgradeBaySize = 6;
        this.armorBaySize = 6;
        this.storageBaySize = 54 * 4;
        this.displayName = nameKey();
        this.displayTooltip.add(tooltipKey("noweapon"));
        this.displayTooltip.add(tooltipKey("boat"));
        this.displayTooltip.add(tooltipKey("noturret"));
        this.displayTooltip.add(tooltipKey("storage"));
        this.validUpgrades.add(UpgradeRegistry.speedUpgrade);
    }

    @Override
    public ResourceLocation getTextureForMaterialLevel(int level) {
        return vehicleTexture("boat_transport", level);
    }

    @Override
    public VehicleFiringVarsHelper getFiringVarsHelper(VehicleBase veh) {
        return new DummyVehicleHelper(veh);
    }
}
