/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5).
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfareautomation.menu.MarketStallMenu;

/** Screen of {@link MarketStallMenu}: the inventories with their own captions, the side button and the button for the offers screen. */
public class GuiWorksiteMarketStall extends GuiWorksiteBase<MarketStallMenu> {

    public GuiWorksiteMarketStall(MarketStallMenu menu) {
        super(menu);
    }

    @Override
    public void initElements() {
        MarketStallMenu menu = getContainer();
        addGuiElement(new Label(8, menu.topLabel, "guistrings.automation.market_earnings"));
        addGuiElement(new Label(8, menu.frontLabel, "guistrings.automation.market_stock"));
        addGuiElement(new Label(8, menu.bottomLabel, "guistrings.automation.market_till"));
        addGuiElement(new Label(8, menu.playerLabel, "guistrings.inventory.player"));
        addSideSelectButton();
        addGuiElement(new Button(58, ySize - 8 - 12, 70, 12, "guistrings.automation.market_offers_button") {
            @Override
            protected void onPressed() {
                getContainer().requestOpenAlt();
            }
        });
    }

    @Override
    public void setupElements() {
        // noop
    }
}
