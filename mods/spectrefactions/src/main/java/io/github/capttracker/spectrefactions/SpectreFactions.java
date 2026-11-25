package io.github.capttracker.spectrefactions;

import com.mojang.logging.LogUtils;
import io.github.capttracker.spectrefactions.client.FactionsClient;
import io.github.capttracker.spectrefactions.config.FactionsConfig;
import io.github.capttracker.spectrefactions.event.FactionsEvents;
import io.github.capttracker.spectrefactions.event.ReputationHandler;
import io.github.capttracker.spectrefactions.network.FactionsNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Spectre Factions: factions, reputation and diplomacy, plus per-faction NPC guard behaviour.
 * Nothing is registered in a game registry, so there is no ModRegistrar here.
 */
@Mod(SpectreFactions.MOD_ID)
public final class SpectreFactions {
    public static final String MOD_ID = "spectrefactions";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SpectreFactions(IEventBus modBus, ModContainer container) {
        FactionsConfig.register(modBus, container);
        modBus.addListener(FactionsNetwork::register);
        NeoForge.EVENT_BUS.register(FactionsEvents.class);
        NeoForge.EVENT_BUS.register(ReputationHandler.class);
        if (FMLEnvironment.dist.isClient()) {
            FactionsClient.init(modBus);
        }
    }
}
