/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): the reading of the wind that ships and the HUD use.
 */
package io.github.capttracker.ancientwarfarevehicle.wind;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * The wind as ships and the HUD read it: on the server the saved {@link WindData}, on the client the last values the server sent (extrapolated by the
 * drift, so the HUD moves smoothly between the packets). The numbers are plain statics; nothing here touches client classes.
 */
public final class Wind {
    private static volatile float clientDirection = 0.F;
    private static volatile float clientStrength = WindData.MEAN_STRENGTH;
    private static volatile float clientTurn = 0.F;
    private static volatile long clientStamp = 0L;

    private Wind() {}

    /** CLIENT: the server sent the wind. */
    public static void setClient(float direction, float strength, float turn, long gameTime) {
        clientDirection = direction;
        clientStrength = strength;
        clientTurn = turn;
        clientStamp = gameTime;
    }

    /** The direction the wind blows towards at this moment, as a vehicle yaw in degrees (0 to 360). */
    public static float direction(Level level) {
        if (level instanceof ServerLevel server) {
            return WindData.get(server.getServer()).direction();
        }
        float age = Math.max(0L, level.getGameTime() - clientStamp);
        return Mth.positiveModulo(clientDirection + clientTurn * age, 360.0F);
    }

    /** The strength, {@link WindData#MIN_STRENGTH} to 1. */
    public static float strength(Level level) {
        if (level instanceof ServerLevel server) {
            return WindData.get(server.getServer()).strength();
        }
        return clientStrength;
    }

    /**
     * How well a square rigged ship with this heading (a vehicle yaw) uses the wind, 0.15 to 1: all of it with the wind from astern, a third on a beam
     * reach and not much into the wind.
     */
    public static float sailEfficiency(float shipYaw, float windDirection) {
        float angle = Mth.wrapDegrees(shipYaw - windDirection);
        float cos = (float) Math.cos(Math.toRadians(angle));
        return 0.15F + 0.85F * ((1.0F + cos) / 2.0F);
    }
}
