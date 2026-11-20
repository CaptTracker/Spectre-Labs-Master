/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): the shop of a trader that staffs a market stall.
 */
package io.github.capttracker.ancientwarfarenpc.client.screen;

import io.github.capttracker.ancientwarfare.api.IMarketStall.TradeResult;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.client.gui.elements.ItemSlot;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.Line;
import io.github.capttracker.ancientwarfarenpc.menu.MarketShopMenu;

/**
 * The shop of a market stall trader: each sell offer with the price this player pays and a Buy button, the number of deals in stock, and below them
 * what the stall buys. The outcome of the last purchase shows at the top. Only asks the server (see {@link MarketShopMenu}).
 */
public class GuiNpcMarketShop extends GuiNpcBase<MarketShopMenu> {
    private CompositeScrolled area;

    public GuiNpcMarketShop(MarketShopMenu menu) {
        super(menu);
    }

    @Override
    public void initElements() {
        area = new CompositeScrolled(this, 0, 0, xSize, ySize - 16 - 4 - 4 * 18);
    }

    @Override
    public void setupElements() {
        clearElements();
        area.clearElements();
        addGuiElement(area);
        MarketShopMenu menu = getContainer();
        int y = 8;
        if (!menu.open) {
            area.addGuiElement(new Label(8, y, "guistrings.market.closed"));
            area.setAreaSize(y + 16);
            return;
        }
        if (menu.refused) {
            area.addGuiElement(new Label(8, y, "guistrings.market.refused"));
            area.setAreaSize(y + 16);
            return;
        }
        if (menu.lastResult >= 0 && menu.lastResult < TradeResult.values().length) {
            area.addGuiElement(new Label(8, y, "guistrings.market.result." + TradeResult.values()[menu.lastResult].name().toLowerCase(java.util.Locale.ROOT)));
            y += 12;
        }
        if (menu.multiplier != 1.0D) {
            area.addGuiElement(new Label(8, y, format("guistrings.market.price_factor", Math.round(menu.multiplier * 100.0D))));
            y += 12;
        }
        if (menu.offers.isEmpty()) {
            area.addGuiElement(new Label(8, y, "guistrings.market.no_offers"));
            y += 14;
        }
        for (MarketShopMenu.Offer offer : menu.offers) {
            area.addGuiElement(new ItemSlot(9, y + 1, offer.item(), this));
            area.addGuiElement(new Label(30, y + 5, ">"));
            area.addGuiElement(new ItemSlot(40, y + 1, offer.price(), this));
            if (offer.deals() > 0) {
                int index = offer.index();
                area.addGuiElement(new Button(66, y + 1, 50, 16, "guistrings.trade") {
                    @Override
                    protected void onPressed() {
                        getContainer().buy(index);
                    }
                });
                area.addGuiElement(new Label(122, y + 5, format("guistrings.trades_available", offer.deals())));
            } else {
                area.addGuiElement(new Label(66, y + 5, "guistrings.market.sold_out"));
            }
            y += 20;
        }
        if (!menu.orders.isEmpty()) {
            area.addGuiElement(new Line(0, y, xSize, y, 1, 0x000000ff));
            y += 5;
            area.addGuiElement(new Label(8, y, "guistrings.market.buys_header"));
            y += 12;
            for (MarketShopMenu.Order order : menu.orders) {
                area.addGuiElement(new ItemSlot(9, y + 1, order.item(), this).setHighlightOnMouseOver(false));
                area.addGuiElement(new Label(30, y + 5, ">"));
                area.addGuiElement(new ItemSlot(40, y + 1, order.price(), this).setHighlightOnMouseOver(false));
                if (order.remaining() >= 0) {
                    area.addGuiElement(new Label(66, y + 5, format("guistrings.market.wants_left", order.remaining())));
                }
                y += 20;
            }
        }
        area.setAreaSize(y + 4);
    }
}
