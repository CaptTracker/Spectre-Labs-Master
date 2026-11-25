/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/missiles/AmmoArrow.
 */
package io.github.capttracker.ancientwarfarevehicle.missiles;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** The NPC arrow: 1 kg, 8 damage, persistent, not craftable. */
public class AmmoArrow extends Ammo {

    public AmmoArrow() {
        super("ammo_arrow");
        ammoWeight = 1.f;
        renderScale = 0.2f;
        vehicleDamage = 8;
        entityDamage = 8;
        isArrow = true;
        isRocket = false;
        isPersistent = true;
        isCraftable = false;
        configName = "arrow";
        modelTexture = ammoTexture("arrow_wood");
    }

    @Override
    public void onImpactWorld(Level level, float x, float y, float z, MissileBase missile, BlockHitResult hit) {
        // noop
    }

    @Override
    public void onImpactEntity(Level level, Entity entity, float x, float y, float z, MissileBase missile) {
        if (!level.isClientSide) {
            entity.hurt(MissileDamage.causeEntityMissileDamage(missile, false, false), getEntityDamage());
        }
    }
}
