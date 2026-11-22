/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the client-to-server GUI request of core/network/NetworkHandler.openGui, as used by the NPC screens.
 */
package io.github.capttracker.ancientwarfarenpc.menu;

import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The payload with which an NPC screen asks the server to open another NPC menu (the inventory screen's alt, creative and orders buttons, the
 * bards' return to the inventory, the trade view's inventory and setup buttons). The NPC menus are opened by the server, so what the 1.12.2
 * screens did with {@code NetworkHandler.openGui} on the client is a request here. The server checks that the NPC is alive and within 8 blocks and
 * that the player may use the menu ({@link NpcMenuOpener}).
 *
 * <p>Wiring: {@code AwNpcMenus.registerEvents} registers it with {@code event.registrar(NpcNetwork.VERSION)}.
 */
public final class NpcMenuPayloads {
    private NpcMenuPayloads() {}

    /** Registers the payloads; call it with a registrar made with {@code NpcNetwork.VERSION}. */
    public static void registerPayloads(PayloadRegistrar registrar) {
        registrar.playToServer(OpenNpcMenu.TYPE, OpenNpcMenu.CODEC, OpenNpcMenu::handle);
    }

    /** Which menu a client asks for. */
    public enum Request {
        /** The NPC's own inventory menu ({@code NpcBase.openGUI}, 1.12.2: {@code GUI_NPC_INVENTORY}). */
        INVENTORY,
        /** The alt GUI of the NPC ({@code NpcBase.openAltGui}: the bard's songs, the trader's shop). */
        ALT,
        /** The talk screen: what the NPC is thinking (anyone who is not an enemy). */
        TALK,
        /** The talk screen on the morale tab (the commanders of a player-owned NPC). */
        MORALE,
        /** The creative controls ({@code GUI_NPC_CREATIVE}). */
        CREATIVE,
        /** The trade setup of a faction trader ({@code GUI_NPC_FACTION_TRADE_SETUP}). */
        TRADE_SETUP
    }

    /** Client to server: open a menu of this NPC. */
    public record OpenNpcMenu(int entityId, Request request) implements CustomPacketPayload {
        public static final Type<OpenNpcMenu> TYPE = new Type<>(Ids.of(AncientWarfareNPC.MOD_ID, "open_npc_menu"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenNpcMenu> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OpenNpcMenu::entityId,
                NeoForgeStreamCodecs.enumCodec(Request.class), OpenNpcMenu::request,
                OpenNpcMenu::new);

        @Override
        public Type<OpenNpcMenu> type() {
            return TYPE;
        }

        public static void handle(OpenNpcMenu payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player
                    && player.level().getEntity(payload.entityId) instanceof NpcBase npc
                    && npc.isAlive() && player.distanceToSqr(npc) < NpcMenuBase.USE_DISTANCE_SQ) {
                NpcMenuOpener.openRequested(player, npc, payload.request);
            }
        }
    }
}
