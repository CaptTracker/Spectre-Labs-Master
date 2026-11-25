/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/missiles/AmmoClusterShot.
 */
package io.github.capttracker.ancientwarfarevehicle.missiles;

import io.github.capttracker.ancientwarfarevehicle.registry.AmmoRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** Cluster shot (10/15/30/45 kg): bursts into {@code weight} ball shots on impact. */
public class AmmoClusterShot extends Ammo {

    public AmmoClusterShot(int weight) {
        super("ammo_cluster_shot_" + weight);
        this.ammoWeight = weight;
        float scaleFactor = weight + 45.f;
        this.renderScale = (weight / scaleFactor) * 2;
        this.configName = "cluster_shot_" + weight;
        this.modelTexture = ammoTexture("ammo_stone_shot");

        this.entityDamage = 5;
        this.vehicleDamage = 5;
    }

    @Override
    public void onImpactWorld(Level level, float x, float y, float z, MissileBase missile, BlockHitResult hit) {
        if (!level.isClientSide) {
            spawnGroundBurst(level, hit, 10, AmmoRegistry.ammoBallShot, (int) ammoWeight, 35, missile.shooterLiving);
        }
    }

    @Override
    public void onImpactEntity(Level level, Entity entity, float x, float y, float z, MissileBase missile) {
        if (!level.isClientSide) {
            spawnAirBurst(level, (float) entity.getX(), (float) entity.getY() + entity.getBbHeight(), (float) entity.getZ(), 10,
                    AmmoRegistry.ammoBallShot, (int) ammoWeight, missile.shooterLiving);
        }
    }
}
