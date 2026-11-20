/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/input/VehicleCallback.
 */
package io.github.capttracker.ancientwarfarevehicle.client;

import io.github.capttracker.ancientwarfare.input.IInputCallback;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.function.Consumer;

/**
 * Key callback that runs an action on the vehicle the player rides. Core's key handler counts a press whatever the key's
 * conflict context says, so the callback checks that the player is driving (1.12.2 cast the riding entity without a check).
 */
public class VehicleCallback implements IInputCallback {
    private final Consumer<VehicleBase> callback;

    public VehicleCallback(Consumer<VehicleBase> callback) {
        this.callback = callback;
    }

    @Override
    public void onKeyPressed() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && VehicleKeyConflictContext.INSTANCE.isActive() && player.getVehicle() instanceof VehicleBase vehicle && vehicle.isWeaponCrew(player)) {
            callback.accept(vehicle);
        }
    }
}
