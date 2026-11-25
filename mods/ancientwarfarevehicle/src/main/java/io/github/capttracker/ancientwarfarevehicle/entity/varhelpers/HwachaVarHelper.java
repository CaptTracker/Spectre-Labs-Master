/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeHwacha.HwachaFiringVarsHelper.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.varhelpers;

import io.github.capttracker.ancientwarfare.util.Trig;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleFiringVarsHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/** Hwacha: starts launching at once and spawns one rocket every 5 ticks from a 9 by 4 grid (36 per salvo). */
public class HwachaVarHelper extends VehicleFiringVarsHelper {
    private static final int ROCKETS_PER_SALVO = 36;

    private int missileFired = 0;
    private int delayTick = 0;

    private float missileOffsetX;
    private float missileOffsetY;
    private float missileOffsetZ;

    public HwachaVarHelper(VehicleBase vehicle) {
        super(vehicle);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        // The original wrote an empty compound, so a salvo in progress was lost on save. Obvious local fix.
        CompoundTag tag = new CompoundTag();
        tag.putInt("mF", missileFired);
        tag.putInt("dT", delayTick);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        missileFired = tag.getInt("mF");
        delayTick = tag.getInt("dT");
    }

    @Override
    public void onFiringUpdate() {
        vehicle.firingHelper.startLaunching();
    }

    @Override
    public void onReloadUpdate() {
    }

    @Override
    public void onLaunchingUpdate() {
        delayTick++;
        if (delayTick >= 5) {
            delayTick = 0;
            calcMissileOffset(missileFired);
            if (!vehicle.level().isClientSide && vehicle.ammoHelper.getCurrentAmmoCount() > 0) {
                vehicle.playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0F, 0.5F);
            }
            vehicle.firingHelper.spawnMissile(missileOffsetX, missileOffsetY, missileOffsetZ);
            this.missileFired++;
            if (missileFired >= ROCKETS_PER_SALVO) {
                vehicle.firingHelper.setFinishedLaunching();
            }
        }
    }

    private void calcMissileOffset(int missileNum) {
        int currentRow = missileNum / 9;
        int currentCol = missileNum % 9;
        float targetX = ((float) currentCol) * 0.0625f * 2.f;
        targetX -= 8.f * 0.0625f;
        float targetY = ((float) currentRow) * 0.0625f * 2.f;
        float targetZ = 0.f;
        float targetAngle = 0.f + vehicle.getYRot();
        float len = Mth.sqrt(targetX * targetX + targetZ * targetZ);
        missileOffsetX = Trig.cosDegrees(targetAngle) * len;
        if (targetX < 0) {
            missileOffsetX *= -1;
        }
        missileOffsetZ = -Trig.sinDegrees(targetAngle) * len;
        missileOffsetY = Trig.cosDegrees(vehicle.localTurretPitch) * targetY;
        missileOffsetZ += Trig.sinDegrees(vehicle.localTurretPitch) * targetY;
    }

    @Override
    public void onReloadingFinished() {
        this.missileFired = 0;
        this.delayTick = 0;
    }

    @Override
    public float getVar1() {
        return 0;
    }

    @Override
    public float getVar2() {
        return 0;
    }

    @Override
    public float getVar3() {
        return 0;
    }

    @Override
    public float getVar4() {
        return 0;
    }

    @Override
    public float getVar5() {
        return 0;
    }

    @Override
    public float getVar6() {
        return 0;
    }

    @Override
    public float getVar7() {
        return 0;
    }

    @Override
    public float getVar8() {
        return 0;
    }
}
