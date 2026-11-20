/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/gui/GuiNpcPlayerOwnedTrade.
 */
package io.github.capttracker.ancientwarfarenpc.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.Line;
import io.github.capttracker.ancientwarfarenpc.menu.NpcMenuPayloads;
import io.github.capttracker.ancientwarfarenpc.menu.PlayerOwnedTradeMenu;
import io.github.capttracker.ancientwarfarenpc.trade.POTrade;

/**
 * The shop of a player-owned trader (1.12.2: {@code GuiNpcPlayerOwnedTrade}): the trades that are in stock, each with a button. The owner also gets a
 * button for the inventory. Without a backpack the trader has nothing to sell.
 *
 * <p>Change from 1.12.2: the trade button only asks the server; the stock the screen checks against is updated by the inventory sync.
 */
public class GuiNpcPlayerOwnedTrade extends GuiNpcBase<PlayerOwnedTradeMenu> {
    private CompositeScrolled area;
    private boolean owner;

    public GuiNpcPlayerOwnedTrade(PlayerOwnedTradeMenu menu) {
        super(menu);
    }

    @Override
    public void initElements() {
        int areaSize = ySize - 8 - 4 - 8 - 4 * 18;
        int areaY = 0;
        if (getContainer().npc.isOwner(player)) {
            areaSize -= 12 + 8 + 4;
            areaY = 12 + 8 + 4;
            owner = true;
        }
        area = new CompositeScrolled(this, 0, areaY, xSize, areaSize);
    }

    @Override
    public void setupElements() {
        area.clearElements();
        clearElements();
        if (owner) {
            Button inventory = new Button(8, 8, 240, 12, "guistrings.inventory") {
                @Override
                protected void onPressed() {
                    requestOpen(NpcMenuPayloads.Request.INVENTORY);
                }
            };
            addGuiElement(inventory);
        }
        addGuiElement(area);
        if (getContainer().storage != null) {
            addTrades();
        } else {
            addSingleMessage("guistrings.trader.missing_backpack");
        }
    }

    private void addSingleMessage(String text) {
        area.addGuiElement(new Label(8, 8, text));
    }

    private void addTrades() {
        int totalHeight = 8;
        if (getContainer().tradeList == null || getContainer().tradeList.isEmpty()) {
            addSingleMessage("guistrings.trader.no_trade");
        } else {
            for (int i = 0; i < getContainer().tradeList.size(); i++) {
                POTrade trade = getContainer().tradeList.get(i);
                if (trade.isAvailable(getContainer().storage)) {
                    totalHeight = addTrade(trade, i, totalHeight);
                }
            }
            if (totalHeight == 8) {
                addSingleMessage("guistrings.trader.cant_trade");
            }
        }
        area.setAreaSize(totalHeight);
    }

    private int addTrade(final POTrade trade, final int tradeIndex, int startHeight) {
        int gridY = TradeWidgets.addGrid(trade, startHeight, this, area::addGuiElement, null);

        int startWidth = TradeWidgets.arrowX(trade);
        area.addGuiElement(new Label(startWidth + 1, startHeight + (gridY + 1) * 5, ">"));
        Button tradeButton = new Button(2 * startWidth + 9, startHeight + 17, 70, 20, "guistrings.trade") {
            @Override
            protected void onPressed() {
                getContainer().doTrade(tradeIndex);
            }
        };
        area.addGuiElement(tradeButton);

        startHeight += 18 * gridY;
        area.addGuiElement(new Line(0, startHeight + 1, xSize, startHeight + 1, 1, 0x000000ff));
        startHeight += 5;
        return startHeight;
    }
}
