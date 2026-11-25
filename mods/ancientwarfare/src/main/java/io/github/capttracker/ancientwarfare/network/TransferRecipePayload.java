/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/compat/jei/PacketTransferRecipe. Contains no recipe viewer types, so it is
 * registered with the other payloads whether or not JEI is installed.
 */
package io.github.capttracker.ancientwarfare.network;

import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.ancientwarfare.crafting.AWCraftingManager;
import io.github.capttracker.ancientwarfare.crafting.ICraftingContainer;
import io.github.capttracker.ancientwarfare.crafting.ICraftingRecipe;
import io.github.capttracker.ancientwarfare.crafting.RecipeResourceLocation;
import io.github.capttracker.ancientwarfare.crafting.RecipeTransfer;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: a recipe viewer's "move ingredients" button. The server checks that the player has a crafting
 * menu open (the id must match), finds the recipe by name and fills the menu's grid from the inventories.
 */
public record TransferRecipePayload(int containerId, String recipe, boolean maxTransfer) implements CustomPacketPayload {
    public static final Type<TransferRecipePayload> TYPE = new Type<>(Ids.of(AncientWarfareCore.MOD_ID, "transfer_recipe"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TransferRecipePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TransferRecipePayload::containerId,
            ByteBufCodecs.stringUtf8(256), TransferRecipePayload::recipe,
            ByteBufCodecs.BOOL, TransferRecipePayload::maxTransfer,
            TransferRecipePayload::new);

    @Override
    public Type<TransferRecipePayload> type() {
        return TYPE;
    }

    public static void handle(TransferRecipePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (player.containerMenu.containerId != payload.containerId
                || !(player.containerMenu instanceof ICraftingContainer container)) {
            return;
        }
        ICraftingRecipe recipe = AWCraftingManager.getRecipe(RecipeResourceLocation.deserialize(payload.recipe), player.level());
        if (recipe.isValid() && RecipeTransfer.execute(container, player, recipe, payload.maxTransfer)) {
            player.containerMenu.broadcastChanges();
        }
    }
}
