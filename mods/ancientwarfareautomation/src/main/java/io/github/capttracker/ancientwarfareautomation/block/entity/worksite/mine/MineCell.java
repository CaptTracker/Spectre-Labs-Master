/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 3, mine styles).
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.mine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import javax.annotation.Nullable;

/** One planned cell of a slice: the geometry of a pattern, before the world is looked at. {@code kind} is DIG, LADDER, TORCH_FLOOR or TORCH_WALL. */
public record MineCell(BlockPos pos, MineOp.Kind kind, @Nullable Direction facing) {
    public static MineCell dig(int x, int y, int z) {
        return new MineCell(new BlockPos(x, y, z), MineOp.Kind.DIG, null);
    }

    public static MineCell of(int x, int y, int z, MineOp.Kind kind, @Nullable Direction facing) {
        return new MineCell(new BlockPos(x, y, z), kind, facing);
    }
}
