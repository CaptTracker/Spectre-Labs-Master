package io.github.capttracker.lib;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Shared library loaded by every mod in this repo.
 * Put code here once it is needed by two or more mods.
 */
@Mod(SpectreLib.MOD_ID)
public final class SpectreLib {
    public static final String MOD_ID = "spectrelib";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SpectreLib(IEventBus modBus, ModContainer container) {
        LOGGER.debug("{} loaded", MOD_ID);
    }
}
