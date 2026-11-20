/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the "blocking" item property that 1.12.2 vanilla (ItemShield) and the shield models of npc/item/ItemShield relied on.
 */
package io.github.capttracker.ancientwarfarenpc.client;

import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcs;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Registers the {@code minecraft:blocking} model property on the 16 shields. The vanilla game only registers it for the vanilla shield, and the
 * shield models ({@code models/item/*_shield.json}) switch to their {@code *_blocking} model while the property is 1.
 */
@EventBusSubscriber(modid = AncientWarfareNPC.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class NpcItemPropertiesClient {
    private static final ResourceLocation BLOCKING = ResourceLocation.withDefaultNamespace("blocking");

    private NpcItemPropertiesClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> AwNpcs.SHIELDS.values().forEach(shield ->
                ItemProperties.register(shield.get(), BLOCKING,
                        (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F)));
    }
}
