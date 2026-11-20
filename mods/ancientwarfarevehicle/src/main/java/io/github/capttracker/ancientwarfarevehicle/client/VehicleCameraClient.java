/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): the chase camera of a rider.
 */
package io.github.capttracker.ancientwarfarevehicle.client;

import io.github.capttracker.ancientwarfarevehicle.AncientWarfareVehicles;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * The chase camera of the vehicle the local player rides. The third person view (F5 still cycles first person, behind and in front) sits behind and above
 * the vehicle at the distance of its type ({@code IVehicleType#getCameraDistance}) times the zoom, and the distance eases towards that, so zooming and
 * mounting do not jump. The mouse wheel zooms while riding in third person (it still changes the hotbar slot in first person). When the player mounts
 * in third person the view is tilted down a little, so the camera starts above the vehicle.
 *
 * <p>The turning of the camera follows the vehicle already: its yaw change is added to the rider's view every tick by the vehicle (classic steering), or the vehicle follows the
 * camera (mouse steering, see {@link VehicleInputClient}).
 */
@EventBusSubscriber(modid = AncientWarfareVehicles.MOD_ID, value = Dist.CLIENT)
public final class VehicleCameraClient {
    private static final float MIN_ZOOM = 0.5F;
    private static final float MAX_ZOOM = 3.0F;
    private static final float ZOOM_STEP = 0.15F;
    private static final float EASE = 0.12F;
    /** The pitch the view is tilted to when the player mounts (looking down at the vehicle from above). */
    private static final float MOUNT_PITCH = 14.0F;

    private static float zoom = 1.0F;
    private static float distance = Float.NaN;
    private static boolean wasRiding = false;

    private VehicleCameraClient() {}

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && mc.player != null && mc.player.getVehicle() instanceof VehicleBase && !mc.options.getCameraType().isFirstPerson()) {
            zoom = Mth.clamp(zoom - (float) Math.signum(event.getScrollDeltaY()) * ZOOM_STEP, MIN_ZOOM, MAX_ZOOM);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.player != null && event.getCamera().getEntity() == mc.player && mc.player.getVehicle() instanceof VehicleBase vehicle)) {
            distance = Float.NaN;
            return;
        }
        float target = vehicle.vehicleType.getCameraDistance() * zoom;
        distance = Float.isNaN(distance) ? target : distance + (target - distance) * EASE;
        event.setDistance(distance);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        LocalPlayer player = Minecraft.getInstance().player;
        boolean riding = player != null && player.getVehicle() instanceof VehicleBase;
        if (riding && !wasRiding && !Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            player.setXRot(Math.max(player.getXRot(), MOUNT_PITCH));
        }
        wasRiding = riding;
    }
}
