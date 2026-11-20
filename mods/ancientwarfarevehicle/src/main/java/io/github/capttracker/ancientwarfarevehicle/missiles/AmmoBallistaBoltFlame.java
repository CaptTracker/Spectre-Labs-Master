/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/missiles/AmmoBallistaBoltFlame.
 */
package io.github.capttracker.ancientwarfarevehicle.missiles;

import io.github.capttracker.ancientwarfarevehicle.config.AWVehicleStatics;
import io.github.capttracker.ancientwarfarevehicle.init.AwVehicles;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** The flaming ballista bolt: 2.2 kg, config damage (16), persistent; ignites the target block and sets entities on fire for 4 seconds. */
public class AmmoBallistaBoltFlame extends Ammo {

    public AmmoBallistaBoltFlame() {
        super("ammo_ballista_bolt_flame");
        ammoWeight = 2.2f;
        renderScale = 0.3f;
        vehicleDamage = AWVehicleStatics.vehicleStats.ammoBallistaBoltFlameDamage;
        entityDamage = AWVehicleStatics.vehicleStats.ammoBallistaBoltFlameDamage;
        isArrow = true;
        isRocket = false;
        isPersistent = true;
        isFlaming = true;
        configName = "ballist_bolt_flame";
        modelTexture = ammoTexture("arrow_wood");
    }

    @Override
    public void onImpactWorld(Level level, float x, float y, float z, MissileBase missile, BlockHitResult hit) {
        if (!level.isClientSide) {
            igniteBlock(level, Mth.floor(x), Mth.floor(y) + 2, Mth.floor(z), 5);
        }
    }

    @Override
    public void onImpactEntity(Level level, Entity entity, float x, float y, float z, MissileBase missile) {
        if (!level.isClientSide) {
            level.playSound(null, x, y, z, AwVehicles.BALLISTA_BOLT_HIT_ENTITY.get(), SoundSource.NEUTRAL, 2, 1);
            entity.hurt(MissileDamage.causeEntityMissileDamage(missile, true, false), getEntityDamage());
            entity.igniteForSeconds(4);
        }
    }
}
