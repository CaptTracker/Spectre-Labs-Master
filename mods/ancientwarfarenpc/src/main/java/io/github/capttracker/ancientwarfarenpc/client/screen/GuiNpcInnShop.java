/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): the meal shop of an innkeeper.
 */
package io.github.capttracker.ancientwarfarenpc.client.screen;

import io.github.capttracker.ancientwarfare.api.IMarketStall.TradeResult;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.client.gui.elements.ItemSlot;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfarenpc.menu.InnShopMenu;

/** The meal shop of an innkeeper: each meal with the price this player pays and a Buy button. Only asks the server (see {@link InnShopMenu}). */
public class GuiNpcInnShop extends GuiNpcBase<InnShopMenu> {
    private CompositeScrolled area;

    public GuiNpcInnShop(InnShopMenu menu) {
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
        InnShopMenu menu = getContainer();
        int y = 8;
        if (!menu.open) {
            area.addGuiElement(new Label(8, y, "guistrings.inn.closed"));
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
        area.addGuiElement(new Label(8, y, "guistrings.inn.meals_header"));
        y += 12;
        if (menu.meals.isEmpty()) {
            area.addGuiElement(new Label(8, y, "guistrings.inn.no_meals"));
            y += 14;
        }
        for (InnShopMenu.Meal meal : menu.meals) {
            area.addGuiElement(new ItemSlot(9, y + 1, meal.item(), this));
            area.addGuiElement(new Label(30, y + 5, ">"));
            area.addGuiElement(new ItemSlot(40, y + 1, menu.price, this));
            int slot = meal.slot();
            area.addGuiElement(new Button(66, y + 1, 50, 16, "guistrings.trade") {
                @Override
                protected void onPressed() {
                    getContainer().buy(slot);
                }
            });
            area.addGuiElement(new Label(122, y + 5, format("guistrings.trades_available", meal.count())));
            y += 20;
        }
        area.setAreaSize(y + 4);
    }
}
