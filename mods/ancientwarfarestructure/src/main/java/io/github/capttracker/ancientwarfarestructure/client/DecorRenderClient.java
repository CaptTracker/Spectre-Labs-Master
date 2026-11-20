/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the client registration half of structure/block/BlockCoinStack, BlockFirePit and
 * BlockTotemPart (registerClient). The blockstates and models are JSON now, so only the item predicates remain.
 */
package io.github.capttracker.ancientwarfarestructure.client;

import io.github.capttracker.ancientwarfarestructure.AncientWarfareStructure;
import io.github.capttracker.ancientwarfarestructure.client.render.decor.DecorItemProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only setup of the decorative blocks. Nothing outside the client package may reference this class. The
 * cutout render layer of fire pits and totem parts is set by {@code "render_type": "minecraft:cutout"} in their
 * models; none of these blocks needs a block entity renderer.
 */
@EventBusSubscriber(modid = AncientWarfareStructure.MOD_ID, value = Dist.CLIENT)
public final class DecorRenderClient {
    private DecorRenderClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(DecorItemProperties::register);
    }
}
