/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/missiles/AmmoBallistaBolt.
 */
package io.github.capttracker.ancientwarfarevehicle.missiles;

import io.github.capttracker.ancientwarfarevehicle.config.AWVehicleStatics;
import io.github.capttracker.ancientwarfarevehicle.init.AwVehicles;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** The plain ballista bolt: 2 kg, config damage (18), persistent, sounds on impact. */
public class AmmoBallistaBolt extends Ammo {

    public AmmoBallistaBolt() {
        super("ammo_ballista_bolt");
        ammoWeight = 2.f;
        renderScale = 0.3f;
        vehicleDamage = AWVehicleStatics.vehicleStats.ammoBallistaBoltDamage;
        entityDamage = AWVehicleStatics.vehicleStats.ammoBallistaBoltDamage;
        isArrow = true;
        isRocket = false;
        isPersistent = true;
        configName = "ballist_bolt";
        modelTexture = ammoTexture("arrow_wood");
    }

    @Override
    public void onImpactWorld(Level level, float x, float y, float z, MissileBase missile, BlockHitResult hit) {
        if (!level.isClientSide) {
            level.playSound(null, x, y, z, AwVehicles.BALLISTA_BOLT_HIT_GROUND.get(), SoundSource.AMBIENT, 2, 1);
        }
    }

    @Override
    public void onImpactEntity(Level level, Entity entity, float x, float y, float z, MissileBase missile) {
        if (!level.isClientSide) {
            // Level.playSound with a null player is heard by everyone nearby; Entity.playSound skips the entity itself when it is a player
            level.playSound(null, x, y, z, AwVehicles.BALLISTA_BOLT_HIT_ENTITY.get(), SoundSource.NEUTRAL, 2, 1);
            entity.hurt(MissileDamage.causeEntityMissileDamage(missile, false, false), getEntityDamage());
        }
    }
}
