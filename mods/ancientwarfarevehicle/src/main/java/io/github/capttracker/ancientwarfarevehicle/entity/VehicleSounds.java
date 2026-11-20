/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the AWVehicleSounds references of the vehicle types and firing helpers.
 */
package io.github.capttracker.ancientwarfarevehicle.entity;

import io.github.capttracker.ancientwarfarevehicle.AncientWarfareVehicles;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;

/**
 * Plays the module's sounds by registry name ({@code ancientwarfarevehicle:<name>}, the names of the 1.12.2
 * {@code AWVehicleSounds}), so the vehicle code does not depend on the class that registers them. A sound that
 * is not registered is skipped.
 */
public final class VehicleSounds {
    public static final String BALLISTA_RELOAD = "ballista_reload";
    public static final String BALLISTA_LAUNCH = "ballista_launch";
    public static final String BATTERING_RAM_LAUNCH = "battering_ram_launch";
    public static final String BATTERING_RAM_HIT_WOOD = "battering_ram_hit_wood";
    public static final String BATTERING_RAM_HIT_IRON = "battering_ram_hit_iron";
    public static final String CATAPULT_LAUNCH = "catapult_launch";
    public static final String CATAPULT_RELOAD = "catapult_reload";
    public static final String GIANT_TREBUCHET_LAUNCH = "giant_trebuchet_launch";
    public static final String TREBUCHET_LAUNCH = "trebuchet_launch";
    public static final String TREBUCHET_RELOAD = "trebuchet_reload";

    private VehicleSounds() {}

    public static void play(Entity source, String soundName, float volume, float pitch) {
        BuiltInRegistries.SOUND_EVENT.getOptional(Ids.of(AncientWarfareVehicles.MOD_ID, soundName))
                .ifPresent(sound -> play(source, sound, volume, pitch));
    }

    public static void play(Entity source, SoundEvent sound, float volume, float pitch) {
        source.playSound(sound, volume, pitch);
    }
}
