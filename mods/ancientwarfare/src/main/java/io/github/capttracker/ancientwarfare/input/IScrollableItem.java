/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/input/IScrollableItem.
 */
package io.github.capttracker.ancientwarfare.input;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Items that react to sneak plus mouse wheel while held in the main hand. Each method is called on the
 * client first; returning true cancels the hotbar scroll and sends the same call to the server.
 */
public interface IScrollableItem {
    boolean onScrollUp(Level world, Player player, ItemStack stack);

    boolean onScrollDown(Level world, Player player, ItemStack stack);
}
