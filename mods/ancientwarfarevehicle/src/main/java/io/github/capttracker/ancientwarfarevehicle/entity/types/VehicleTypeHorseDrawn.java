/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): the base of the horse-drawn cart and wagon.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleMovementType;
import io.github.capttracker.ancientwarfarevehicle.entity.materials.VehicleMaterial;
import io.github.capttracker.ancientwarfarevehicle.entity.varhelpers.DummyVehicleHelper;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleFiringVarsHelper;
import io.github.capttracker.ancientwarfarevehicle.registry.ArmorRegistry;

/**
 * A wheeled vehicle with no weapon that horses pull. The vehicle follows the hitch point of its horse (see
 * {@code entity.hitch.CartHitch}); the forward speed of the type only matters for the slow push of a cart with no horse. The driver seat is a
 * bench; whoever sits in it steers the lead horse with the movement keys.
 */
abstract class VehicleTypeHorseDrawn extends VehicleType {
    VehicleTypeHorseDrawn(int typeNum, String configName) {
        super(typeNum);
        this.configName = configName;
        this.vehicleMaterial = VehicleMaterial.materialWood;
        this.movementType = VehicleMovementType.GROUND;
        this.materialCount = 5;
        this.horseDrawn = true;
        this.breaksOnAttack = true;
        this.validArmors.add(ArmorRegistry.armorStone);
        this.validArmors.add(ArmorRegistry.armorObsidian);
        this.validArmors.add(ArmorRegistry.armorIron);
        this.mountable = true;
        this.drivable = true;
        this.combatEngine = false;
        this.riderSits = true;
        this.pilotableBySoldiers = false;
        this.baseForwardSpeed = 3.7f * 0.05f;
        this.baseStrafeSpeed = 1.75f;
        this.ammoBaySize = 0;
        this.upgradeBaySize = 0;
        this.armorBaySize = 3;
        this.displayName = nameKey();
        this.displayTooltip.add(tooltipKey("noweapon"));
        this.displayTooltip.add(tooltipKey("horse"));
        this.displayTooltip.add(tooltipKey("noturret"));
        this.displayTooltip.add(tooltipKey("storage"));
    }

    @Override
    public VehicleFiringVarsHelper getFiringVarsHelper(VehicleBase veh) {
        return new DummyVehicleHelper(veh);
    }
}
