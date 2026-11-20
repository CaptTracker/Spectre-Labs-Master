/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/missiles/AmmoNapalmShot.
 */
package io.github.capttracker.ancientwarfarevehicle.missiles;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Napalm shot (10/15/30/45 kg), damage = weight, flaming: places lava source blocks on the first solid block of the impact
 * column (searched 5 down), plus the forward block at 15 kg, the rear block at 30 kg and both sides at 45 kg. Entities burn for
 * 3 seconds and the lava is placed at their feet. Needs the {@code blockFires} option.
 *
 * <p>Fix: 1.12.2 placed the lava on both sides (the impact method had no side check), so the client world got lava the server
 * then overwrote. It is server only now ({@link MissileBase} calls the impacts on the server, and the checks are here as well).
 */
public class AmmoNapalmShot extends Ammo {

    public AmmoNapalmShot(int weight) {
        super("ammo_napalm_shot_" + weight);
        this.ammoWeight = weight;
        this.entityDamage = weight;
        this.vehicleDamage = weight;
        float scaleFactor = weight + 45.f;
        this.renderScale = (weight / scaleFactor) * 2;
        this.configName = "napalm_shot_" + weight;
        this.modelTexture = ammoTexture("ammo_stone_shot");
        this.isFlaming = true;
    }

    @Override
    public void onImpactWorld(Level level, float x, float y, float z, MissileBase missile, BlockHitResult hit) {
        if (!level.isClientSide) {
            placeLava(level, x, y, z, missile);
        }
    }

    @Override
    public void onImpactEntity(Level level, Entity entity, float x, float y, float z, MissileBase missile) {
        if (!level.isClientSide) {
            entity.hurt(MissileDamage.causeEntityMissileDamage(missile, true, false), this.getEntityDamage());
            entity.igniteForSeconds(3);
            placeLava(level, x, (float) entity.getY(), z, missile);
        }
    }

    private void placeLava(Level level, float x, float y, float z, MissileBase missile) {
        int bx = Mth.floor(x);
        int by = Mth.floor(y);
        int bz = Mth.floor(z);
        setBlockToLava(level, bx, by, bz, 5);
        // the dominant horizontal direction of flight, as -1, 0 or 1 on one axis
        Vec3 motion = missile.getDeltaMovement();
        double dx = motion.x;
        double dz = motion.z;
        if (Math.abs(dx) > Math.abs(dz)) {
            dz = 0;
        } else {
            dx = 0;
        }
        int ix = (int) Math.signum(dx);
        int iz = (int) Math.signum(dz);
        if (ammoWeight >= 15) { // set the 'forward' block to lava as well
            setBlockToLava(level, bx + ix, by, bz + iz, 5);
        }
        if (ammoWeight >= 30) { // set the 'rear' block to lava as well
            setBlockToLava(level, bx - ix, by, bz - iz, 5);
        }
        if (ammoWeight >= 45) {
            if (ix == 0) { // have already done Z's
                setBlockToLava(level, bx + 1, by, bz, 5);
                setBlockToLava(level, bx - 1, by, bz, 5);
            } else {
                setBlockToLava(level, bx, by, bz + 1, 5);
                setBlockToLava(level, bx, by, bz - 1, 5);
            }
        }
    }
}
