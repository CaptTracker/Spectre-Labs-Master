/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: the trade slot code that npc/gui/GuiTradeOrder, GuiNpcFactionTradeSetup, GuiNpcFactionTradeView and GuiNpcPlayerOwnedTrade each
 * had a copy of (addTrade, addTradeInputSlot, addTradeOutputSlot).
 */
package io.github.capttracker.ancientwarfarenpc.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.ITooltipRenderer;
import io.github.capttracker.ancientwarfare.client.gui.elements.GuiElement;
import io.github.capttracker.ancientwarfare.client.gui.elements.ItemSlot;
import io.github.capttracker.ancientwarfarenpc.trade.Trade;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/** The grid of input and output slots of one trade, as every trade screen draws it. */
final class TradeWidgets {
    private TradeWidgets() {}

    /**
     * Adds the three by three grid of a trade: the inputs on the left, the outputs 3 * 18 + 9 pixels to the right of them.
     *
     * @param onChanged what to do when the player sets a slot with the stack on the cursor; null makes the slots read only
     * @return the number of rows the grid grew to (the {@code gridY} of the original: the row the next slot would go in)
     */
    static int addGrid(Trade trade, int startHeight, ITooltipRenderer render, Consumer<GuiElement> add, @Nullable Runnable onChanged) {
        int gridX = 0;
        int gridY = 0;
        for (int i = 0; i < trade.size(); i++) {
            int slotX = gridX * 18 + 8;
            int slotY = gridY * 18 + startHeight;
            add.accept(slot(render, trade, true, i, slotX, slotY, onChanged));
            slotX += 3 * 18 + 9;
            add.accept(slot(render, trade, false, i, slotX, slotY, onChanged));
            gridX++;
            if (gridX >= 3) {
                gridX = 0;
                gridY++;
            }
        }
        return gridY;
    }

    /** The x of the arrow between the inputs and the outputs for a trade of this many slots (the original shifted it for fewer than three). */
    static int arrowX(Trade trade) {
        int startWidth = 8 + 3 * 18;
        if (trade.size() < 3) {
            startWidth += (trade.size() - 3) * 18;
        }
        return startWidth;
    }

    private static ItemSlot slot(ITooltipRenderer render, Trade trade, boolean input, int slotNum, int x, int y, @Nullable Runnable onChanged) {
        ItemStack stack = input ? trade.getInputStack(slotNum) : trade.getOutputStack(slotNum);
        stack = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        ItemSlot slot;
        if (onChanged == null) {
            slot = new ItemSlot(x, y, stack, render);
        } else {
            slot = new ItemSlot(x, y, stack, render) {
                @Override
                public void onSlotClicked(ItemStack carried, boolean rightClicked) {
                    ItemStack copy = carried.isEmpty() ? ItemStack.EMPTY : carried.copy();
                    setItem(copy);
                    if (input) {
                        trade.setInputStack(slotNum, copy);
                    } else {
                        trade.setOutputStack(slotNum, copy);
                    }
                    onChanged.run();
                }
            };
        }
        if (stack.isEmpty()) {
            slot.addTooltip(input ? "guistrings.npc.trade_input_slot" : "guistrings.npc.trade_output_slot");
        }
        return slot;
    }
}
