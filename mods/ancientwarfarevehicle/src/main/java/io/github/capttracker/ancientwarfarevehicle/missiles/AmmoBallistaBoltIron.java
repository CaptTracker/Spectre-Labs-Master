/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/missiles/AmmoBallistaBoltIron.
 */
package io.github.capttracker.ancientwarfarevehicle.missiles;

import io.github.capttracker.ancientwarfarevehicle.config.AWVehicleStatics;
import io.github.capttracker.ancientwarfarevehicle.init.AwVehicles;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** The iron ballista bolt: 2 kg, config damage (30), persistent. */
public class AmmoBallistaBoltIron extends Ammo {

    public AmmoBallistaBoltIron() {
        super("ammo_ballista_bolt_iron");
        ammoWeight = 2.f;
        renderScale = 0.3f;
        vehicleDamage = AWVehicleStatics.vehicleStats.ammoBallistaBoltIronDamage;
        entityDamage = AWVehicleStatics.vehicleStats.ammoBallistaBoltIronDamage;
        isArrow = true;
        isRocket = false;
        isPersistent = true;
        configName = "ballist_bolt_iron";
        modelTexture = ammoTexture("arrow_iron");
    }

    @Override
    public void onImpactWorld(Level level, float x, float y, float z, MissileBase missile, BlockHitResult hit) {
        // noop
    }

    @Override
    public void onImpactEntity(Level level, Entity entity, float x, float y, float z, MissileBase missile) {
        if (!level.isClientSide) {
            level.playSound(null, x, y, z, AwVehicles.BALLISTA_BOLT_HIT_ENTITY.get(), SoundSource.NEUTRAL, 2, 1);
            entity.hurt(MissileDamage.causeEntityMissileDamage(missile, false, false), getEntityDamage());
        }
    }
}
