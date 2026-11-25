/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/network/PacketItemInteraction, PacketItemMouseScroll and
 * PacketManualReload. The clipboard payload is new (the info tool copied to the server's clipboard).
 */
package io.github.capttracker.ancientwarfare.network;

import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.ancientwarfare.input.IItemKeyInterface;
import io.github.capttracker.ancientwarfare.input.IItemKeyInterface.ItemAltFunction;
import io.github.capttracker.ancientwarfare.input.IScrollableItem;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import javax.annotation.Nullable;

/**
 * Payloads of the core utilities slice: the alt-key and mouse-scroll item interactions (client to
 * server), the manual reload helper and the clipboard copy of the info tool (server to client).
 *
 * <p>Wiring: call {@link #registerPayloads(PayloadRegistrar)} from {@code AwNetwork.register}.
 */
public final class CoreMiscPayloads {
    private CoreMiscPayloads() {}

    /** What the server-to-client payloads do on the client. Implemented by {@code CoreMiscClient}, which installs itself at client setup. */
    public interface ClientHandler {
        void reloadManual();

        void copyToClipboard(String text);
    }

    @Nullable
    private static volatile ClientHandler clientHandler;

    /** Client only: called once by {@code CoreMiscClient}. */
    public static void setClientHandler(ClientHandler handler) {
        clientHandler = handler;
    }

    public static void registerPayloads(PayloadRegistrar registrar) {
        registrar.playToServer(ItemAltKeyPayload.TYPE, ItemAltKeyPayload.CODEC, ItemAltKeyPayload::handle);
        registrar.playToServer(ItemScrollPayload.TYPE, ItemScrollPayload.CODEC, ItemScrollPayload::handle);
        registrar.playToClient(ManualReloadPayload.TYPE, ManualReloadPayload.CODEC, ManualReloadPayload::handle);
        registrar.playToClient(ClipboardPayload.TYPE, ClipboardPayload.CODEC, ClipboardPayload::handle);
    }

    /** One of the five alt keys was pressed while an {@link IItemKeyInterface} item was held (was PacketItemInteraction). */
    public record ItemAltKeyPayload(int altFunction) implements CustomPacketPayload {
        public static final Type<ItemAltKeyPayload> TYPE = new Type<>(Ids.of(AncientWarfareCore.MOD_ID, "item_alt_key"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ItemAltKeyPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ItemAltKeyPayload::altFunction,
                ItemAltKeyPayload::new);

        public ItemAltKeyPayload(ItemAltFunction function) {
            this(function.ordinal());
        }

        @Override
        public Type<ItemAltKeyPayload> type() {
            return TYPE;
        }

        public static void handle(ItemAltKeyPayload payload, IPayloadContext context) {
            Player player = context.player();
            if (!executeKeyPress(player, InteractionHand.MAIN_HAND, payload.altFunction)) {
                executeKeyPress(player, InteractionHand.OFF_HAND, payload.altFunction);
            }
        }

        private static boolean executeKeyPress(Player player, InteractionHand hand, int altFunction) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.isEmpty()) {
                return false;
            }
            // The client is not trusted: the ordinal is range checked and the item must opt in.
            if (altFunction >= 0 && altFunction < ItemAltFunction.values().length && stack.getItem() instanceof IItemKeyInterface keyItem) {
                keyItem.onKeyAction(player, stack, ItemAltFunction.values()[altFunction]);
                return true;
            }
            return false;
        }
    }

    /** Sneak plus mouse wheel on an {@link IScrollableItem} (was PacketItemMouseScroll). */
    public record ItemScrollPayload(boolean scrollUp) implements CustomPacketPayload {
        public static final Type<ItemScrollPayload> TYPE = new Type<>(Ids.of(AncientWarfareCore.MOD_ID, "item_scroll"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ItemScrollPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, ItemScrollPayload::scrollUp,
                ItemScrollPayload::new);

        @Override
        public Type<ItemScrollPayload> type() {
            return TYPE;
        }

        public static void handle(ItemScrollPayload payload, IPayloadContext context) {
            Player player = context.player();
            ItemStack stack = player.getMainHandItem();
            if (stack.getItem() instanceof IScrollableItem scrollable) {
                if (payload.scrollUp) {
                    scrollable.onScrollUp(player.level(), player, stack);
                } else {
                    scrollable.onScrollDown(player.level(), player, stack);
                }
            }
        }
    }

    /** Dev helper from {@code /awutils reloadmanual}: clear and reload the manual content (was PacketManualReload). */
    public record ManualReloadPayload() implements CustomPacketPayload {
        public static final Type<ManualReloadPayload> TYPE = new Type<>(Ids.of(AncientWarfareCore.MOD_ID, "manual_reload"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ManualReloadPayload> CODEC = StreamCodec.unit(new ManualReloadPayload());

        @Override
        public Type<ManualReloadPayload> type() {
            return TYPE;
        }

        public static void handle(ManualReloadPayload payload, IPayloadContext context) {
            ClientHandler handler = clientHandler;
            if (handler != null) {
                handler.reloadManual();
            }
        }
    }

    /** Text for the player's clipboard, sent by the info tool. */
    public record ClipboardPayload(String text) implements CustomPacketPayload {
        public static final Type<ClipboardPayload> TYPE = new Type<>(Ids.of(AncientWarfareCore.MOD_ID, "clipboard"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ClipboardPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(65536), ClipboardPayload::text,
                ClipboardPayload::new);

        @Override
        public Type<ClipboardPayload> type() {
            return TYPE;
        }

        public static void handle(ClipboardPayload payload, IPayloadContext context) {
            ClientHandler handler = clientHandler;
            if (handler != null) {
                handler.copyToClipboard(payload.text);
            }
        }
    }
}
