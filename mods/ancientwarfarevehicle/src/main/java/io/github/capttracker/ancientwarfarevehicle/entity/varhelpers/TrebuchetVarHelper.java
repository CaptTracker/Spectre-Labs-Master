/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeTrebuchet.TrebuchetVarHelper (the giant
 * trebuchet had a line-for-line copy, TrebuchetLargeVarHelper, which is merged into this class).
 */
package io.github.capttracker.ancientwarfarevehicle.entity.varhelpers;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleFiringVarsHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** Trebuchet: the arm swings from -27 to 90 degrees in 20 ticks, launches by weight count, and returns over the reload time. */
public class TrebuchetVarHelper extends VehicleFiringVarsHelper {
    private static final float STRING_FACTOR = 1.3162316f;

    private float armAngle = -27.f;
    private float armSpeed = 0.f;
    private float stringAngle = -64.f;
    private float stringSpeed = 0.f;

    public TrebuchetVarHelper(VehicleBase vehicle) {
        super(vehicle);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("aA", armAngle);
        tag.putFloat("aS", armSpeed);
        tag.putFloat("sA", stringAngle);
        tag.putFloat("sS", stringSpeed);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        armAngle = tag.getFloat("aA");
        armSpeed = tag.getFloat("aS");
        stringAngle = tag.getFloat("sA");
        stringSpeed = tag.getFloat("sS");
    }

    @Override
    public void onFiringUpdate() {
        float increment = (90.f + 27.f) / 20.f;
        armAngle += increment;
        armSpeed = increment;
        stringAngle += STRING_FACTOR * increment;
        stringSpeed = STRING_FACTOR * increment;
        if (armAngle >= 90) {
            armSpeed = 0;
            armAngle = 90.f;
            stringAngle = 90.f;
            stringSpeed = 0.f;
            vehicle.firingHelper.startLaunching();
        }
    }

    @Override
    public void onReloadUpdate() {
        float increment = (90.f + 27.f) / (float) vehicle.currentReloadTicks;
        if (armAngle > -27) {
            armAngle -= increment;
            armSpeed = -increment;
            stringAngle -= STRING_FACTOR * increment;
            stringSpeed = -STRING_FACTOR * increment;
        } else {
            armAngle = -27;
            armSpeed = 0;
            stringAngle = -64.f;
            stringSpeed = 0.f;
        }
    }

    @Override
    public void onLaunchingUpdate() {
        vehicle.firingHelper.spawnMissilesByWeightCount(0, 0, 0);
        vehicle.firingHelper.setFinishedLaunching();
    }

    @Override
    public void onReloadingFinished() {
        armAngle = -27;
        armSpeed = 0;
        stringAngle = -64.f;
        stringSpeed = 0.f;
    }

    @Override
    public float getVar1() {
        return armAngle;
    }

    @Override
    public float getVar2() {
        return armSpeed;
    }

    @Override
    public float getVar3() {
        return stringAngle;
    }

    @Override
    public float getVar4() {
        return stringSpeed;
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
