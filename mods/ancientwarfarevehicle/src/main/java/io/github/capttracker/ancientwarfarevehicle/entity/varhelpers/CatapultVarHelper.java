/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/VehicleVarHelpers/CatapultVarHelper.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.varhelpers;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleFiringVarsHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** Catapult: the arm rises 4 degrees per tick to 70, launches by weight count, then lowers over the reload time while the crank turns. */
public class CatapultVarHelper extends VehicleFiringVarsHelper {
    public float armAngle = 0.f;
    public float armSpeed = 0.f;
    public float crankAngle = 0.f;
    public float crankSpeed = 0.f;

    public CatapultVarHelper(VehicleBase vehicle) {
        super(vehicle);
    }

    @Override
    public void onFiringUpdate() {
        float prevAngle = this.armAngle;
        this.armAngle += 80.f / 20;
        if (this.armAngle >= 70) {
            vehicle.firingHelper.startLaunching();
            this.armAngle = 70.f;
        }
        this.armSpeed = this.armAngle - prevAngle;
    }

    @Override
    public void onLaunchingUpdate() {
        vehicle.firingHelper.spawnMissilesByWeightCount(0, 0, 0);
        vehicle.firingHelper.setFinishedLaunching();
    }

    @Override
    public void onReloadUpdate() {
        float prevAngle = this.armAngle;
        this.armAngle -= 80 / (float) (vehicle.currentReloadTicks - 2);
        this.crankAngle += 4;
        this.crankSpeed = 4;
        this.armSpeed = this.armAngle - prevAngle;
        if (this.armAngle <= 0) {
            this.armAngle = 0;
            this.crankSpeed = 0;
            this.armSpeed = 0;
        }
    }

    @Override
    public void onReloadingFinished() {
        this.crankSpeed = 0;
        this.armSpeed = 0;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("cA", crankAngle);
        tag.putFloat("cS", crankSpeed);
        tag.putFloat("aA", armAngle);
        tag.putFloat("aS", armSpeed);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        this.crankAngle = tag.getFloat("cA");
        this.crankSpeed = tag.getFloat("cS");
        this.armAngle = tag.getFloat("aA");
        this.armSpeed = tag.getFloat("aS");
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
        return crankAngle;
    }

    @Override
    public float getVar4() {
        return crankSpeed;
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
