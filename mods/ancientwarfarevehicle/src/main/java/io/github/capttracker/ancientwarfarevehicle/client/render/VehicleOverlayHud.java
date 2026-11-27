/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/render/RenderOverlay.
 */
package io.github.capttracker.ancientwarfarevehicle.client.render;

import io.github.capttracker.ancientwarfare.util.Trig;
import io.github.capttracker.ancientwarfarevehicle.config.AWVehicleStatics;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleMovementType;
import io.github.capttracker.ancientwarfarevehicle.item.ItemAmmo;
import io.github.capttracker.ancientwarfarevehicle.missiles.IAmmo;
import io.github.capttracker.ancientwarfarevehicle.registry.AmmoRegistry;
import java.text.DecimalFormat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * The text HUD shown while riding a vehicle: range, pitch, yaw, launch velocity, ammo name and count (air vehicles: throttle, pitch,
 * climb rate, elevation), plus speed and yaw rate when the advanced overlay is on. A GUI layer instead of the 1.12.2
 * {@code RenderTickEvent}; it is skipped while a screen is open, as before.
 *
 * <p>The strings were hard coded English in 1.12.2 and are lang keys now ({@code ancientwarfarevehicle.overlay.*}).
 */
public final class VehicleOverlayHud implements LayeredDraw.Layer {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int RED = 0xFFFF0000;
    private static final int X = 10;
    private static final DecimalFormat ONE_DECIMAL = new DecimalFormat("0.0");

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!AWVehicleStatics.clientSettings.renderOverlay) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || mc.options.hideGui || !(mc.player.getVehicle() instanceof VehicleBase vehicle)) {
            return;
        }
        drawVehicleOverlay(graphics, mc, vehicle);
    }

    private static void drawVehicleOverlay(GuiGraphics graphics, Minecraft mc, VehicleBase vehicle) {
        VehicleMovementType movement = vehicle.vehicleType.getMovementType();
        Vec3 motion = vehicle.getDeltaMovement();
        if (movement == VehicleMovementType.AIR1 || movement == VehicleMovementType.AIR2) {
            line(graphics, mc, "throttle", ONE_DECIMAL.format(vehicle.moveHelper.throttle), 10, WHITE);
            line(graphics, mc, "pitch", ONE_DECIMAL.format(vehicle.getXRot()), 20, WHITE);
            line(graphics, mc, "climb_rate", ONE_DECIMAL.format(motion.y * 20), 30, WHITE);
            line(graphics, mc, "elevation", ONE_DECIMAL.format(vehicle.getY()), 40, WHITE);
        } else if (vehicle.vehicleType.isPushed()) {
            drawSiegeOverlay(graphics, mc, vehicle);
        } else if (vehicle.ship == null) {
            line(graphics, mc, "range", ONE_DECIMAL.format(vehicle.firingHelper.clientHitRange), 10, WHITE);
            line(graphics, mc, "turret_pitch", ONE_DECIMAL.format(vehicle.firingHelper.clientTurretPitch), 20, WHITE);
            line(graphics, mc, "turret_yaw", ONE_DECIMAL.format(vehicle.firingHelper.clientTurretYaw), 30, WHITE);
            line(graphics, mc, "launch_velocity", ONE_DECIMAL.format(vehicle.firingHelper.clientLaunchSpeed), 40, WHITE);
        }

        IAmmo ammo = vehicle.ammoHelper.getCurrentAmmoType();
        if ((vehicle.ship != null || vehicle.vehicleType.isPushed()) && vehicle.vehicleType.getValidAmmoTypes().isEmpty()) {
            // a ship with no weapon: nothing about ammo (the ship HUD shows the rest)
        } else if (ammo != null) {
            int count = vehicle.ammoHelper.getCurrentAmmoCount();
            int color = count > 0 ? WHITE : RED;
            ItemAmmo item = AmmoRegistry.getItemForAmmo(ammo);
            Component name = item != null ? item.getDescription() : Component.literal(ammo.getRegistryName().toString());
            graphics.drawString(mc.font, Component.translatable("ancientwarfarevehicle.overlay.ammo", name), X, 50, color);
            line(graphics, mc, "ammo_count", Integer.toString(count), 60, color);
        } else {
            graphics.drawString(mc.font, Component.translatable("ancientwarfarevehicle.overlay.no_ammo"), X, 50, RED);
        }

        if (AWVehicleStatics.clientSettings.renderAdvOverlay) {
            float velocity = Trig.getVelocity(motion.x, 0, motion.z);
            graphics.drawString(mc.font, Component.translatable("ancientwarfarevehicle.overlay.speed",
                    ONE_DECIMAL.format(velocity * 20.f), ONE_DECIMAL.format(vehicle.currentForwardSpeedMax * 20)), X, 70, WHITE);
            line(graphics, mc, "yaw_rate", ONE_DECIMAL.format(vehicle.moveHelper.getRotationSpeed() * 20.f), 80, WHITE);
        }
    }

    /** The siege works (rework phase 12): the pushers and the speed they give, and for a tower the state of the bridge and the keys. */
    private static void drawSiegeOverlay(GuiGraphics graphics, Minecraft mc, VehicleBase vehicle) {
        int full = vehicle.vehicleType.getPushersForFullSpeed();
        line(graphics, mc, "pushers", vehicle.getPushCount() + "/" + full, 10, vehicle.getPushCount() > 0 ? WHITE : RED);
        if (vehicle.siege != null) {
            float progress = vehicle.siege.bridgeProgress(mc.getTimer().getGameTimeDeltaPartialTick(false));
            String state = vehicle.siege.isBridgeFullyDown() ? "bridge_down" : progress > 0.001F || vehicle.isBridgeDown() ? "bridge_moving" : "bridge_up";
            graphics.drawString(mc.font, Component.translatable("ancientwarfarevehicle.overlay." + state), X, 20, WHITE);
            graphics.drawString(mc.font, Component.translatable("ancientwarfarevehicle.overlay.siege_keys"), X, 30, WHITE);
        }
    }

    private static void line(GuiGraphics graphics, Minecraft mc, String key, String value, int y, int color) {
        graphics.drawString(mc.font, Component.translatable("ancientwarfarevehicle.overlay." + key, value), X, y, color);
    }
}
