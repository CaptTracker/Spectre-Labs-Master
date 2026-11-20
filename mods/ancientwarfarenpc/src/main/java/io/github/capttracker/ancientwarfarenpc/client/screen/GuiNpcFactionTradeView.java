/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/gui/GuiNpcFactionTradeView.
 */
package io.github.capttracker.ancientwarfarenpc.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.Line;
import io.github.capttracker.ancientwarfarenpc.menu.FactionTradeViewMenu;
import io.github.capttracker.ancientwarfarenpc.menu.NpcMenuPayloads;
import io.github.capttracker.ancientwarfarenpc.trade.FactionTrade;

/**
 * The trade view of a faction trader (1.12.2: {@code GuiNpcFactionTradeView}): the trades with a button each, how many can still be bought, or when a
 * sold out trade refills. Creative players also get the buttons for the inventory and the trade setup.
 *
 * <p>Change from 1.12.2: the trade button only asks the server (see {@link FactionTradeViewMenu}); the screen shows the result when the server's new
 * list arrives.
 */
public class GuiNpcFactionTradeView extends GuiNpcBase<FactionTradeViewMenu> {
    private Button inventoryButton;
    private Button setupButton;
    private CompositeScrolled area;

    public GuiNpcFactionTradeView(FactionTradeViewMenu menu) {
        super(menu);
    }

    @Override
    public void initElements() {
        int areaYSize = player.isCreative() ? ySize - 24 - 16 - 4 - 4 * 18 : ySize - 16 - 4 - 4 * 18;
        area = new CompositeScrolled(this, 0, player.isCreative() ? 24 : 0, xSize, areaYSize);
        inventoryButton = new Button(8, 8, (256 - 16) / 2, 12, "guistrings.inventory") {
            @Override
            protected void onPressed() {
                requestOpen(NpcMenuPayloads.Request.INVENTORY);
            }
        };
        setupButton = new Button(8 + ((256 - 16) / 2), 8, (256 - 16) / 2, 12, "guistrings.trade_setup") {
            @Override
            protected void onPressed() {
                requestOpen(NpcMenuPayloads.Request.TRADE_SETUP);
            }
        };
    }

    @Override
    public void setupElements() {
        clearElements();
        addGuiElement(area);
        if (player.isCreative()) {
            addGuiElement(inventoryButton);
            addGuiElement(setupButton);
        }
        addTrades();
    }

    private void addTrades() {
        area.clearElements();
        int totalHeight = 8;
        if (getContainer().refused) {
            area.addGuiElement(new Label(8, 8, "guistrings.market.refused"));
        } else if (getContainer().tradeList.isEmpty()) {
            area.addGuiElement(new Label(8, 8, "guistrings.trader.no_trade"));
        } else {
            if (getContainer().priceMultiplier != 1.0D) {
                area.addGuiElement(new Label(8, totalHeight, format("guistrings.market.price_factor", Math.round(getContainer().priceMultiplier * 100.0D))));
                totalHeight += 12;
            }
            for (int i = 0; i < getContainer().tradeList.size(); i++) {
                totalHeight = addTrade(getContainer().tradeList.get(i).pricedCopy(getContainer().priceMultiplier), i, totalHeight);
            }
        }
        area.setAreaSize(totalHeight);
    }

    private int addTrade(final FactionTrade trade, final int tradeNum, int startHeight) {
        int gridY = TradeWidgets.addGrid(trade, startHeight, this, area::addGuiElement, null);

        int startWidth = TradeWidgets.arrowX(trade);
        area.addGuiElement(new Label(startWidth + 1, startHeight + (gridY + 1) * 5, ">"));
        startWidth *= 2;
        startWidth += 9;

        if (trade.getCurrentAvailable() <= 0) {
            area.addGuiElement(new Label(startWidth, startHeight, "guistrings.trade_unavailable"));
            long refillsIn = trade.getRefillTime() - getContainer().getWorld().getGameTime();
            if (refillsIn < 0) {
                refillsIn = 0;
            }
            long seconds = (refillsIn / 20) % 60;
            long minutes = (refillsIn / (60 * 20)) % 60;
            long hours = (refillsIn / (60 * 60 * 20)) % 24;
            long days = (refillsIn / (24 * 60 * 60 * 20));
            area.addGuiElement(new Label(startWidth, startHeight + 12, "guistrings.trade_refills_in"));
            area.addGuiElement(new Label(startWidth, startHeight + 24, format("guistrings.trade_refill_time", days, hours, minutes, seconds)));
        } else {
            Button tradeButton = new Button(startWidth, startHeight + 17, 70, 20, "guistrings.trade") {
                @Override
                protected void onPressed() {
                    getContainer().doTrade(tradeNum);
                }
            };
            area.addGuiElement(tradeButton);
            area.addGuiElement(new Label(startWidth, startHeight, format("guistrings.trades_available", trade.getCurrentAvailable())));
        }

        startHeight += 18 * gridY;//input/output grid size
        area.addGuiElement(new Line(0, startHeight + 1, xSize, startHeight + 1, 1, 0x000000ff));
        startHeight += 5;//separator line and padding
        return startHeight;
    }
}
