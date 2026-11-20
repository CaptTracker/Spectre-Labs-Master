/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the render, skin and item colour registrations of npc/proxy/NpcClientProxy and npc/client/NPCItemColors.
 */
package io.github.capttracker.ancientwarfarenpc.client;

import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.client.render.NpcCommandOverlay;
import io.github.capttracker.ancientwarfarenpc.client.render.NpcRenderer;
import io.github.capttracker.ancientwarfarenpc.client.skin.NpcPlayerSkins;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcs;
import io.github.capttracker.ancientwarfarenpc.item.ItemCoin;
import io.github.capttracker.ancientwarfarenpc.item.ItemNpcSpawner;
import io.github.capttracker.ancientwarfarenpc.registry.FactionRegistry;
import io.github.capttracker.ancientwarfarenpc.skin.NpcSkinSettings;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Client-only setup of the NPC rendering: the renderer of every NPC type and of the priest's potion, the player-skin lookup of the skin settings,
 * the baton HUD layer and the item colours of the spawner and the coins. The overlays that listen to the game bus
 * ({@link io.github.capttracker.ancientwarfarenpc.client.render.NpcCommandOverlay}, {@code NpcWorkLinesOverlay}) subscribe by themselves.
 * Nothing outside the client package may reference this class.
 *
 * <p>Needs no wiring in the mod class: the annotation registers the mod bus listeners here and the nested class registers the game bus one.
 */
@EventBusSubscriber(modid = AncientWarfareNPC.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class NpcRenderClient {
    /** The colour of the top overlay of a faction spawner item (1.12.2 {@code NPCItemColors.FACTION_TOP_COLOR}). */
    public static final int FACTION_TOP_COLOR = 0xEF5757;

    private NpcRenderClient() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (AwNpcs.NpcDeclaration declaration : AwNpcs.getNpcMap().values()) {
            event.registerEntityRenderer(declaration.getEntityType(), NpcRenderer<NpcBase>::new);
        }
        event.registerEntityRenderer(AwNpcs.NO_FRIENDLY_FIRE_POTION.get(), ThrownItemRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        NpcSkinSettings.setPlayerSkinResolver(NpcPlayerSkins::resolveTexture);
    }

    /** The command baton text HUD, over everything else, as the 1.12.2 render tick handler drew it. */
    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Ids.of(AncientWarfareNPC.MOD_ID, "command_overlay"), new NpcCommandOverlay.Hud());
    }

    /**
     * Item tints (1.12.2 {@code NPCItemColors}): the spawner's bottom overlay (tint 1) has the colour of its faction, the top overlay (tint 2) a fixed
     * red for a known faction, and the coins have the colour of their metal. 1.21.1 reads the alpha of the colour, so the colours are made opaque;
     * -1 (white) leaves the layer as it is.
     */
    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> getSpawnerColor(stack, tintIndex), AwNpcs.NPC_SPAWNER.get());
        Item[] coins = AwNpcs.COINS.values().stream().map(DeferredItem::get).toArray(Item[]::new);
        event.register((stack, tintIndex) -> opaque(ItemCoin.getMetal(stack).getColor()), coins);
    }

    private static int getSpawnerColor(ItemStack stack, int tintIndex) {
        if (tintIndex != 1 && tintIndex != 2) {
            return -1;
        }
        String factionName = ItemNpcSpawner.getFaction(stack).orElse("");
        if (tintIndex == 2) {
            return FactionRegistry.getFactionNames().contains(factionName) ? opaque(FACTION_TOP_COLOR) : -1;
        }
        int color = FactionRegistry.getFaction(factionName).getColor();
        return color < 0 ? -1 : opaque(color);
    }

    private static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }
}
