/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): the wind and ship indicator.
 */
package io.github.capttracker.ancientwarfarevehicle.client.render;

import com.mojang.math.Axis;
import io.github.capttracker.ancientwarfarevehicle.config.AWVehicleStatics;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipHardpoint;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipHelper;
import io.github.capttracker.ancientwarfarevehicle.wind.Wind;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The indicator shown while the player is on a ship (riding it or standing on its deck): a compass whose arrow shows where the wind blows to, relative to the
 * ship (up is the bow, so an arrow that points up is a following wind), the wind strength in knots (strength 1 is 20 knots), the sail setting, the speed, the
 * condition of the hull and, for a gunner, the state of the turret mount. Skipped while a screen is open or the GUI is hidden.
 */
public final class ShipHud implements LayeredDraw.Layer {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int RED = 0xFFFF5555;
    private static final int YELLOW = 0xFFFFDD55;
    private static final int GREEN = 0xFF66DD66;
    private static final int PANEL = 0x80000000;
    private static final int RADIUS = 20;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!AWVehicleStatics.clientSettings.renderOverlay || mc.screen != null || mc.player == null || mc.options.hideGui) {
            return;
        }
        VehicleBase vehicle = ShipHelper.shipOf(mc.player);
        if (vehicle == null || vehicle.ship == null || !vehicle.ship.hasSpec()) {
            return;
        }
        int width = graphics.guiWidth();
        int left = width - 112;
        int top = 8;
        graphics.fill(left - 4, top - 4, width - 6, top + 2 * RADIUS + 66, PANEL);

        float direction = Wind.direction(vehicle.level());
        float strength = Wind.strength(vehicle.level());
        int cx = left + RADIUS + 2;
        int cy = top + RADIUS;
        drawCompass(graphics, cx, cy, Mth.wrapDegrees(vehicle.getYRot() - direction), strength);

        int textX = cx + RADIUS + 8;
        graphics.drawString(mc.font, Component.translatable("ancientwarfarevehicle.ship.wind", Math.round(strength * 20.0F)), textX, top + 2, WHITE);
        graphics.drawString(mc.font, Component.translatable("ancientwarfarevehicle.ship.heading", Math.floorMod(Math.round(-vehicle.getYRot()), 360)), textX, top + 14, WHITE);

        int y = top + 2 * RADIUS + 6;
        int sails = Math.round(vehicle.ship.trim() * 100.0F);
        graphics.drawString(mc.font, Component.translatable("ancientwarfarevehicle.ship.sails", sails), left, y, WHITE);
        float speed = Math.abs(vehicle.moveHelper.forwardMotion) * 20.0F;
        graphics.drawString(mc.font, Component.translatable("ancientwarfarevehicle.ship.speed", String.format("%.1f", speed)), left, y + 10, WHITE);
        float hull = vehicle.baseHealth <= 0 ? 0 : Mth.clamp(vehicle.getHealth() / vehicle.baseHealth, 0.0F, 1.0F);
        graphics.drawString(mc.font, Component.translatable("ancientwarfarevehicle.ship.hull", Math.round(hull * 100.0F)), left, y + 20, hull < 0.3F ? RED : (hull < 0.6F ? YELLOW : WHITE));
        if (vehicle.ship.isSinking()) {
            graphics.drawString(mc.font, Component.translatable("ancientwarfarevehicle.ship.sinking"), left, y + 30, RED);
        } else if (vehicle.ship.isAground()) {
            graphics.drawString(mc.font, Component.translatable("ancientwarfarevehicle.ship.aground"), left, y + 30, RED);
        } else {
            int index = vehicle.ship.hardpointOf(mc.player);
            if (index >= 0) {
                ShipHardpoint hardpoint = vehicle.ship.hardpoint(index);
                boolean ready = hardpoint.recoil <= 0.0F && vehicle.ammoHelper.getCurrentAmmoCount() > 0;
                graphics.drawString(mc.font, Component.translatable(ready ? "ancientwarfarevehicle.ship.turret_ready" : "ancientwarfarevehicle.ship.turret_wait", index + 1), left, y + 30,
                        ready ? GREEN : YELLOW);
            }
        }
    }

    /** A ring of dots with an arrow in it; up is the bow of the ship. */
    private static void drawCompass(GuiGraphics graphics, int cx, int cy, float arrowAngle, float strength) {
        for (int i = 0; i < 24; i++) {
            double a = Math.toRadians(i * 15.0D);
            int x = cx + (int) Math.round(Math.sin(a) * RADIUS);
            int y = cy - (int) Math.round(Math.cos(a) * RADIUS);
            graphics.fill(x, y, x + 1 + (i % 6 == 0 ? 1 : 0), y + 1 + (i % 6 == 0 ? 1 : 0), i == 0 ? YELLOW : 0xFFAAAAAA);
        }
        int length = 6 + Math.round(strength * 10.0F);
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(arrowAngle));
        graphics.fill(-1, -length, 1, length - 4, WHITE);
        graphics.fill(-3, -length + 3, 3, -length + 4, WHITE);
        graphics.fill(-2, -length + 2, 2, -length + 3, WHITE);
        graphics.fill(-1, -length + 1, 1, -length + 2, WHITE);
        graphics.pose().popPose();
    }
}
