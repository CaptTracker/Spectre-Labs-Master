/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/missiles/AmmoHwachaRocketFlame.
 */
package io.github.capttracker.ancientwarfarevehicle.missiles;

import io.github.capttracker.ancientwarfarevehicle.config.AWVehicleStatics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** The flaming hwacha rocket: 1.1 kg, config damage (5), persistent; ignites the target block, entities burn for 2 seconds. */
public class AmmoHwachaRocketFlame extends Ammo {

    public AmmoHwachaRocketFlame() {
        super("ammo_hwacha_rocket_flame");
        this.entityDamage = AWVehicleStatics.vehicleStats.ammoHwachaRocketFlameDamage;
        this.vehicleDamage = AWVehicleStatics.vehicleStats.ammoHwachaRocketFlameDamage;
        this.isArrow = true;
        this.isPersistent = true;
        this.isRocket = true;
        this.isFlaming = true;
        this.ammoWeight = 1.1f;
        this.renderScale = 0.2f;
        this.configName = "hwacha_rocket_flame";
        this.modelTexture = ammoTexture("arrow_wood");
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
            entity.hurt(MissileDamage.causeEntityMissileDamage(missile, true, false), this.getEntityDamage());
            entity.igniteForSeconds(2);
        }
    }
}
