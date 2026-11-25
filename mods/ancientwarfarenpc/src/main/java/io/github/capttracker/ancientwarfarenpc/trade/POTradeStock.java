/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the lookup of the trader's backpack that ContainerNpcPlayerOwnedTrade and NpcAIPlayerOwnedTrader did inline
 * (EntityTools.getHandHoldingItem + the item handler capability of the backpack).
 */
package io.github.capttracker.ancientwarfarenpc.trade;

import io.github.capttracker.ancientwarfare.item.BackpackItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Optional;

/**
 * The stock of a player owned trader is the backpack it holds (main hand first, then off hand). The backpack's inventory is the item handler
 * capability that Core registers for its backpacks (a {@code ComponentItemHandler} on the container component), so changes to it are saved
 * with the item.
 */
public final class POTradeStock {
    private POTradeStock() {}

    /** The inventory of the backpack the entity holds, if it holds one. */
    public static Optional<IItemHandler> getStorage(LivingEntity trader) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = trader.getItemInHand(hand);
            if (!stack.isEmpty() && stack.getItem() instanceof BackpackItem) {
                IItemHandler handler = stack.getCapability(Capabilities.ItemHandler.ITEM);
                if (handler != null) {
                    return Optional.of(handler);
                }
            }
        }
        return Optional.empty();
    }

    /** True if the trade can be done right now: every output is in stock and there is room for every payment. */
    public static boolean isAvailable(POTrade trade, LivingEntity trader) {
        return getStorage(trader).map(trade::isAvailable).orElse(false);
    }
}
