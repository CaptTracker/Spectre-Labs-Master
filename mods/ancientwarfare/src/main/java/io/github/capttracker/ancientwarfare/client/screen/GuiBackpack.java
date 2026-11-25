/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/GuiBackpack.
 */
package io.github.capttracker.ancientwarfare.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.menu.BackpackMenu;

/**
 * The backpack: the toolkit's panel with the backpack's rows of slots above the player inventory. Height follows the
 * number of rows ({@code 114 + rows * 18}, the layout {@link BackpackMenu} uses).
 *
 * <p>The original also swallowed the hotbar number key that would swap the open backpack out of its slot
 * ({@code checkHotbarKeys}); {@link BackpackMenu#clicked} refuses that swap now, and its hotbar slot is not there at all.
 */
public class GuiBackpack extends GuiContainerBase<BackpackMenu> {
    public GuiBackpack(BackpackMenu menu) {
        super(menu, 178, 114 + menu.rows * 18);
    }

    @Override
    public void initElements() {
    }

    @Override
    public void setupElements() {
    }
}
