/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/VehicleVarHelpers/DummyVehicleHelper (also replaces the
 * ChestCartVarHelper stub of VehicleTypeChestCart, which did nothing either).
 */
package io.github.capttracker.ancientwarfarevehicle.entity.varhelpers;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleFiringVarsHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** The firing state of a vehicle that never fires (chest cart, transport boat) or has not been given its type yet. */
public class DummyVehicleHelper extends VehicleFiringVarsHelper {
    public DummyVehicleHelper(VehicleBase vehicle) {
        super(vehicle);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return new CompoundTag();
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
    }

    @Override
    public void onFiringUpdate() {
    }

    @Override
    public void onReloadUpdate() {
    }

    @Override
    public void onLaunchingUpdate() {
    }

    @Override
    public void onReloadingFinished() {
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
