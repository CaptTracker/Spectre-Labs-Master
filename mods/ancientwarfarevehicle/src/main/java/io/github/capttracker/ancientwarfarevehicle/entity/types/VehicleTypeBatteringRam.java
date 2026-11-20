/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeBatteringRam.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSettings;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSounds;
import io.github.capttracker.ancientwarfarevehicle.entity.materials.VehicleMaterial;
import io.github.capttracker.ancientwarfarevehicle.entity.varhelpers.BatteringRamVarHelper;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleFiringVarsHelper;
import io.github.capttracker.ancientwarfarevehicle.registry.ArmorRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.UpgradeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Type 8: the battering ram. No ammo: its "missile" is the swing of the log. */
public class VehicleTypeBatteringRam extends VehicleType {
    public VehicleTypeBatteringRam(int typeNum) {
        super(typeNum);
        this.configName = "battering_ram";
        this.baseHealth = VehicleSettings.vehicleBatteringRamHealth;
        this.vehicleMaterial = VehicleMaterial.materialWood;
        this.validArmors.add(ArmorRegistry.armorStone);
        this.validArmors.add(ArmorRegistry.armorIron);
        this.validArmors.add(ArmorRegistry.armorObsidian);
        this.validUpgrades.add(UpgradeRegistry.speedUpgrade);
        this.validUpgrades.add(UpgradeRegistry.reloadUpgrade);
        this.width = 2.7f;
        this.height = 2.1f;
        this.mountable = true;
        this.drivable = true;
        this.combatEngine = true;
        this.powerAdjustable = false;
        this.pitchAdjustable = false;
        this.yawAdjustable = false;
        this.riderSits = true;
        this.riderMovesWithTurret = false;
        this.accuracy = 0.99f;
        this.baseStrafeSpeed = 1.f;
        this.baseForwardSpeed = 4.5f * 0.05f;
        this.basePitchMax = 0.f;
        this.basePitchMin = 0.f;
        this.turretRotationMax = 0.f;
        this.turretForwardsOffset = 2.6f;
        this.turretVerticalOffset = 1.8f;
        this.turretHorizontalOffset = -.3f;
        this.minAttackDistance = 1.f;
        this.riderForwardsOffset = 0.f;
        this.riderVerticalOffset = 0.65f;
        this.riderHorizontalOffset = 0.325f;
        this.displayName = nameKey();
        this.displayTooltip.add(tooltipKey("weight"));
        this.displayTooltip.add(tooltipKey("mobile"));
        this.displayTooltip.add(tooltipKey("noturret"));
        this.displayTooltip.add(tooltipKey("ram"));
        this.storageBaySize = 0;
        this.armorBaySize = 6;
        this.upgradeBaySize = 3;
    }

    @Override
    public VehicleFiringVarsHelper getFiringVarsHelper(VehicleBase veh) {
        return new BatteringRamVarHelper(veh);
    }

    @Override
    public ResourceLocation getTextureForMaterialLevel(int level) {
        return vehicleTexture("battering_ram", level);
    }

    /** The head position of the ram and its six neighbours: the positions a swing hits. */
    public static BlockPos[] getEffectedPositions(VehicleBase vehicle) {
        BlockPos[] positions = new BlockPos[7];
        Vec3 offset = vehicle.getMissileOffset();
        float bx = (float) (vehicle.getX() + offset.x);
        float by = (float) (vehicle.getY() + offset.y);
        float bz = (float) (vehicle.getZ() + offset.z);
        BlockPos blockHit = BlockPos.containing(bx, by, bz);
        positions[0] = blockHit;
        positions[1] = new BlockPos(blockHit.getX() - 1, blockHit.getY(), blockHit.getZ());
        positions[2] = new BlockPos(blockHit.getX(), blockHit.getY() - 1, blockHit.getZ());
        positions[3] = new BlockPos(blockHit.getX(), blockHit.getY(), blockHit.getZ() - 1);
        positions[4] = new BlockPos(blockHit.getX() + 1, blockHit.getY(), blockHit.getZ());
        positions[5] = new BlockPos(blockHit.getX(), blockHit.getY() + 1, blockHit.getZ());
        positions[6] = new BlockPos(blockHit.getX(), blockHit.getY(), blockHit.getZ() + 1);
        return positions;
    }

    @Override
    public void playFiringSound(VehicleBase vehicleBase) {
        VehicleSounds.play(vehicleBase, VehicleSounds.BATTERING_RAM_LAUNCH, 2, 1);
    }
}
