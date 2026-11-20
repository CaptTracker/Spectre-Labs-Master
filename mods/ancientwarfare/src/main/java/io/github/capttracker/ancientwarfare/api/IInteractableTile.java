/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/interfaces/IInteractableTile.
 */
package io.github.capttracker.ancientwarfare.api;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * A block entity that reacts to being right-clicked. {@code BaseBlock#useItemOn} calls it on both sides for every
 * hand that is not skipped by sneaking, as {@code onBlockActivated} did in 1.12.2 (replaces
 * {@code WorldTools.clickInteractableTileWithHand}).
 */
public interface IInteractableTile {
    /** @return true if the click was handled (the block then swallows it) */
    boolean onBlockClicked(Player player, @Nullable InteractionHand hand);
}
