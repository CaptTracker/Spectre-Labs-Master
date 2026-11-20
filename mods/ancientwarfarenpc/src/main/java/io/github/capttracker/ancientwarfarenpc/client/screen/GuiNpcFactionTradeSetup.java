/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/gui/GuiNpcFactionTradeSetup.
 */
package io.github.capttracker.ancientwarfarenpc.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiSelectFromList;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.Line;
import io.github.capttracker.ancientwarfare.client.gui.elements.NumberInput;
import io.github.capttracker.ancientwarfare.menu.ScreenOnlyMenu;
import io.github.capttracker.ancientwarfarenpc.menu.FactionTradeSetupMenu;
import io.github.capttracker.ancientwarfarenpc.trade.FactionTrade;
import io.github.capttracker.ancientwarfarenpc.trade.FactionTradeList;

/**
 * The trade setup of a faction trader (1.12.2: {@code GuiNpcFactionTradeSetup}), for creative players: the trades with their input and output slots
 * (click a slot with a stack on the cursor to set it), the number of trades and the refill time of each, and the buttons to load a template and to save
 * the list as a template.
 *
 * <p>Changes from 1.12.2: the template names come from the server through the menu, and choosing one asks the server to replace the list (the template
 * registry is server data); the list is edited on the screen's own copy and sent when the screen closes, as before.
 */
public class GuiNpcFactionTradeSetup extends GuiNpcBase<FactionTradeSetupMenu> {
    private CompositeScrolled area;
    private FactionTradeList tradeList;

    public GuiNpcFactionTradeSetup(FactionTradeSetupMenu menu) {
        super(menu, 320, 240);
    }

    @Override
    public void initElements() {
        area = new CompositeScrolled(this, 0, 0, xSize, ySize - 16 - 4 - 4 * 18);
        addGuiElement(area);

        Button templateButton = new Button(170, ySize - 85, xSize - 174, 12, "guistrings.select_trade_template") {
            @Override
            protected void onPressed() {
                showScreen(new GuiSelectFromList<String>(GuiNpcFactionTradeSetup.this, "", s -> s, () -> getContainer().getTemplateNames(), s -> {
                    getContainer().applyTradeTemplate(s);
                    refreshGui();
                }, true, new ScreenOnlyMenu(player.getInventory())));
            }
        };
        addGuiElement(templateButton);

        Button templateSaveButton = new Button(170, ySize - 69, xSize - 174, 12, "guistrings.save_to_trade_template") {
            @Override
            protected void onPressed() {
                showScreen(new GuiTradeTemplateSave(GuiNpcFactionTradeSetup.this));
                refreshGui();
            }
        };
        addGuiElement(templateSaveButton);
    }

    @Override
    public void setupElements() {
        area.clearElements();
        tradeList = getContainer().tradeList;
        int totalHeight = 8;
        for (int i = 0; i < tradeList.size(); i++) {
            totalHeight = addTrade(tradeList.get(i), totalHeight, i);
        }
        Button newTradeButton = new Button(8, totalHeight, xSize - 8 - 16, 12, "guistrings.new_trade") {
            @Override
            protected void onPressed() {
                tradeList.addNewTrade();
                getContainer().tradesChanged = true;
                refreshGui();
            }
        };
        area.addGuiElement(newTradeButton);
        totalHeight += 12;
        area.setAreaSize(totalHeight);
    }

    private int addTrade(final FactionTrade trade, int startHeight, final int tradeNum) {
        int gridY = TradeWidgets.addGrid(trade, startHeight, this, area::addGuiElement, () -> getContainer().tradesChanged = true);
        addTradeControls(trade, startHeight, tradeNum);
        startHeight += 18 * gridY;//input/output grid size
        area.addGuiElement(new Line(0, startHeight + 1, xSize, startHeight + 1, 1, 0x000000ff));
        startHeight += 5;//separator line and padding
        return startHeight;
    }

    private void addTradeControls(final FactionTrade trade, int startHeight, final int tradeNum) {
        startHeight -= 1;//offset by 1 to lineup better with the item slot boxes, as they align to the inner slot rather than border
        int startWidth = TradeWidgets.arrowX(trade);
        int infoX = startWidth * 2 + 5;
        Button upButton = new Button(infoX, startHeight, 55, 12, "guistrings.up") {
            @Override
            protected void onPressed() {
                tradeList.increment(tradeNum);
                refreshGui();
                getContainer().tradesChanged = true;
            }
        };
        area.addGuiElement(upButton);

        Button downButton = new Button(infoX, startHeight + 3 * 18 - 12, 55, 12, "guistrings.down") {
            @Override
            protected void onPressed() {
                tradeList.decrement(tradeNum);
                refreshGui();
                getContainer().tradesChanged = true;
            }
        };
        area.addGuiElement(downButton);

        Button delete = new Button(infoX, startHeight + 21, 55, 12, "guistrings.delete") {
            @Override
            protected void onPressed() {
                tradeList.remove(tradeNum);
                refreshGui();
                getContainer().tradesChanged = true;
            }
        };
        area.addGuiElement(delete);

        area.addGuiElement(new Label(startWidth + 1, startHeight + 20, ">"));
        area.addGuiElement(new Label(infoX + 55 + 4, startHeight + 1, "guistrings.max_trades"));
        area.addGuiElement(new Label(infoX + 55 + 4, startHeight + 18 + 1, "guistrings.refill_frequency"));

        NumberInput tradeInput = new NumberInput(infoX + 55 + 4 + 60, startHeight, 40, trade.getMaxAvailable(), this) {
            @Override
            public void onValueUpdated(float value) {
                trade.setMaxAvailable((int) value);
                getContainer().tradesChanged = true;
            }
        };
        tradeInput.setIntegerValue();
        area.addGuiElement(tradeInput);

        NumberInput refillInput = new NumberInput(infoX + 55 + 4 + 60, startHeight + 18, 40, trade.getRefillFrequency(), this) {
            @Override
            public void onValueUpdated(float value) {
                trade.setRefillFrequency((int) value);
                getContainer().tradesChanged = true;
            }
        };
        refillInput.setIntegerValue();
        refillInput.setAllowNegative();
        area.addGuiElement(refillInput);
    }

    @Override
    protected boolean onGuiCloseRequested() {
        getContainer().onGuiClosed();
        return super.onGuiCloseRequested();
    }
}
