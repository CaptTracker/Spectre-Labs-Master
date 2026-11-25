/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 3, mine styles).
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.mine;

import net.minecraft.core.Direction;

import java.util.List;

/**
 * The tunnel mines. First an access shaft (1x1 with a ladder) from the top layer of the work area down to the chosen Y, then a tunnel east along x from
 * the shaft, one column per slice. The tunnel's feet layer is the chosen Y and it is 2 high.
 * <ul>
 * <li>Branch mine: the main tunnel is 3 wide; every third column (not the first) starts a 1x2 side branch to each side, at most
 * {@code branchLength} long and never out of the work area; floor torches every 8 blocks on the main line and in each branch.</li>
 * <li>Strip mine: the tunnel is 1x2 and follows the ores it exposes as veins.</li>
 * </ul>
 * Tunnels stay where they are. The tunnel stops at the edge of the work area.
 */
public final class TunnelPattern extends SlicePattern {
    private final boolean branches;

    public TunnelPattern(boolean branches) {
        super(branches ? MineMode.BRANCH : MineMode.STRIP);
        this.branches = branches;
    }

    @Override
    protected boolean followsVeins() {
        return !branches;
    }

    private static int tunnelY(MineSite site) {
        return Math.max(site.worldMinY() + 1, Math.min(site.mineY(), site.boundsMax().getY() - 1));
    }

    private static int shaftX(MineSite site) {
        return site.boundsMin().getX() + 1;
    }

    private static int centerZ(MineSite site) {
        return (site.boundsMin().getZ() + site.boundsMax().getZ()) / 2;
    }

    private int shaftZ(MineSite site) {
        return branches ? centerZ(site) - 1 : centerZ(site);
    }

    private static int shaftDepth(MineSite site) {
        return Math.max(0, site.boundsMax().getY() - tunnelY(site) + 1);
    }

    private static int tunnelLength(MineSite site) {
        return Math.max(0, site.boundsMax().getX() - shaftX(site));
    }

    @Override
    protected int sliceCount(MineSite site) {
        return shaftDepth(site) + tunnelLength(site);
    }

    @Override
    protected void cells(MineSite site, int slice, List<MineCell> out) {
        int depth = shaftDepth(site);
        int ax = shaftX(site);
        if (slice < depth) {
            int y = site.boundsMax().getY() - slice;
            int sz = shaftZ(site);
            out.add(MineCell.dig(ax, y, sz));
            out.add(MineCell.of(ax, y, sz, MineOp.Kind.LADDER, Direction.SOUTH));
            return;
        }
        int t = slice - depth;
        int x = ax + t;
        int y = tunnelY(site);
        int cz = centerZ(site);
        int rows = branches ? 1 : 0;
        for (int dz = -rows; dz <= rows; dz++) {
            out.add(MineCell.dig(x, y, cz + dz));
            out.add(MineCell.dig(x, y + 1, cz + dz));
        }
        if (branches && t > 0 && t % 8 == 0) {
            out.add(MineCell.of(x, y, cz, MineOp.Kind.TORCH_FLOOR, null));
        }
        if (branches && t > 0 && t % 3 == 0) {
            int length = site.branchLength();
            for (int d = 1; d <= length; d++) {
                for (int side = -1; side <= 1; side += 2) {
                    int z = cz + side * (1 + d);
                    if (z < site.boundsMin().getZ() || z > site.boundsMax().getZ()) {
                        continue;
                    }
                    out.add(MineCell.dig(x, y, z));
                    out.add(MineCell.dig(x, y + 1, z));
                    if (d % 8 == 0) {
                        out.add(MineCell.of(x, y, z, MineOp.Kind.TORCH_FLOOR, null));
                    }
                }
            }
        }
    }
}
