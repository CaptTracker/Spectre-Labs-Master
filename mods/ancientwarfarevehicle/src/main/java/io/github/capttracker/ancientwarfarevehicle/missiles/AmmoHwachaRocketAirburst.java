/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/missiles/AmmoHwachaRocketAirburst.
 */
package io.github.capttracker.ancientwarfarevehicle.missiles;

import io.github.capttracker.ancientwarfarevehicle.registry.AmmoRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The airburst hwacha rocket: 1.4 kg, no damage of its own. A proximity rocket: it detonates within 12 blocks of the ground or
 * 10 blocks of an entity (checked while it falls), bursting into 4 ball shots, and is removed.
 */
public class AmmoHwachaRocketAirburst extends Ammo {

    public AmmoHwachaRocketAirburst() {
        super("ammo_hwacha_rocket_airburst");
        this.entityDamage = 0;
        this.vehicleDamage = 0;
        this.isArrow = true;
        this.isPersistent = false;
        this.isRocket = true;
        this.isProximityAmmo = true;
        this.groundProximity = 12.f;
        this.entityProximity = 10f;
        this.ammoWeight = 1.4f;
        this.renderScale = 0.2f;
        this.configName = "hwacha_rocket_airburst";
        this.modelTexture = ammoTexture("arrow_wood");
    }

    @Override
    public void onImpactWorld(Level level, float x, float y, float z, MissileBase missile, BlockHitResult hit) {
        if (!level.isClientSide) {
            this.spawnAirBurst(level, x, y, z, 10, AmmoRegistry.ammoBallShot, 4, missile.shooterLiving);
            missile.discard();
        }
    }

    @Override
    public void onImpactEntity(Level level, Entity entity, float x, float y, float z, MissileBase missile) {
        if (!level.isClientSide) {
            this.spawnAirBurst(level, x, y, z, 10, AmmoRegistry.ammoBallShot, 4, missile.shooterLiving);
            missile.discard();
        }
    }
}
