/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/missiles/AmmoFlameShot.
 */
package io.github.capttracker.ancientwarfarevehicle.missiles;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Flame shot (10/15/30/45 kg), 8 damage: sets fire to the columns around the impact (1 column, a cross at 15 kg, a square at
 * 30 kg, a wider pattern at 45 kg), each searched 5 blocks down; entities burn for 3 seconds. Needs the {@code blockFires} option.
 */
public class AmmoFlameShot extends Ammo {

    public AmmoFlameShot(int weight) {
        super("ammo_flame_shot_" + weight);
        this.isPersistent = false;
        this.isArrow = false;
        this.isRocket = false;
        this.isFlaming = true;
        this.ammoWeight = weight;
        float scaleFactor = weight + 45.f;
        this.renderScale = (weight / scaleFactor) * 2;
        this.configName = "flame_shot_" + weight;
        this.vehicleDamage = 8;
        this.entityDamage = 8;
        this.modelTexture = ammoTexture("ammo_stone_shot");
    }

    @Override
    public void onImpactWorld(Level level, float x, float y, float z, MissileBase missile, BlockHitResult hit) {
        if (!level.isClientSide) {
            ignite(level, x, y, z);
        }
    }

    @Override
    public void onImpactEntity(Level level, Entity entity, float x, float y, float z, MissileBase missile) {
        if (!level.isClientSide) {
            entity.hurt(MissileDamage.causeEntityMissileDamage(missile, true, false), this.getEntityDamage());
            entity.igniteForSeconds(3);
            ignite(level, x, y, z);
        }
    }

    /** The fire pattern; the block position is floored (1.12.2 truncated, which was off by one for negative coordinates). */
    private void ignite(Level level, float x, float y, float z) {
        int bx = Mth.floor(x);
        int by = Mth.floor(y) + 2;
        int bz = Mth.floor(z);
        this.igniteBlock(level, bx, by, bz, 5);
        if (this.ammoWeight >= 15) {
            this.igniteBlock(level, bx - 1, by, bz, 5);
            this.igniteBlock(level, bx + 1, by, bz, 5);
            this.igniteBlock(level, bx, by, bz - 1, 5);
            this.igniteBlock(level, bx, by, bz + 1, 5);
        }
        if (ammoWeight >= 30) {
            this.igniteBlock(level, bx - 1, by, bz - 1, 5);
            this.igniteBlock(level, bx - 1, by, bz + 1, 5);
            this.igniteBlock(level, bx + 1, by, bz - 1, 5);
            this.igniteBlock(level, bx + 1, by, bz + 1, 5);
            this.igniteBlock(level, bx - 2, by, bz, 5);
            this.igniteBlock(level, bx + 2, by, bz, 5);
            this.igniteBlock(level, bx, by, bz - 2, 5);
            this.igniteBlock(level, bx, by, bz + 2, 5);
        }
        if (ammoWeight >= 45) {
            this.igniteBlock(level, bx - 1, by, bz - 2, 5);
            this.igniteBlock(level, bx + 1, by, bz - 2, 5);
            this.igniteBlock(level, bx - 1, by, bz + 2, 5);
            this.igniteBlock(level, bx + 1, by, bz + 2, 5);
            this.igniteBlock(level, bx - 2, by, bz - 1, 5);
            this.igniteBlock(level, bx - 2, by, bz + 1, 5);
            this.igniteBlock(level, bx + 2, by, bz - 1, 5);
            this.igniteBlock(level, bx + 2, by, bz + 1, 5);
        }
    }
}
