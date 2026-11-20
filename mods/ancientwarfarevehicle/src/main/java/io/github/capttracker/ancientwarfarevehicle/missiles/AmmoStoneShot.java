/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/missiles/AmmoStoneShot.
 */
package io.github.capttracker.ancientwarfarevehicle.missiles;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Stone shot (10/15/30/45 kg), damage = weight: breaks blocks around the impact on three layers when
 * {@code maxHardness - 15 * distance} exceeds the block hardness,
 * {@code maxHardness = 5 + (weight * 0.2 + weight * 0.8 * random) * 0.6}.
 */
public class AmmoStoneShot extends Ammo {

    public AmmoStoneShot(int weight) {
        super("ammo_stone_shot_" + weight);
        isPersistent = false;
        isArrow = false;
        isRocket = false;
        ammoWeight = weight;
        configName = "stone_shot_" + weight;
        entityDamage = weight;
        vehicleDamage = weight;
        float scaleFactor = weight + 45.f;
        renderScale = (weight / scaleFactor) * 2;
        modelTexture = ammoTexture("ammo_stone_shot");
    }

    @Override
    public void onImpactWorld(Level level, float x, float y, float z, MissileBase missile, BlockHitResult hit) {
        if (level.isClientSide) {
            return;
        }
        BlockPos origin = new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));

        float maxHardness = 5 + (ammoWeight * 0.2f + ammoWeight * 0.8f * level.getRandom().nextFloat()) * 0.6f;

        breakAroundOnLevel(level, origin, origin, maxHardness);
        breakAroundOnLevel(level, origin, origin.above(), maxHardness);
        breakAroundOnLevel(level, origin, origin.below(), maxHardness);
    }

    @Override
    public void onImpactEntity(Level level, Entity entity, float x, float y, float z, MissileBase missile) {
        if (!level.isClientSide) {
            entity.hurt(MissileDamage.causeEntityMissileDamage(missile, false, false), getEntityDamage());
        }
    }
}
