/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 3, mine styles).
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.mine;

import net.minecraft.core.Direction;

import java.util.List;

/**
 * A 3x3 shaft straight down at the centre of the work area, from the top layer of the area to the chosen Y (or bedrock). One slice is one layer. Ladders
 * stand in the middle of the north row against its wall, a torch on the south wall every 8 layers.
 */
public final class ShaftPattern extends SlicePattern {
    public ShaftPattern() {
        super(MineMode.SHAFT);
    }

    private static int bottom(MineSite site) {
        return Math.max(site.mineY(), site.worldMinY());
    }

    @Override
    protected int sliceCount(MineSite site) {
        return Math.max(0, site.boundsMax().getY() - bottom(site) + 1);
    }

    @Override
    protected void cells(MineSite site, int slice, List<MineCell> out) {
        int cx = (site.boundsMin().getX() + site.boundsMax().getX()) / 2;
        int cz = (site.boundsMin().getZ() + site.boundsMax().getZ()) / 2;
        int y = site.boundsMax().getY() - slice;
        out.add(MineCell.dig(cx, y, cz));
        out.add(MineCell.dig(cx, y, cz - 1));
        out.add(MineCell.dig(cx + 1, y, cz));
        out.add(MineCell.dig(cx - 1, y, cz));
        out.add(MineCell.dig(cx, y, cz + 1));
        out.add(MineCell.dig(cx + 1, y, cz - 1));
        out.add(MineCell.dig(cx - 1, y, cz - 1));
        out.add(MineCell.dig(cx + 1, y, cz + 1));
        out.add(MineCell.dig(cx - 1, y, cz + 1));
        out.add(MineCell.of(cx, y, cz - 1, MineOp.Kind.LADDER, Direction.SOUTH));
        if (slice % 8 == 0) {
            out.add(MineCell.of(cx, y, cz + 1, MineOp.Kind.TORCH_WALL, Direction.NORTH));
        }
    }
}
