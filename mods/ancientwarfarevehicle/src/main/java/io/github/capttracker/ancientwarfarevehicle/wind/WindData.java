/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): the wind of the world.
 */
package io.github.capttracker.ancientwarfarevehicle.wind;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The wind: one direction and one strength for the whole world, drifting slowly. Saved with the overworld; the server ticks it ({@link WindEvents}) and sends it
 * to the players. The direction is the direction the wind blows towards, as a vehicle yaw ({@code forward = (-sin, -cos)}): a ship whose yaw equals it has the
 * wind from astern. The strength runs from {@link #MIN_STRENGTH} (a light breeze: sails never stand dead) to 1.
 *
 * <p>The drift is a random walk of the turning speed: it turns at most {@link #MAX_TURN} degrees a tick (a full circle takes at least 40 minutes), the strength
 * wanders round {@link #MEAN_STRENGTH}.
 */
public class WindData extends SavedData {
    public static final float MIN_STRENGTH = 0.25F;
    public static final float MAX_STRENGTH = 1.0F;
    public static final float MEAN_STRENGTH = 0.6F;
    private static final float MAX_TURN = 0.0075F;
    private static final float MAX_STRENGTH_CHANGE = 0.00012F;
    private static final String NAME = "ancientwarfarevehicle_wind";
    private static final SavedData.Factory<WindData> FACTORY = new SavedData.Factory<>(WindData::new, WindData::load);

    private float direction;
    private float strength = MEAN_STRENGTH;
    private float turn = 0.F;
    private float strengthChange = 0.F;
    private final RandomSource random = RandomSource.create();

    public WindData() {
        this.direction = random.nextFloat() * 360.0F;
    }

    public static WindData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /** The direction the wind blows towards (a vehicle yaw, 0 to 360), and its strength. */
    public float direction() {
        return direction;
    }

    public float strength() {
        return strength;
    }

    public float turn() {
        return turn;
    }

    /** One tick of the drift. */
    public void tick() {
        turn = Mth.clamp(turn + (random.nextFloat() - 0.5F) * 0.0004F, -MAX_TURN, MAX_TURN);
        direction += turn;
        if (direction >= 360.0F) {
            direction -= 360.0F;
        } else if (direction < 0.0F) {
            direction += 360.0F;
        }
        strengthChange = Mth.clamp(strengthChange + (random.nextFloat() - 0.5F) * 0.00002F + (MEAN_STRENGTH - strength) * 0.0000005F,
                -MAX_STRENGTH_CHANGE, MAX_STRENGTH_CHANGE);
        strength = Mth.clamp(strength + strengthChange, MIN_STRENGTH, MAX_STRENGTH);
        if (strength <= MIN_STRENGTH || strength >= MAX_STRENGTH) {
            strengthChange = 0.F;
        }
        // not marked dirty every tick: a minute's drift is not worth a write, the world saves it with the next change below
    }

    /** Called now and then so the changed wind is saved. */
    public void touch() {
        setDirty();
    }

    private static WindData load(CompoundTag tag, HolderLookup.Provider registries) {
        WindData data = new WindData();
        if (tag.contains("direction")) {
            data.direction = tag.getFloat("direction");
            data.strength = Mth.clamp(tag.getFloat("strength"), MIN_STRENGTH, MAX_STRENGTH);
            data.turn = tag.getFloat("turn");
            data.strengthChange = tag.getFloat("strengthChange");
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putFloat("direction", direction);
        tag.putFloat("strength", strength);
        tag.putFloat("turn", turn);
        tag.putFloat("strengthChange", strengthChange);
        return tag;
    }
}
