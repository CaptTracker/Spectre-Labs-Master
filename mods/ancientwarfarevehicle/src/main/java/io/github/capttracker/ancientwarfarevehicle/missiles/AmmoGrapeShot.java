/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/missiles/AmmoGrapeShot.
 */
package io.github.capttracker.ancientwarfarevehicle.missiles;

import io.github.capttracker.ancientwarfarevehicle.registry.AmmoRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** Grape shot (5/10/15/25 kg): a container ammo; the vehicle fires {@code weight} iron ball shots at launch instead. */
public class AmmoGrapeShot extends Ammo {

    public AmmoGrapeShot(int weight) {
        super("ammo_grape_shot_" + weight);
        this.ammoWeight = weight;
        this.secondaryAmmoCount = weight;
        float scaleFactor = weight + 45.f;
        this.renderScale = (weight / scaleFactor) * 2;
        this.configName = "grape_shot_" + weight;
        this.modelTexture = ammoTexture("ammo_stone_shot");
    }

    @Override
    public boolean hasSecondaryAmmo() {
        return true;
    }

    @Override
    public IAmmo getSecondaryAmmoType() {
        return AmmoRegistry.ammoBallIronShot;
    }

    @Override
    public void onImpactWorld(Level level, float x, float y, float z, MissileBase missile, BlockHitResult hit) {
        // noop
    }

    @Override
    public void onImpactEntity(Level level, Entity entity, float x, float y, float z, MissileBase missile) {
        // noop
    }
}
