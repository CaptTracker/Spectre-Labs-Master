/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/input/ItemInputCallback.
 */
package io.github.capttracker.ancientwarfare.client.input;

import io.github.capttracker.ancientwarfare.input.IInputCallback;
import io.github.capttracker.ancientwarfare.input.IItemKeyInterface;
import io.github.capttracker.ancientwarfare.network.CoreMiscPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** One per alt key: offers the key press to the held item, main hand first, and tells the server if it took it. */
final class ItemInputCallback implements IInputCallback {
    private final IItemKeyInterface.ItemAltFunction altFunction;

    ItemInputCallback(IItemKeyInterface.ItemAltFunction altFunction) {
        this.altFunction = altFunction;
    }

    @Override
    public void onKeyPressed() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null) {
            return;
        }
        if (!runAction(minecraft.player, InteractionHand.MAIN_HAND)) {
            runAction(minecraft.player, InteractionHand.OFF_HAND);
        }
    }

    private boolean runAction(LocalPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof IItemKeyInterface keyItem && keyItem.onKeyActionClient(player, stack, altFunction)) {
            PacketDistributor.sendToServer(new CoreMiscPayloads.ItemAltKeyPayload(altFunction));
            return true;
        }
        return false;
    }
}
