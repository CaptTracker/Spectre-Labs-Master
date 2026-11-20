/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/container/ICraftingContainer.
 */
package io.github.capttracker.ancientwarfare.crafting;

import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * A menu with a recipe memory that a recipe viewer's "move recipe items" button can fill (1.12.2: the JEI transfer
 * handler). Implemented by the engineering bench menu; the automation crafting blocks do the same.
 */
public interface ICraftingContainer {
    CraftingMemoryController getCraftingMemoryContainer();

    /** The inventories recipe items may be taken from, in order (the block's storage, then the player's inventory). */
    IItemHandlerModifiable[] getInventories();

    /** Moves the grid's items into the inventories. @return false, changing nothing, if they do not all fit */
    boolean pushCraftingMatrixToInventories();
}
