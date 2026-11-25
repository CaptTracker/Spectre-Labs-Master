/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/missiles/AmmoExplosiveShot.
 */
package io.github.capttracker.ancientwarfarevehicle.missiles;

import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Explosive shot (10/15/30/45 kg) and high explosive shot ("big", same weights): explodes on impact with a power of
 * {@code 3.5 * weight / 45} (HE: {@code 7 * weight / 45}).
 */
public class AmmoExplosiveShot extends Ammo {

    private final boolean bigExplosion;

    public AmmoExplosiveShot(int weight, boolean bigExplosion) {
        super("ammo_explosive_shot_" + weight + (bigExplosion ? "_big" : ""));
        this.ammoWeight = weight;
        this.bigExplosion = bigExplosion;
        this.entityDamage = weight;
        this.vehicleDamage = weight;
        float scaleFactor = weight + 45.f;
        this.renderScale = (weight / scaleFactor) * 2;

        if (bigExplosion) {
            this.configName = "high_explosive_" + weight;
        } else {
            this.configName = "explosive_" + weight;
        }
        this.modelTexture = ammoTexture("ammo_stone_shot");
    }

    @Override
    public void onImpactWorld(Level level, float x, float y, float z, MissileBase missile, BlockHitResult hit) {
        if (!level.isClientSide) {
            Vec3i dirVec = hit.getDirection().getNormal();
            Vec3 hitVec = hit.getLocation().add(dirVec.getX() * 0.2d, dirVec.getY() * 0.2d, dirVec.getZ() * 0.2d);
            explode(level, (float) hitVec.x, (float) hitVec.y, (float) hitVec.z, missile);
        }
    }

    @Override
    public void onImpactEntity(Level level, Entity entity, float x, float y, float z, MissileBase missile) {
        if (!level.isClientSide) {
            explode(level, x, y, z, missile);
        }
    }

    private void explode(Level level, float x, float y, float z, MissileBase missile) {
        float maxPower = bigExplosion ? 7.f : 3.5f;
        float powerPercent = ammoWeight / 45.f;
        float power = maxPower * powerPercent;
        createExplosion(level, missile, x, y, z, power);
    }
}
