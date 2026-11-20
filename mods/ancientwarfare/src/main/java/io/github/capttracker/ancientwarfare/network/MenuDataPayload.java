/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/network/PacketGui.
 */
package io.github.capttracker.ancientwarfare.network;

import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.ancientwarfare.menu.AwMenu;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * NBT message between the two sides of an open {@link AwMenu}. Sent in either direction.
 * {@code forScreen} replaces the 1.12.2 convention of wrapping the data in a "gui" tag.
 */
public record MenuDataPayload(CompoundTag data, boolean forScreen) implements CustomPacketPayload {
    public static final Type<MenuDataPayload> TYPE = new Type<>(Ids.of(AncientWarfareCore.MOD_ID, "menu_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MenuDataPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, MenuDataPayload::data,
            ByteBufCodecs.BOOL, MenuDataPayload::forScreen,
            MenuDataPayload::new);

    @Override
    public Type<MenuDataPayload> type() {
        return TYPE;
    }

    /** Runs on the main thread of whichever side received it. */
    public static void handle(MenuDataPayload payload, IPayloadContext context) {
        // Only the menu the player has open right now may receive data, so a client cannot address another menu.
        if (context.player().containerMenu instanceof AwMenu menu) {
            menu.onPacketData(payload.data(), payload.forScreen());
        }
    }
}
