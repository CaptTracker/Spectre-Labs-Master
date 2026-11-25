/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/interfaces/ITooltipRenderer.
 */
package io.github.capttracker.ancientwarfare.client.gui;

import io.github.capttracker.ancientwarfare.client.gui.elements.Tooltip;
import net.minecraft.world.item.ItemStack;

/**
 * Implemented by the screen. Widgets hand it the tooltip they want shown; the screen draws it last,
 * on top of everything else.
 */
public interface ITooltipRenderer {

    void handleItemStackTooltipRender(ItemStack itemStack, int mouseX, int mouseY);

    void handleElementTooltipRender(Tooltip tooltip, int mouseX, int mouseY);

}
