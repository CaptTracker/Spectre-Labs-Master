/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5).
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.client.gui.elements.ItemSlot;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.Line;
import io.github.capttracker.ancientwarfare.client.gui.elements.NumberInput;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.MarketEntry;
import io.github.capttracker.ancientwarfareautomation.menu.MarketOffersMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The offers screen of a market stall. Each line has a slot for the item (click it with an item on the cursor to set it, right-click to clear), a number box
 * for the amount per deal, a slot that shows the price coin (click to change the metal, right-click to go back) and a number box for the coins. A buy order has
 * a third box, the most items the stall buys (0 = no limit). Closing the screen sends the changes and returns to the stall.
 */
public class GuiWorksiteMarketOffers extends GuiContainerBase<MarketOffersMenu> {
    private CompositeScrolled area;

    public GuiWorksiteMarketOffers(MarketOffersMenu menu) {
        super(menu, 256, 240);
    }

    @Override
    public void initElements() {
        area = new CompositeScrolled(this, 0, 0, xSize, ySize);
    }

    @Override
    public void setupElements() {
        clearElements();
        area.clearElements();
        addGuiElement(area);
        int y = 6;
        area.addGuiElement(new Label(8, y, "guistrings.automation.market_sell_header"));
        y += 12;
        area.addGuiElement(new Label(8, y, "guistrings.automation.market_columns"));
        y += 12;
        y = addLines(getContainer().sell, false, y);
        area.addGuiElement(new Line(0, y, xSize, y, 1, 0x000000ff));
        y += 6;
        area.addGuiElement(new Label(8, y, "guistrings.automation.market_buy_header"));
        y += 12;
        area.addGuiElement(new Label(8, y, "guistrings.automation.market_columns_buy"));
        y += 12;
        y = addLines(getContainer().buy, true, y);
        area.setAreaSize(y + 6);
    }

    private int addLines(List<MarketEntry> entries, boolean buy, int startY) {
        int y = startY;
        for (MarketEntry entry : entries) {
            addLine(entry, buy, y);
            y += 20;
        }
        return y;
    }

    private void addLine(MarketEntry entry, boolean buy, int y) {
        ItemSlot itemSlot = new ItemSlot(9, y + 1, entry.item, this) {
            @Override
            public void onSlotClicked(ItemStack carried, boolean rightClicked) {
                if (carried.isEmpty() || rightClicked) {
                    entry.item = ItemStack.EMPTY;
                } else {
                    int count = entry.item.isEmpty() ? 1 : entry.item.getCount();
                    entry.item = carried.copyWithCount(Math.max(1, Math.min(carried.getMaxStackSize(), count)));
                }
                setItem(entry.item);
            }
        };
        itemSlot.addTooltip("guistrings.automation.market_item_slot");
        area.addGuiElement(itemSlot);

        NumberInput amount = new NumberInput(32, y + 3, 28, entry.item.isEmpty() ? 1 : entry.item.getCount(), this) {
            @Override
            public void onValueUpdated(float value) {
                if (!entry.item.isEmpty()) {
                    entry.item.setCount(Math.max(1, Math.min(entry.item.getMaxStackSize(), (int) value)));
                    itemSlot.setItem(entry.item);
                }
            }
        };
        amount.setIntegerValue();
        area.addGuiElement(amount);

        ItemStack coin = entry.price.isEmpty() ? MarketEntry.coin("copper", 1) : entry.price;
        ItemSlot priceSlot = new ItemSlot(72, y + 1, coin, this) {
            @Override
            public void onSlotClicked(ItemStack carried, boolean rightClicked) {
                String metal = MarketEntry.cycleMetal(MarketEntry.metalOf(entry.price), !rightClicked);
                int count = entry.price.isEmpty() ? 1 : entry.price.getCount();
                entry.price = MarketEntry.coin(metal, count);
                setItem(entry.price);
            }
        };
        priceSlot.setRenderItemQuantity(false);
        priceSlot.addTooltip("guistrings.automation.market_price_slot");
        area.addGuiElement(priceSlot);

        NumberInput price = new NumberInput(94, y + 3, 30, entry.price.isEmpty() ? 1 : entry.price.getCount(), this) {
            @Override
            public void onValueUpdated(float value) {
                int count = Math.max(1, Math.min(MarketEntry.MAX_PRICE, (int) value));
                if (entry.price.isEmpty()) {
                    entry.price = MarketEntry.coin("copper", count);
                } else {
                    entry.price.setCount(count);
                }
                priceSlot.setItem(entry.price);
            }
        };
        price.setIntegerValue();
        area.addGuiElement(price);

        if (buy) {
            NumberInput limit = new NumberInput(136, y + 3, 36, entry.limit, this) {
                @Override
                public void onValueUpdated(float value) {
                    entry.limit = Math.max(0, Math.min(MarketEntry.MAX_LIMIT, (int) value));
                    entry.remaining = entry.limit;
                }
            };
            limit.setIntegerValue();
            area.addGuiElement(limit);
            if (entry.limit > 0 && !entry.item.isEmpty()) {
                area.addGuiElement(new Label(180, y + 5, Component.translatable("guistrings.automation.market_left", entry.remaining).getString()));
            }
        }
    }

    /** Sends the entries and asks the server for the stall's main screen; this screen stays until that one opens. */
    @Override
    protected boolean onGuiCloseRequested() {
        // an entry without an item is sent empty; one without a price (the slot shows a copper coin) gets 1 copper
        for (List<MarketEntry> list : List.of(getContainer().sell, getContainer().buy)) {
            for (MarketEntry entry : list) {
                if (entry.item.isEmpty()) {
                    entry.price = ItemStack.EMPTY;
                } else if (entry.price.isEmpty()) {
                    entry.price = MarketEntry.coin("copper", 1);
                }
            }
        }
        getContainer().sendOffersToServer(true);
        return false;
    }
}
