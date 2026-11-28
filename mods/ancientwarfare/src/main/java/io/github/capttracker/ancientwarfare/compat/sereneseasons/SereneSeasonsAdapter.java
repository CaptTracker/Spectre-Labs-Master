/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (third round, mod integrations): the Serene Seasons side of {@link SeasonCompat}. Loaded only when Serene Seasons is installed.
 */
package io.github.capttracker.ancientwarfare.compat.sereneseasons;

import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfare.compat.season.SeasonCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import sereneseasons.api.season.ISeasonState;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.init.ModConfig;
import sereneseasons.init.ModFertility;
import sereneseasons.init.ModTags;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Serene Seasons 10.x (NeoForge 1.21.1) through its API: {@link SeasonHelper#getSeasonState(Level)} for the season and the year, and
 * {@link ModFertility#isCropFertile} (the check its own crop growth uses: the season tags of the crops, the infertile and tropical biomes, the underground
 * level) plus a greenhouse glass block within 16 blocks above, which also lets a crop grow there. A call that fails switches the integration off for the
 * rest of the session (one warning in the log); the caller then sees "everything is fertile".
 */
public final class SereneSeasonsAdapter implements SeasonCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int GREENHOUSE_REACH = 16;

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public boolean isFertile(Level level, BlockPos pos, ResourceLocation block) {
        try {
            if (ModFertility.isCropFertile(block.toString(), level, pos)) {
                return true;
            }
            for (int i = 0; i < GREENHOUSE_REACH; i++) {
                if (level.getBlockState(pos.offset(0, i + 1, 0)).is(ModTags.Blocks.GREENHOUSE_GLASS)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            fail(t);
            return true;
        }
    }

    @Nullable
    @Override
    public Info info(Level level, BlockPos pos) {
        try {
            if (!ModConfig.seasons.isDimensionWhitelisted(level.dimension())) {
                return null;
            }
            ISeasonState state = SeasonHelper.getSeasonState(level);
            int yearDays = Math.max(1, state.getCycleDuration() / Math.max(1, state.getDayDuration()));
            if (SeasonHelper.usesTropicalSeasons(level.getBiome(pos))) {
                String sub = state.getTropicalSeason().name().toLowerCase(Locale.ROOT);
                return new Info(sub.endsWith("dry") ? "dry" : "wet", sub, true, state.getDay(), yearDays);
            }
            return new Info(state.getSeason().name().toLowerCase(Locale.ROOT), state.getSubSeason().name().toLowerCase(Locale.ROOT), false, state.getDay(), yearDays);
        } catch (Throwable t) {
            fail(t);
            return null;
        }
    }

    @Override
    public long yearTicks(Level level) {
        try {
            return SeasonHelper.getSeasonState(level).getCycleDuration();
        } catch (Throwable t) {
            fail(t);
            return 0L;
        }
    }

    private static void fail(Throwable t) {
        LOGGER.warn("Serene Seasons could not be read; the integration is switched off for this session", t);
        SeasonCompat.set(NONE);
    }
}
