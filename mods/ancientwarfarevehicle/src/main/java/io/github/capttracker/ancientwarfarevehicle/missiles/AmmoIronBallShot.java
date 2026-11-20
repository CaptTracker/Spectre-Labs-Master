/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/missiles/AmmoIronBallShot.
 */
package io.github.capttracker.ancientwarfarevehicle.missiles;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** The small iron ball of canister and grape shot: 1 kg, 8 damage, not craftable. */
public class AmmoIronBallShot extends Ammo {

    public AmmoIronBallShot() {
        super("ammo_iron_ball_shot");
        renderScale = 0.05f;
        ammoWeight = 1.f;
        entityDamage = 8;
        vehicleDamage = 8;
        isCraftable = false;
        configName = "iron_ball_shot";
        modelTexture = ammoTexture("ammo_stone_shot");
    }

    @Override
    public void onImpactWorld(Level level, float x, float y, float z, MissileBase missile, BlockHitResult hit) {
        // NOOP
    }

    @Override
    public void onImpactEntity(Level level, Entity entity, float x, float y, float z, MissileBase missile) {
        if (!level.isClientSide) {
            entity.hurt(MissileDamage.causeEntityMissileDamage(missile, false, false), getEntityDamage());
        }
    }
}
