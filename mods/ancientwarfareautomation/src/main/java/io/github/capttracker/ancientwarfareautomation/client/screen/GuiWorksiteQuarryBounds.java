/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/gui/GuiWorksiteQuarryBounds; mine settings added in rework phase 3.
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.Checkbox;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.NumberInput;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.mine.MineMode;
import io.github.capttracker.ancientwarfareautomation.menu.QuarryBoundsMenu;
import net.minecraft.util.Mth;

/**
 * The quarry's mine screen: the depth (open quarry), the mine style, the Y of the shaft bottom and the tunnels, and the void filter. The mine settings go to
 * the server as soon as they change (it validates them); closing sends the height and returns to the quarry.
 */
public class GuiWorksiteQuarryBounds extends GuiContainerBase<QuarryBoundsMenu> {

    private NumberInput heightValue;
    private NumberInput yValue;
    private Button modeButton;
    private Checkbox cobble;
    private Checkbox dirt;
    private Checkbox gravel;

    private boolean boundsAdjusted = false;

    public GuiWorksiteQuarryBounds(QuarryBoundsMenu menu) {
        super(menu, 160, 112);
        this.shouldCloseOnVanillaKeys = true;
    }

    @Override
    public void initElements() {
        this.clearElements();
        addGuiElement(new Label(6, 4, "guistrings.automation.height"));
        heightValue = new NumberInput(6, 14, 40, getContainer().maxHeight, this) {
            @Override
            public void onValueUpdated(float value) {
                getContainer().maxHeight = (int) value;
                boundsAdjusted = true;
                refreshGui();
            }
        };
        heightValue.setIntegerValue();
        addGuiElement(heightValue);

        addGuiElement(new Label(70, 4, "guistrings.automation.mine_y"));
        yValue = new NumberInput(70, 14, 40, getContainer().mineY, this) {
            @Override
            public void onValueUpdated(float value) {
                int y = Mth.clamp((int) value, getContainer().mineYMin, getContainer().mineYMax);
                if (y != getContainer().mineY) {
                    getContainer().mineY = y;
                    getContainer().sendMineToServer();
                }
            }
        };
        yValue.setIntegerValue();
        yValue.setAllowNegative();
        addGuiElement(yValue);

        addGuiElement(new Label(6, 34, "guistrings.automation.mine_mode"));
        modeButton = new Button(6, 44, 148, 14, MineMode.byOrdinal(getContainer().mode).langKey()) {
            @Override
            protected void onPressed() {
                QuarryBoundsMenu menu = getContainer();
                menu.mode = (menu.mode + 1) % MineMode.values().length;
                menu.sendMineToServer();
                refreshGui();
            }
        };
        addGuiElement(modeButton);

        addGuiElement(new Label(6, 64, "guistrings.automation.mine_void"));
        cobble = new Checkbox(6, 76, 12, 12, "guistrings.automation.mine_void_cobble") {
            @Override
            public void onToggled() {
                getContainer().voidCobble = checked();
                getContainer().sendMineToServer();
            }
        };
        dirt = new Checkbox(6, 92, 12, 12, "guistrings.automation.mine_void_dirt") {
            @Override
            public void onToggled() {
                getContainer().voidDirt = checked();
                getContainer().sendMineToServer();
            }
        };
        gravel = new Checkbox(84, 76, 12, 12, "guistrings.automation.mine_void_gravel") {
            @Override
            public void onToggled() {
                getContainer().voidGravel = checked();
                getContainer().sendMineToServer();
            }
        };
        addGuiElement(cobble);
        addGuiElement(dirt);
        addGuiElement(gravel);
    }

    @Override
    public void setupElements() {
        QuarryBoundsMenu menu = getContainer();
        heightValue.setValue(menu.maxHeight);
        yValue.setValue(menu.mineY);
        modeButton.setText(MineMode.byOrdinal(menu.mode).langKey());
        cobble.setChecked(menu.voidCobble);
        dirt.setChecked(menu.voidDirt);
        gravel.setChecked(menu.voidGravel);
    }

    /** The server revalidates the quarry's bounds and opens the quarry's main screen; this screen stays until that one opens. */
    @Override
    protected boolean onGuiCloseRequested() {
        getContainer().onClose(boundsAdjusted);
        return false;
    }
}
