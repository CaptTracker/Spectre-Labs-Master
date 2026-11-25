/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/helpers/VehicleFiringVarsHelper.
 */
package io.github.capttracker.ancientwarfarevehicle.helpers;

import io.github.capttracker.ancientwarfarevehicle.entity.IVehicleCrew;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBridge;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * The per-type firing animation and launch state machine. {@link #getVar1()} to {@link #getVar8()} are the
 * animation values the renderer reads.
 */
public abstract class VehicleFiringVarsHelper implements INBTSerializable<CompoundTag> {
    protected final VehicleBase vehicle;

    public VehicleFiringVarsHelper(VehicleBase vehicle) {
        this.vehicle = vehicle;
    }

    /** Called on every tick that the vehicle is 'firing' to update the firing animation and to start launching when the animation has reached the launch point. */
    public abstract void onFiringUpdate();

    /** Called every tick after the vehicle has fired, until the reload timer is complete, to update animations. */
    public abstract void onReloadUpdate();

    /** Called every tick after startLaunching() is called, until setFinishedLaunching() is called. */
    public abstract void onLaunchingUpdate();

    public abstract void onReloadingFinished();

    public void onTick() {
    }

    /** A player used the vehicle: mount it, open its inventory when sneaking, or make an NPC rider get off. */
    public boolean interact(Player player) {
        if (player.level().isClientSide) {
            return true;
        }
        if (!player.isShiftKeyDown() && !vehicle.hasPassenger(player) && vehicle.hasFreeSeatFor(player)) {
            vehicle.boardAs(player, null);
            return true;
        } else if (player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer serverPlayer) {
                VehicleBridge.openInventory(serverPlayer, vehicle);
            }
        } else if (vehicle.isVehicle()) {
            // no seat for the player: an NPC crew member in the driver seat is made to get off
            Entity rider = vehicle.getControllingPassenger();
            if (rider instanceof IVehicleCrew) {
                rider.stopRiding();
            }
        }
        return true;
    }

    public abstract float getVar1();

    public abstract float getVar2();

    public abstract float getVar3();

    public abstract float getVar4();

    public abstract float getVar5();

    public abstract float getVar6();

    public abstract float getVar7();

    public abstract float getVar8();
}
