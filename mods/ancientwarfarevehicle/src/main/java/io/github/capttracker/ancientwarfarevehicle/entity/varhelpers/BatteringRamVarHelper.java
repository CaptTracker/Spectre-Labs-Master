/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/VehicleVarHelpers/BatteringRamVarHelper.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.varhelpers;

import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfarevehicle.entity.IGateEntity;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSettings;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSounds;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeBatteringRam;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleFiringVarsHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Battering ram: the log is pulled back to +30, swings forward 2 degrees per tick to -30, then damages everything
 * in the seven positions around the head (and breaks blocks, by config).
 */
public class BatteringRamVarHelper extends VehicleFiringVarsHelper {
    float logAngle = 0.f;
    float logSpeed = 0.f;

    public BatteringRamVarHelper(VehicleBase vehicle) {
        super(vehicle);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("lA", logAngle);
        tag.putFloat("lS", logSpeed);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        logAngle = tag.getFloat("lA");
        logSpeed = tag.getFloat("lS");
    }

    @Override
    public void onFiringUpdate() {
        if (logAngle >= 30) {
            vehicle.firingHelper.startLaunching();
            logSpeed = 0;
        } else {
            logAngle++;
            logSpeed = 1;
        }
    }

    @Override
    public void onReloadUpdate() {
        if (logAngle < 0) {
            logAngle++;
            logSpeed = 1;
        } else {
            logAngle = 0;
            logSpeed = 0;
        }
    }

    @Override
    public void onLaunchingUpdate() {
        if (logAngle <= -30) {
            vehicle.firingHelper.setFinishedLaunching();
            doDamageEffects();
            logSpeed = 0;
        } else {
            logAngle -= 2;
            logSpeed = -2;
        }
    }

    /** SERVER ONLY. */
    public void doDamageEffects() {
        if (vehicle.level().isClientSide) {
            return;
        }
        DamageSource batteringDamage = vehicle.createBatteringDamage();
        for (BlockPos pos : VehicleTypeBatteringRam.getEffectedPositions(vehicle)) {
            if (pos == null) {
                continue;
            }
            AABB bb = new AABB(pos);
            List<Entity> hitEntities = vehicle.level().getEntities(vehicle, bb);
            boolean firstGateBlock = true; // only used if a gate was hit
            for (Entity ent : hitEntities) {
                ent.hurt(batteringDamage, 5 + vehicle.vehicleMaterialLevel);
                if (ent instanceof IGateEntity gate && firstGateBlock) {
                    // makes the sound play only once, the ram can hit the gate entity multiple times at once
                    String gateTypeName = gate.getGateVariantName().toLowerCase();
                    if (gateTypeName.contains("wood")) {
                        VehicleSounds.play(ent, VehicleSounds.BATTERING_RAM_HIT_WOOD, 3, 1);
                        firstGateBlock = false;
                    } else if (gateTypeName.contains("iron")) {
                        VehicleSounds.play(ent, VehicleSounds.BATTERING_RAM_HIT_IRON, 3, 1);
                        firstGateBlock = false;
                    }
                }
            }
            if (VehicleSettings.batteringRamBreaksBlocks) {
                // The original divided the integer percentage by the integer 100 (so the chance was always 0 below 100).
                // Obvious local fix: divide as a double, which is what the config option documents.
                if (vehicle.getRandom().nextDouble() < VehicleSettings.batteringRamBlockBreakPercentageChance / 100.0) {
                    BlockTools.breakBlockAndDrop(vehicle.level(), pos);
                }
            }
        }
    }

    @Override
    public void onReloadingFinished() {
        logAngle = 0;
        logSpeed = 0;
    }

    @Override
    public float getVar1() {
        return logAngle;
    }

    @Override
    public float getVar2() {
        return logSpeed;
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
