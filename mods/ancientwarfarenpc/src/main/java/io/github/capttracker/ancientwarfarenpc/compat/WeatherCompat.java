/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (third round, mod integrations): severe weather of other mods that the settlements shelter from (Weather2).
 */
package io.github.capttracker.ancientwarfarenpc.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** What the settlements ask about severe weather. The default senses none. Server side only. */
public interface WeatherCompat {
    WeatherCompat NONE = new WeatherCompat() {
        @Override
        public Sensed sense(ServerLevel level, BlockPos pos, double distance) {
            return null;
        }
    };

    /** The kinds of severe weather, in the order of how bad they are; {@code key} is the suffix of the lang keys of the warning. */
    enum Kind {
        STORM("storm"),
        SANDSTORM("sandstorm"),
        TORNADO("tornado");

        private final String key;

        Kind(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    /** One storm that was sensed: its id in the other mod (the same storm keeps it), what it is, where its centre is, and the distance from the asked position to the edge of it (0 or less: the position is under it). */
    record Sensed(long id, Kind kind, Vec3 pos, double distance) {}

    /**
     * The worst severe weather whose edge is within {@code distance} blocks (horizontally) of {@code pos}, or null for none (and when the mod fails: an
     * implementation that throws switches itself off and returns null from then on).
     */
    @Nullable
    Sensed sense(ServerLevel level, BlockPos pos, double distance);
}
