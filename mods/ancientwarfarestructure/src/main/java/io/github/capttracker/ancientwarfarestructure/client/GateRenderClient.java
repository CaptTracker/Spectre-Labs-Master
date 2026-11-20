/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the gate render registrations of structure/proxy/ClientProxyStructure (the invisible gate
 * entity renderer and the TESR of the gate proxy block, which BlockGateProxy#registerClient bound).
 */
package io.github.capttracker.ancientwarfarestructure.client;

import io.github.capttracker.ancientwarfarestructure.AncientWarfareStructure;
import io.github.capttracker.ancientwarfarestructure.client.render.gate.GateModelBasic;
import io.github.capttracker.ancientwarfarestructure.client.render.gate.GateModelBridge;
import io.github.capttracker.ancientwarfarestructure.client.render.gate.GateRenderer;
import io.github.capttracker.ancientwarfarestructure.init.AwGates;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-only setup of the gate rendering: the two model layers and the gate entity renderer. The proxy block is invisible
 * and needs no renderer. The outline of the gate spawner ({@code GateBoundsOverlay}) listens on the game bus by itself.
 * Nothing outside the client package may reference this class.
 */
@EventBusSubscriber(modid = AncientWarfareStructure.MOD_ID, value = Dist.CLIENT)
public final class GateRenderClient {
    private GateRenderClient() {}

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(GateModelBasic.LAYER, GateModelBasic::createLayer);
        event.registerLayerDefinition(GateModelBridge.LAYER, GateModelBridge::createLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AwGates.GATE.get(), GateRenderer::new);
    }
}
