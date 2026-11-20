/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/missiles/AmmoPebbleShot.
 */
package io.github.capttracker.ancientwarfarevehicle.missiles;

import io.github.capttracker.ancientwarfarevehicle.registry.AmmoRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** Pebble shot (10/15/30/45 kg): a container ammo; the vehicle fires {@code weight} ball shots at launch instead. */
public class AmmoPebbleShot extends Ammo {

    public AmmoPebbleShot(int weight) {
        super("ammo_pebble_shot_" + weight);
        this.isPersistent = false;
        this.isArrow = false;
        this.isRocket = false;
        this.ammoWeight = weight;
        this.secondaryAmmoCount = weight;
        float scaleFactor = weight + 45.f;
        this.renderScale = (weight / scaleFactor) * 2;
        this.configName = "pebble_shot_" + weight;
        this.modelTexture = ammoTexture("ammo_stone_shot");
    }

    @Override
    public boolean hasSecondaryAmmo() {
        return true;
    }

    @Override
    public IAmmo getSecondaryAmmoType() {
        return AmmoRegistry.ammoBallShot;
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
