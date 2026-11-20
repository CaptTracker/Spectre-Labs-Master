/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/owner/IOwnable.
 */
package io.github.capttracker.ancientwarfare.owner;

import net.minecraft.world.entity.player.Player;

/**
 * Block entities and entities that are owned by a player. Called by spawning and placing items to set the owner.
 */
public interface IOwnable {
    void setOwner(Player player);

    void setOwner(Owner owner);

    Owner getOwner();

    boolean isOwner(Player player);
}
