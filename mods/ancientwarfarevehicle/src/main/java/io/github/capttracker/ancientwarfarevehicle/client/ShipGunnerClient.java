/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): the controls of a ship's gunner.
 */
package io.github.capttracker.ancientwarfarevehicle.client;

import io.github.capttracker.ancientwarfare.util.Trig;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipHardpoint;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipSpec;
import io.github.capttracker.ancientwarfarevehicle.network.VehicleEntityPayloads;
import io.github.capttracker.ancientwarfarevehicle.network.VehicleNetwork;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.phys.Vec3;

/**
 * The gunner of a ship (a rider of a GUNNER seat) aims the turret mount of the seat with his camera, whatever the mouse aim setting is: every tick the mount is turned
 * (within its arc) towards the point he looks at, and the elevation is the one that makes the shot land there (or the camera's own, when he looks at the sky). The
 * wish is sent to the server when it changed; the fire key fires the mount.
 */
final class ShipGunnerClient {
    private static float lastYaw = Float.NaN;
    private static float lastPitch = Float.NaN;
    private static int lastIndex = -1;

    private ShipGunnerClient() {}

    static void tick(LocalPlayer player, VehicleBase vehicle) {
        int index = vehicle.ship.hardpointOf(player);
        if (index < 0) {
            lastIndex = -1;
            return;
        }
        ShipHardpoint hardpoint = vehicle.ship.hardpoint(index);
        ShipSpec.Hardpoint mount = hardpoint.spec;
        Vec3 origin = vehicle.ship.toWorld(mount.forward(), mount.horizontal(), mount.vertical());
        Vec3 target = VehicleInputClient.getPlayerLookTargetClient(player, vehicle);
        float worldYaw;
        float pitch;
        if (target != null) {
            float dx = (float) (target.x - origin.x);
            float dy = (float) (target.y - origin.y);
            float dz = (float) (target.z - origin.z);
            worldYaw = Trig.toDegrees((float) Math.atan2(-dx, -dz));
            pitch = player.getXRot() * -1.0F;
            float speed = vehicle.firingHelper.getAdjustedMaxMissileVelocity();
            Tuple<Float, Float> angles = Trig.getLaunchAngleToHit(dx, dy, dz, speed);
            if (!angles.getA().isNaN() && !angles.getB().isNaN()) {
                if (Trig.isAngleBetween(angles.getB(), mount.pitchMin(), mount.pitchMax())) {
                    pitch = angles.getB();
                } else if (Trig.isAngleBetween(angles.getA(), mount.pitchMin(), mount.pitchMax())) {
                    pitch = angles.getA();
                }
            }
        } else {
            worldYaw = 180.0F - player.getYRot();
            pitch = -player.getXRot();
        }
        float yaw = hardpoint.clampYaw(Mth.wrapDegrees(worldYaw - vehicle.getYRot()));
        pitch = hardpoint.clampPitch(pitch);
        if (lastIndex != index || Float.isNaN(lastYaw) || Math.abs(Mth.wrapDegrees(yaw - lastYaw)) > 0.4F || Math.abs(pitch - lastPitch) > 0.4F) {
            lastIndex = index;
            lastYaw = yaw;
            lastPitch = pitch;
            hardpoint.setClientDestination(yaw, pitch);
            VehicleNetwork.sendToServer(new VehicleEntityPayloads.HardpointAim(vehicle.getId(), index, yaw, pitch));
        }
    }

    static void fire(LocalPlayer player, VehicleBase vehicle) {
        int index = vehicle.ship.hardpointOf(player);
        if (index < 0) {
            player.displayClientMessage(Component.translatable("ancientwarfarevehicle.ship.no_turret"), true);
            return;
        }
        if (vehicle.ammoHelper.getCurrentAmmoCount() <= 0) {
            player.displayClientMessage(Component.translatable("gui.ancientwarfarevehicles.ammo.no_ammo"), true);
            return;
        }
        VehicleNetwork.sendToServer(new VehicleEntityPayloads.HardpointFire(vehicle.getId(), index));
    }
}
