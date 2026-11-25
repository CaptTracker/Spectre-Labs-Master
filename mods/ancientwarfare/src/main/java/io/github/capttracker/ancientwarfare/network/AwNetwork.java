/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Replaces Ancient Warfare 2's core/network/NetworkHandler (one FMLEventChannel with byte packet ids).
 */
package io.github.capttracker.ancientwarfare.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class AwNetwork {
    /** Bump when a payload's wire format changes. */
    private static final String VERSION = "1";

    private AwNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playBidirectional(MenuDataPayload.TYPE, MenuDataPayload.CODEC, MenuDataPayload::handle);
        registrar.playToClient(ResearchSyncPayload.TYPE, ResearchSyncPayload.CODEC, ResearchSyncPayload::handle);
        registrar.playToServer(TransferRecipePayload.TYPE, TransferRecipePayload.CODEC, TransferRecipePayload::handle);
        CoreMiscPayloads.registerPayloads(registrar);
    }
}
