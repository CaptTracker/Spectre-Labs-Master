/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): the big ships.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleMovementType;
import io.github.capttracker.ancientwarfarevehicle.entity.materials.VehicleMaterial;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipSpec;
import io.github.capttracker.ancientwarfarevehicle.entity.varhelpers.DummyVehicleHelper;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleFiringVarsHelper;
import io.github.capttracker.ancientwarfarevehicle.registry.ArmorRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.UpgradeRegistry;
import net.minecraft.resources.ResourceLocation;

/**
 * The base of the three ships (fishing boat, cog, war galley): a large vehicle with a walkable deck, several seats and, on the two bigger ones, turret mounts
 * worked from gunner seats. The type's width and height are only the box of the entity in the middle of the hull; the hull itself is the {@link ShipSpec}.
 * Soldiers do not pilot ships. {@code baseForwardSpeed} is the top speed with every source of power at full strength (see {@code ShipHelper}) and
 * {@code baseStrafeSpeed} the top turn rate in degrees a tick.
 */
abstract class VehicleTypeShip extends VehicleType {
    private final String textureName;

    VehicleTypeShip(int typeNum, String configName, ShipSpec design) {
        super(typeNum);
        this.configName = configName;
        this.textureName = configName;
        this.shipSpec = design.build();
        this.vehicleMaterial = VehicleMaterial.materialWood;
        this.movementType = VehicleMovementType.WATER;
        this.materialCount = 5;
        this.width = 2.5f;
        this.height = 2.5f;
        this.mountable = true;
        this.drivable = true;
        this.combatEngine = false;
        this.riderSits = true;
        this.pilotableBySoldiers = false;
        this.validArmors.add(ArmorRegistry.armorStone);
        this.validArmors.add(ArmorRegistry.armorObsidian);
        this.validArmors.add(ArmorRegistry.armorIron);
        this.validUpgrades.add(UpgradeRegistry.speedUpgrade);
        this.ammoBaySize = 0;
        this.upgradeBaySize = 3;
        this.armorBaySize = 3;
        this.displayName = nameKey();
        this.displayTooltip.add(tooltipKey("ship"));
        this.displayTooltip.add(tooltipKey("sails"));
    }

    /** The height of a seated rider's seat over the keel: the deck, plus the 0.35 a seated player is lowered by. */
    protected final float seatHeight() {
        return shipSpec.deckTop() + 0.35f;
    }

    @Override
    public ResourceLocation getTextureForMaterialLevel(int level) {
        return vehicleTexture(textureName, level);
    }

    @Override
    public VehicleFiringVarsHelper getFiringVarsHelper(VehicleBase veh) {
        return new DummyVehicleHelper(veh);
    }
}
