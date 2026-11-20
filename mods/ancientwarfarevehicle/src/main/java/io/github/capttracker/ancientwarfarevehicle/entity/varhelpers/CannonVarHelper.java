/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/entity/types/VehicleTypeCannon.CannonVarHelper.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.varhelpers;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleFiringVarsHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;

/** Cannon: a 10 tick fuse with smoke and sounds, then one missile; no reload animation. */
public class CannonVarHelper extends VehicleFiringVarsHelper {
    private int firingTicks = 0;

    public CannonVarHelper(VehicleBase vehicle) {
        super(vehicle);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("fT", firingTicks);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        // The original wrote the fuse ticks but never read them back (a fuse in progress was lost on save).
        // Obvious local fix: read them.
        firingTicks = tag.getInt("fT");
    }

    @Override
    public void onFiringUpdate() {
        if (firingTicks == 0 && !vehicle.level().isClientSide) {
            vehicle.playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.50F, .25F);
            vehicle.playSound(SoundEvents.TNT_PRIMED, 1.0F, 0.5F);
        }
        firingTicks++;
        if (vehicle.level().isClientSide) {
            // TODO(original): offset to the end of the barrel
            vehicle.level().addParticle(ParticleTypes.SMOKE, vehicle.getX(), vehicle.getY() + 1.2d, vehicle.getZ(), 0.0D, 0.05D, 0.0D);
        }
        if (firingTicks > 10) {
            if (!vehicle.level().isClientSide) {
                vehicle.playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.f, 1.f);
            }
            vehicle.firingHelper.startLaunching();
            firingTicks = 0;
        }
    }

    @Override
    public void onReloadUpdate() {
    }

    @Override
    public void onLaunchingUpdate() {
        vehicle.firingHelper.spawnMissile(0, 0, 0);
        // TODO(original): spawn particles for the explosion in the direction of missile flight at the end of the barrel
        vehicle.firingHelper.setFinishedLaunching();
    }

    @Override
    public void onReloadingFinished() {
        firingTicks = 0;
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
