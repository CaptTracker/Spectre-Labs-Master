/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/proxy/ClientProxy (key binding and registry parser setup) and
 * core/network/PacketManualReload.
 */
package io.github.capttracker.ancientwarfare.client;

import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.ancientwarfare.client.input.InputHandler;
import io.github.capttracker.ancientwarfare.network.CoreMiscPayloads;
import io.github.capttracker.ancientwarfare.registry.RegistryLoader;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * Client-only setup of the core utilities: the alt-key mappings, the client registry data (manual text)
 * reload listener and the handlers of the server-to-client payloads. Nothing outside the client package may
 * reference this class. The key and scroll handling itself is {@link InputHandler}.
 *
 * <p>The 1.12.2 client proxy also forced a 512x288 window in debug mode (LWJGL 2 {@code Display}), which is
 * not ported.
 */
@EventBusSubscriber(modid = AncientWarfareCore.MOD_ID, value = Dist.CLIENT)
public final class CoreMiscClient {
    private CoreMiscClient() {}

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        InputHandler.registerKeyMappings(event);
    }

    /** F3+T reloads the client registry data (this replaces the {@code /awutils reloadmanual} dev loop). */
    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(RegistryLoader.CLIENT);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        CoreMiscPayloads.setClientHandler(new CoreMiscPayloads.ClientHandler() {
            @Override
            public void reloadManual() {
                // The "manual_content" parser is registered by the manual screen slice with RegistryLoader.registerClientParser.
                RegistryLoader.CLIENT.reload(Minecraft.getInstance().getResourceManager(), "manual_content");
            }

            @Override
            public void copyToClipboard(String text) {
                Minecraft.getInstance().keyboardHandler.setClipboard(text);
            }
        });
    }
}
