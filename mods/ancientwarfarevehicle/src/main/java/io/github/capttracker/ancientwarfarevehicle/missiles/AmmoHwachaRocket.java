/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/missiles/AmmoHwachaRocket.
 */
package io.github.capttracker.ancientwarfarevehicle.missiles;

import io.github.capttracker.ancientwarfarevehicle.config.AWVehicleStatics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** The plain hwacha rocket: 1 kg, config damage (6), a persistent rocket. */
public class AmmoHwachaRocket extends Ammo {

    /** Burn time in ticks per block/tick of launch speed times 20: {@code speed * 20 * BURN_TIME_FACTOR}. */
    public static final float BURN_TIME_FACTOR = 3.f;
    /** Acceleration (blocks per tick squared) while the rocket burns. */
    public static final float ACCELERATION_FACTOR = 0.01f;

    public AmmoHwachaRocket() {
        super("ammo_hwacha_rocket");
        this.entityDamage = AWVehicleStatics.vehicleStats.ammoHwachaRocketDamage;
        this.vehicleDamage = AWVehicleStatics.vehicleStats.ammoHwachaRocketDamage;
        this.isArrow = true;
        this.isPersistent = true;
        this.isRocket = true;
        this.ammoWeight = 1.f;
        this.renderScale = 0.2f;
        this.configName = "hwacha_rocket";
        this.modelTexture = ammoTexture("arrow_wood");
    }

    @Override
    public void onImpactWorld(Level level, float x, float y, float z, MissileBase missile, BlockHitResult hit) {
        // noop
    }

    @Override
    public void onImpactEntity(Level level, Entity entity, float x, float y, float z, MissileBase missile) {
        if (!level.isClientSide) {
            entity.hurt(MissileDamage.causeEntityMissileDamage(missile, false, false), this.getEntityDamage());
        }
    }
}
