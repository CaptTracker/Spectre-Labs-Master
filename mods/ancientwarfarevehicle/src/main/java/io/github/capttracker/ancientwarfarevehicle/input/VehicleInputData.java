/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the input fields and setters of vehicle/network/PacketVehicleInput (and the
 * IVehicleMovementHandler.Impl updaters of vehicle/input). Rework phase 4: analog throttle and steer, and the camera aim.
 */
package io.github.capttracker.ancientwarfarevehicle.input;

/**
 * What the driver is asking for. The four bytes are -1, 0 or +1: forward (+1 forward, -1 reverse), turn (+1 right, -1 left),
 * power (+1 up, -1 down; power for catapults, pitch for ballistas) and rotation (+1 turret right, -1 turret left). Opposite keys
 * held together cancel, so the value is 0.
 *
 * <p>{@code throttle} and {@code steer} are the analog versions of forward and turn, -1..1 (steer +1 is right): the keys give a ramp, the
 * mouse steering gives any value in between. {@code aimYaw} and {@code aimPitch} are the camera angles of the rider in degrees (what the
 * mouse is pointing at). The server uses the analog values for the movement and keeps the camera angles for the crew and the HUD.
 *
 * <p>Common code on purpose: the client builds it from its key mappings, the payload carries it, the server clamps it.
 */
public record VehicleInputData(byte forward, byte turn, byte power, byte rotation, float throttle, float steer, float aimYaw, float aimPitch) {
    public static final VehicleInputData NONE = new VehicleInputData((byte) 0, (byte) 0, (byte) 0, (byte) 0, 0.f, 0.f, 0.f, 0.f);

    /** Digital input only: the analog values follow the bytes. */
    public VehicleInputData(byte forward, byte turn, byte power, byte rotation) {
        this(forward, turn, power, rotation, forward, turn, 0.f, 0.f);
    }

    /** The same input with every value limited to -1..1, for data that came from a client. */
    public VehicleInputData clamped() {
        return new VehicleInputData(clamp(forward), clamp(turn), clamp(power), clamp(rotation), clampF(throttle), clampF(steer), finite(aimYaw), finite(aimPitch));
    }

    private static byte clamp(byte value) {
        return (byte) Math.max(-1, Math.min(1, value));
    }

    private static float clampF(float value) {
        return Float.isNaN(value) ? 0.f : Math.max(-1.f, Math.min(1.f, value));
    }

    private static float finite(float value) {
        return Float.isNaN(value) || Float.isInfinite(value) ? 0.f : value;
    }

    /** True if the movement part (not the camera angles) differs enough from the other input to be worth sending. */
    public boolean movementDiffers(VehicleInputData other) {
        return forward != other.forward || turn != other.turn || power != other.power || rotation != other.rotation
                || Math.abs(throttle - other.throttle) > 0.01F || Math.abs(steer - other.steer) > 0.01F;
    }

    /** The input of two opposite keys: +1 if only the positive one is down, -1 if only the negative one is, else 0. */
    public static byte axis(boolean positiveDown, boolean negativeDown) {
        if (positiveDown == negativeDown) {
            return 0;
        }
        return (byte) (positiveDown ? 1 : -1);
    }
}
