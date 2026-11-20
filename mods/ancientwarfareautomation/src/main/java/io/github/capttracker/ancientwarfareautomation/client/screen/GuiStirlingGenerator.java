/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/gui/GuiStirlingGenerator.
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.GuiRender;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.ProgressBar;
import io.github.capttracker.ancientwarfareautomation.menu.StirlingGeneratorMenu;
import net.minecraft.client.resources.language.I18n;

import java.util.Locale;

/** The stirling generator: the stored energy as a bar and number, the fuel slot and the burn progress bar. */
public class GuiStirlingGenerator extends GuiContainerBase<StirlingGeneratorMenu> {

    private Label energyLabel;

    private ProgressBar pg;
    private ProgressBar pg1;

    public GuiStirlingGenerator(StirlingGeneratorMenu menu) {
        super(menu, 178, menu.guiHeight);
    }

    private String energyText() {
        return I18n.get("guistrings.automation.current_energy", String.format(Locale.ROOT, "%.2f", getContainer().energy));
    }

    @Override
    public void initElements() {
        pg1 = new ProgressBar(8, 8, 178 - 16, 10);
        addGuiElement(pg1);

        energyLabel = new Label(8, 8, GuiRender.translate(energyText()));
        addGuiElement(energyLabel);

        pg = new ProgressBar(8, 8 + 10 + 18 + 4, 178 - 16, 16);
        addGuiElement(pg);
    }

    @Override
    public void setupElements() {
        StirlingGeneratorMenu menu = getContainer();
        energyLabel.setText(energyText());
        float progress = 0;
        if (menu.burnTimeBase > 0) {
            progress = (float) menu.burnTime / (float) menu.burnTimeBase;
        }
        pg.setProgress(progress);

        double max = menu.getMaxEnergy();
        pg1.setProgress(max > 0 ? (float) (menu.energy / max) : 0f);
    }
}
