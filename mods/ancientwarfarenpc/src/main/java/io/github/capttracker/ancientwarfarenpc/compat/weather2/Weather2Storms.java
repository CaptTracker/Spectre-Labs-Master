/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (third round, mod integrations): Weather2.
 */
package io.github.capttracker.ancientwarfarenpc.compat.weather2;

import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfarenpc.compat.ModCompat;
import io.github.capttracker.ancientwarfarenpc.compat.WeatherCompat;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import weather2.ServerTickHandler;
import weather2.weathersystem.WeatherManagerServer;
import weather2.weathersystem.storm.StormObject;
import weather2.weathersystem.storm.WeatherObject;
import weather2.weathersystem.storm.WeatherObjectParticleStorm;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the storms of the server side weather system of Weather2 ({@code ServerTickHandler.getWeatherManagerFor}, {@code WeatherManager.getStormObjects}: the
 * {@code StormObject} clouds with their intensity stage and the {@code WeatherObjectParticleStorm} sandstorms). Weather2 has no stable API, so every call is
 * wrapped: the first error (a missing class or method after an update of the mod, an unexpected null) is logged once and switches the integration off for the
 * rest of the session.
 *
 * <p>How far a storm reaches: a tornado counts from its centre, a storm cloud from half its size (the cloud is much wider than the part that hails), a
 * sandstorm from half of its area. The distance asked for is measured from the edge.
 */
public final class Weather2Storms implements WeatherCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private boolean failed;

    @Override
    @Nullable
    public Sensed sense(ServerLevel level, BlockPos pos, double distance) {
        if (failed || !SettlementConfig.weather2Enabled) {
            return null;
        }
        try {
            WeatherManagerServer manager = ServerTickHandler.getWeatherManagerFor(level);
            if (manager == null) {
                return null;
            }
            Sensed worst = null;
            List<WeatherObject> storms = new ArrayList<>(manager.getStormObjects());
            for (WeatherObject object : storms) {
                if (object == null || object.isDead || object.pos == null) {
                    continue;
                }
                Kind kind = kindOf(object);
                if (kind == null) {
                    continue;
                }
                Vec3 centre = object.pos;
                double dx = centre.x - (pos.getX() + 0.5D);
                double dz = centre.z - (pos.getZ() + 0.5D);
                double edge = Math.sqrt(dx * dx + dz * dz) - radiusOf(object, kind);
                if (edge > distance) {
                    continue;
                }
                if (worst == null || kind.ordinal() > worst.kind().ordinal() || kind == worst.kind() && edge < worst.distance()) {
                    worst = new Sensed(object.ID, kind, centre, edge);
                }
            }
            return worst;
        } catch (Throwable t) {
            failed = true;
            ModCompat.WEATHER.disable();
            LOGGER.warn("Weather2 could not be read (an update of the mod changed its code?); the storm warnings of the settlements are off until the game is restarted", t);
            return null;
        }
    }

    /** What the object is for the warnings, or null for weather that is not severe. */
    @Nullable
    private static Kind kindOf(WeatherObject object) {
        if (object instanceof StormObject storm) {
            if (storm.isPet()) {
                return null; // the tornado pets of the mod's items
            }
            if (storm.isTornadoFormingOrGreater() || storm.isCycloneFormingOrGreater()) {
                return Kind.TORNADO;
            }
            return storm.levelCurIntensityStage >= SettlementConfig.weather2MinStage ? Kind.STORM : null;
        }
        if (object instanceof WeatherObjectParticleStorm sand && SettlementConfig.weather2Sandstorms
                && sand.getType() == WeatherObjectParticleStorm.StormType.SANDSTORM && sand.getIntensity() >= SettlementConfig.weather2SandstormMinIntensity) {
            return Kind.SANDSTORM;
        }
        return null;
    }

    private static double radiusOf(WeatherObject object, Kind kind) {
        return switch (kind) {
            case TORNADO -> 0.0D;
            case STORM -> object.size * 0.5D;
            case SANDSTORM -> object.getSize() * 0.5D;
        };
    }
}
