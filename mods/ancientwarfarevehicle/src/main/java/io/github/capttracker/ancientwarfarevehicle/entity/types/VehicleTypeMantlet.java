/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 12): the mantlet.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleMovementType;
import io.github.capttracker.ancientwarfarevehicle.entity.materials.VehicleMaterial;
import io.github.capttracker.ancientwarfarevehicle.entity.varhelpers.DummyVehicleHelper;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleFiringVarsHelper;
import io.github.capttracker.ancientwarfarevehicle.registry.ArmorRegistry;
import net.minecraft.resources.ResourceLocation;

/**
 * Type 28: the mantlet, a wooden shield 3 blocks wide and 2.5 high on small wheels that one rider (or NPC) pushes from behind. It stops arrows, bolts and missiles that fly into its
 * front ({@code entity.siege.MantletShield}, for whatever is behind it) and has health of its own; what comes from behind, from the side or over the top passes, so archers
 * can shoot past it. The box of the entity is only the middle of the shield; the rest of the width counts when the player aims at it (the pick radius).
 */
public class VehicleTypeMantlet extends VehicleType {
    public VehicleTypeMantlet(int typeNum) {
        super(typeNum);
        this.configName = "mantlet";
        this.vehicleMaterial = VehicleMaterial.materialWood;
        this.movementType = VehicleMovementType.GROUND;
        this.materialCount = 5;
        this.width = 1.3f;
        this.height = 2.6f;
        this.pushed = true;
        this.pushersForFullSpeed = 1;
        this.blocksProjectiles = true;
        this.maxUpStep = 0.6f;
        this.pickRadius = 0.85f;
        this.mountable = true;
        this.drivable = true;
        this.combatEngine = false;
        this.riderSits = false;
        this.pilotableBySoldiers = true;
        this.baseForwardSpeed = 0.1f;
        this.baseStrafeSpeed = 1.5f;
        this.weight = 250;
        this.baseHealth = 60;
        this.cameraDistance = 6.0f;
        this.ammoBaySize = 0;
        this.upgradeBaySize = 0;
        this.armorBaySize = 3;
        this.storageBaySize = 0;
        this.validArmors.add(ArmorRegistry.armorStone);
        this.validArmors.add(ArmorRegistry.armorIron);
        this.validArmors.add(ArmorRegistry.armorObsidian);
        this.displayName = nameKey();
        this.displayTooltip.add(tooltipKey("noweapon"));
        this.displayTooltip.add(tooltipKey("noturret"));
        this.displayTooltip.add(tooltipKey("mobile"));
        this.displayTooltip.add(tooltipKey("mantlet"));
        // the one rider stands behind the shield and pushes it
        this.riderForwardsOffset = -0.95f;
        this.riderHorizontalOffset = 0.f;
        this.riderVerticalOffset = 0.02f;
    }

    @Override
    public ResourceLocation getTextureForMaterialLevel(int level) {
        return vehicleTexture("siege_tower", level);
    }

    @Override
    public VehicleFiringVarsHelper getFiringVarsHelper(VehicleBase veh) {
        return new DummyVehicleHelper(veh);
    }
}
