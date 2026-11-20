/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/missiles/AmmoHwachaRocketExplosive.
 */
package io.github.capttracker.ancientwarfarevehicle.missiles;

import io.github.capttracker.ancientwarfarevehicle.config.AWVehicleStatics;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** The explosive hwacha rocket: 1.3 kg, config damage (4), explodes on impact (0.6 on blocks, 0.8 on entities). */
public class AmmoHwachaRocketExplosive extends Ammo {

    public AmmoHwachaRocketExplosive() {
        super("ammo_hwacha_rocket_explosive");
        this.entityDamage = AWVehicleStatics.vehicleStats.ammoHwachaRocketExplosiveDamage;
        this.vehicleDamage = AWVehicleStatics.vehicleStats.ammoHwachaRocketExplosiveDamage;
        this.isArrow = true;
        this.isPersistent = false;
        this.isRocket = true;
        this.ammoWeight = 1.3f;
        this.renderScale = 0.2f;
        this.configName = "hwacha_rocket_explosive";
        this.modelTexture = ammoTexture("arrow_wood");
    }

    @Override
    public void onImpactWorld(Level level, float x, float y, float z, MissileBase missile, BlockHitResult hit) {
        if (!level.isClientSide) {
            Vec3i dirVec = hit.getDirection().getNormal();
            Vec3 hitVec = hit.getLocation().add(dirVec.getX() * 0.2d, dirVec.getY() * 0.2d, dirVec.getZ() * 0.2d);
            createExplosion(level, missile, (float) hitVec.x, (float) hitVec.y, (float) hitVec.z, 0.6f);
        }
    }

    @Override
    public void onImpactEntity(Level level, Entity entity, float x, float y, float z, MissileBase missile) {
        if (!level.isClientSide) {
            entity.hurt(MissileDamage.causeEntityMissileDamage(missile, false, true), this.getEntityDamage());
            entity.igniteForSeconds(3);
            createExplosion(level, missile, x, y, z, 0.8f);
        }
    }
}
