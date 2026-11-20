/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/missiles/AmmoCanisterShot.
 */
package io.github.capttracker.ancientwarfarevehicle.missiles;

import io.github.capttracker.ancientwarfarevehicle.config.AWVehicleStatics;
import io.github.capttracker.ancientwarfarevehicle.registry.AmmoRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** Canister shot (5/10/15/25 kg): bursts into {@code weight} iron ball shots on impact. */
public class AmmoCanisterShot extends Ammo {

    public AmmoCanisterShot(int weight) {
        super("ammo_canister_shot_" + weight);
        this.ammoWeight = weight;
        float scaleFactor = weight + 45.f;
        this.renderScale = (weight / scaleFactor) * 2;
        this.configName = "canister_shot_" + weight;
        this.modelTexture = ammoTexture("ammo_stone_shot");
        this.entityDamage = AWVehicleStatics.vehicleStats.ammoCanisterDamage;
        this.vehicleDamage = AWVehicleStatics.vehicleStats.ammoCanisterDamage;
    }

    @Override
    public void onImpactWorld(Level level, float x, float y, float z, MissileBase missile, BlockHitResult hit) {
        if (!level.isClientSide) {
            spawnGroundBurst(level, hit, 10, AmmoRegistry.ammoBallIronShot, (int) ammoWeight, 35, missile.shooterLiving);
        }
    }

    @Override
    public void onImpactEntity(Level level, Entity entity, float x, float y, float z, MissileBase missile) {
        if (!level.isClientSide) {
            spawnAirBurst(level, (float) entity.getX(), (float) entity.getY() + entity.getBbHeight(), (float) entity.getZ(), 10,
                    AmmoRegistry.ammoBallIronShot, (int) ammoWeight, missile.shooterLiving);
        }
    }
}
