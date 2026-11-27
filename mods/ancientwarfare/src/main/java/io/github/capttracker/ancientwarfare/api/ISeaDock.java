/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 11): a dock that cargo ships moor at (the sea trade routes of players and the trade ships of the factions).
 */
package io.github.capttracker.ancientwarfare.api;

import io.github.capttracker.ancientwarfare.owner.IOwnable;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * A dock a cargo ship can moor at. Besides the boat of its own ({@link IBoatWorksite}) it has a storage: the goods a trade ship unloads go into it and the
 * goods it loads come out of it. The owner ({@link #getOwner()}) is the player the dock belongs to; a sea route only trades with the docks of its own owner.
 */
public interface ISeaDock extends IBoatWorksite, IOwnable {

    /** The position of the dock block. */
    BlockPos getDockPos();

    /** The storage of the dock (its inventory): what a ship unloads is put in, what a ship loads is taken out. */
    IItemHandler getDockStorage();
}
