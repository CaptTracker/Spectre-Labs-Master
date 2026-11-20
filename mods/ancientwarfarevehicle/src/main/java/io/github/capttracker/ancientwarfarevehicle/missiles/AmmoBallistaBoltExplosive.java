/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/missiles/AmmoBallistaBoltExplosive.
 */
package io.github.capttracker.ancientwarfarevehicle.missiles;

import io.github.capttracker.ancientwarfarevehicle.config.AWVehicleStatics;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** The explosive ballista bolt: 2.6 kg, config damage (15), explodes on impact (0.8 on blocks, 1.2 on entities). */
public class AmmoBallistaBoltExplosive extends Ammo {

    public AmmoBallistaBoltExplosive() {
        super("ammo_ballista_bolt_explosive");
        this.ammoWeight = 2.6f;
        this.renderScale = 0.3f;
        this.vehicleDamage = AWVehicleStatics.vehicleStats.ammoBallistaBoltExplosiveDamage;
        this.entityDamage = AWVehicleStatics.vehicleStats.ammoBallistaBoltExplosiveDamage;
        this.isArrow = true;
        this.isRocket = false;
        this.isPersistent = false;
        this.configName = "ballist_bolt_explosive";
        this.modelTexture = ammoTexture("arrow_wood");
    }

    @Override
    public void onImpactWorld(Level level, float x, float y, float z, MissileBase missile, BlockHitResult hit) {
        if (!level.isClientSide) {
            Vec3i dirVec = hit.getDirection().getNormal();
            Vec3 hitVec = hit.getLocation().add(dirVec.getX() * 0.2d, dirVec.getY() * 0.2d, dirVec.getZ() * 0.2d);
            createExplosion(level, missile, (float) hitVec.x, (float) hitVec.y, (float) hitVec.z, 0.8f);
        }
    }

    @Override
    public void onImpactEntity(Level level, Entity entity, float x, float y, float z, MissileBase missile) {
        if (!level.isClientSide) {
            entity.hurt(MissileDamage.causeEntityMissileDamage(missile, false, true), this.getEntityDamage());
            entity.igniteForSeconds(3);
            createExplosion(level, missile, x, y, z, 1.2f);
        }
    }
}
