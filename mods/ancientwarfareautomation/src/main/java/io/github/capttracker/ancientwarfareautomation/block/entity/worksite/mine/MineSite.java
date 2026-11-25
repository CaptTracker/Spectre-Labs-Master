/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 3, mine styles).
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.mine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** What a {@link MinePattern} needs to know about the quarry it plans for. Implemented by the quarry block entity. */
public interface MineSite {
    Level level();

    /** The work area (x and z are the area; y reaches from the open quarry's bottom to the layer below the machine). */
    BlockPos boundsMin();

    BlockPos boundsMax();

    /** The Y of the quarry block. */
    int siteY();

    /** The chosen Y of the shaft's bottom and of the branch and strip tunnels (the feet layer; they are 2 high). */
    int mineY();

    /** The lowest Y the world has blocks at. */
    int worldMinY();

    /** The lowest layer of the open quarry (its set height below the machine). */
    int openBottomY();

    /** The longest side branch of the branch mine. */
    int branchLength();

    /** True if the quarry's tool can break the block (not air or fluid, not unbreakable, and within the tool tier of its upgrades). */
    boolean canDig(BlockPos pos, BlockState state);

    /** True if the block is an ore (the {@code c:ores} tag) a strip mine follows. */
    boolean isOre(BlockState state);

    /** How many of the item the quarry holds: ladders and torches are counted in their own slots, everything else in the main inventory. */
    int stock(Item item);

    /**
     * The pattern cannot go on because it needs a ladder and the quarry's ladder slots are empty: the mine waits and the screen says so. Called while
     * planning; the quarry forgets it at the start of every plan.
     */
    void waitingForLadder();

    /** The pattern is working around this position now: the quarry keeps its chunk loaded when it has a chunk loader. */
    void focus(BlockPos pos);
}
