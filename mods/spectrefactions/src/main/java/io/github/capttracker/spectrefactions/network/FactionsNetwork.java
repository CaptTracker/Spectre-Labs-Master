package io.github.capttracker.spectrefactions.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class FactionsNetwork {
    /** Bump when a payload's wire format changes. */
    private static final String VERSION = "1";

    private FactionsNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(ClientboundFactionsSnapshot.TYPE, ClientboundFactionsSnapshot.CODEC, ClientboundFactionsSnapshot::handle);
        registrar.playToClient(ClientboundOpenFactionScreen.TYPE, ClientboundOpenFactionScreen.CODEC, ClientboundOpenFactionScreen::handle);
        registrar.playToServer(ServerboundFactionAction.TYPE, ServerboundFactionAction.CODEC, ServerboundFactionAction::handle);
    }
}
