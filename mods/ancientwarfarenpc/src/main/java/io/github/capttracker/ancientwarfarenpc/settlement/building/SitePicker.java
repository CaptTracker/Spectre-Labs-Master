/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): finding a place for a building inside the plots.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

import io.github.capttracker.ancientwarfarenpc.settlement.JobBoard;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds where a design fits: inside a plot, in one of the four turns, not on top of another build or of the hall, a storage, a bed, a worksite or a
 * boundary marker, on ground that is flat enough (the config allows filling up to {@code terrain_max_fill} blocks and cutting up to
 * {@code terrain_max_cut}), with no block entity and no water in the way. Near the hall is better, and a front that looks towards the hall is better. Server
 * side only, on the server thread; the chunks of the plot must be loaded.
 *
 * <p>The ground is read from the heightmap that ignores leaves: the standing level of a column is the first free block above the top solid block. Only the
 * columns whose bottom cell is not "leave as is" count (the floor and the walls); the best standing level of the building is the one that needs the least
 * filling and cutting.
 */
public final class SitePicker {
    /** A place that fits: the plot, the lowest north-west corner (its y is the standing level), the turn, the ground to fill and how good it is (lower is better). */
    public record Site(BuildingPlot plot, BlockPos origin, Direction facing, List<BuildProject.Fill> fills, double score) {}

    /** The result of a search: the site, or the reason it failed (a suffix of the lang key {@code guistrings.settlement.build.error.*}). */
    public record Result(@Nullable Site site, String error) {
        static Result ok(Site site) {
            return new Result(site, "");
        }

        static Result fail(String error) {
            return new Result(null, error);
        }
    }

    private static final int INVALID = Integer.MIN_VALUE;
    private static final int FLUID = Integer.MIN_VALUE + 1;
    /** Blocks kept free around the hall. */
    private static final int HALL_MARGIN = 2;
    private static final int MAX_CANDIDATES_KEPT = 8;
    private static final int STRIDE_ABOVE = 5000;

    private static final Direction[] FACINGS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private SitePicker() {}

    // ---------------------------------------------------------------- ground

    private static final class Heights {
        private final ServerLevel level;
        private final Map<Long, Integer> cache = new HashMap<>();

        Heights(ServerLevel level) {
            this.level = level;
        }

        /** The standing level of a column, or {@link #INVALID} (not loaded) or {@link #FLUID} (water or lava on top). */
        int at(int x, int z) {
            long key = BlockPos.asLong(x, 0, z);
            Integer cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
            int value;
            if (!level.hasChunk(x >> 4, z >> 4)) {
                value = INVALID;
            } else {
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (h <= level.getMinBuildHeight() + 1) {
                    value = INVALID;
                } else if (!level.getBlockState(new BlockPos(x, h - 1, z)).getFluidState().isEmpty()) {
                    value = FLUID;
                } else {
                    value = h;
                }
            }
            cache.put(key, value);
            return value;
        }
    }

    /** The columns (offsets from the lowest north-west corner of the placed box) that the ground must carry: where the bottom layer is not "leave as is". */
    static List<int[]> footprintColumns(BuildingDesign design, Direction facing) {
        int turns = design.quarterTurns(facing);
        List<int[]> columns = new ArrayList<>();
        for (int z = 0; z < design.getSizeZ(); z++) {
            for (int x = 0; x < design.getSizeX(); x++) {
                if (design.cellAt(x, 0, z) != BuildingDesign.KEEP) {
                    BlockPos local = design.rotateLocal(x, 0, z, turns);
                    columns.add(new int[]{local.getX(), local.getZ()});
                }
            }
        }
        if (columns.isEmpty()) { // a design that leaves its whole bottom layer: the box itself
            int[] size = design.rotatedSize(facing);
            for (int z = 0; z < size[2]; z++) {
                for (int x = 0; x < size[0]; x++) {
                    columns.add(new int[]{x, z});
                }
            }
        }
        return columns;
    }

    /** The outcome of levelling a spot: the standing level and what it costs, or an error. */
    private record Ground(int y, double cost, String error) {
        boolean ok() {
            return error.isEmpty();
        }
    }

    private static Ground ground(Heights heights, List<int[]> columns, int ox, int oz) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int[] column : columns) {
            int h = heights.at(ox + column[0], oz + column[1]);
            if (h == INVALID) {
                return new Ground(0, 0, "unloaded");
            }
            if (h == FLUID) {
                return new Ground(0, 0, "water");
            }
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        int lo = max - SettlementConfig.terrainMaxCut;
        int hi = min + SettlementConfig.terrainMaxFill;
        if (lo > hi) {
            return new Ground(0, 0, "uneven");
        }
        int[] histogram = new int[max - min + 1];
        for (int[] column : columns) {
            histogram[heights.at(ox + column[0], oz + column[1]) - min]++;
        }
        double bestCost = Double.MAX_VALUE;
        int bestY = lo;
        for (int g = lo; g <= hi; g++) {
            double cost = 0;
            for (int i = 0; i < histogram.length; i++) {
                int h = min + i;
                cost += histogram[i] * (g > h ? (g - h) * 1.5D : (h - g));
            }
            if (cost < bestCost) {
                bestCost = cost;
                bestY = g;
            }
        }
        return new Ground(bestY, bestCost / columns.size(), "");
    }

    private static List<BuildProject.Fill> fillsFor(ServerLevel level, Heights heights, List<int[]> columns, int ox, int oz, int g) {
        List<BuildProject.Fill> fills = new ArrayList<>();
        for (int[] column : columns) {
            int x = ox + column[0];
            int z = oz + column[1];
            int h = heights.at(x, z);
            if (h < g) {
                BlockState surface = level.getBlockState(new BlockPos(x, h - 1, z));
                fills.add(new BuildProject.Fill(new BlockPos(x, h - 1, z), BuiltInRegistries.BLOCK.getKey(surface.getBlock()).toString()));
            }
        }
        return fills;
    }

    // ---------------------------------------------------------------- what to keep clear

    /** The positions a building must not cover: the hall, storages, beds, worksites, boundary markers and gathering points. */
    private static List<BlockPos> keepClear(Settlement settlement) {
        List<BlockPos> points = new ArrayList<>();
        points.addAll(settlement.getStorages());
        points.addAll(settlement.getBeds());
        points.addAll(settlement.getMarkers());
        points.addAll(settlement.getGatherPoints());
        for (JobBoard.Job job : settlement.getJobBoard().getJobs()) {
            points.add(job.pos);
        }
        return points;
    }

    private static boolean hitsPoint(List<BlockPos> points, BlockPos hall, int x1, int z1, int x2, int z2) {
        if (hall.getX() >= x1 - HALL_MARGIN && hall.getX() <= x2 + HALL_MARGIN && hall.getZ() >= z1 - HALL_MARGIN && hall.getZ() <= z2 + HALL_MARGIN) {
            return true;
        }
        for (BlockPos p : points) {
            if (p.getX() >= x1 - 1 && p.getX() <= x2 + 1 && p.getZ() >= z1 - 1 && p.getZ() <= z2 + 1) {
                return true;
            }
        }
        return false;
    }

    private static boolean hitsProject(Settlement settlement, int x1, int z1, int x2, int z2) {
        for (BuildProject project : settlement.getBuilding().getProjects()) {
            if (project.overlaps(x1, z1, x2, z2, SettlementConfig.buildingGap)) {
                return true;
            }
        }
        return false;
    }

    /** True if a block entity (a chest, a worksite, a bed and so on) stands in the box, or a chunk of it is not loaded. */
    private static boolean hazard(ServerLevel level, BlockPos origin, int[] size) {
        int x2 = origin.getX() + size[0] - 1;
        int z2 = origin.getZ() + size[2] - 1;
        int y2 = origin.getY() + size[1] - 1;
        if (y2 >= level.getMaxBuildHeight() || origin.getY() <= level.getMinBuildHeight()) {
            return true;
        }
        for (int cx = origin.getX() >> 4; cx <= x2 >> 4; cx++) {
            for (int cz = origin.getZ() >> 4; cz <= z2 >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    return true;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos p = be.getBlockPos();
                    if (p.getX() >= origin.getX() && p.getX() <= x2 && p.getZ() >= origin.getZ() && p.getZ() <= z2 && p.getY() >= origin.getY() && p.getY() <= y2) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Direction towardHall(BlockPos hall, double cx, double cz) {
        double dx = hall.getX() + 0.5D - cx;
        double dz = hall.getZ() + 0.5D - cz;
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static double facingPenalty(Direction facing, Direction best) {
        if (facing == best) {
            return 0.0D;
        }
        return facing == best.getOpposite() ? 3.0D : 1.5D;
    }

    // ---------------------------------------------------------------- search

    private record Candidate(BuildingPlot plot, int x, int z, Direction facing, int y, double score) {}

    /**
     * The best place for the design in any plot of the settlement, or the reason there is none. Considers every plot, every turn and every position;
     * above {@link #STRIDE_ABOVE} positions in one plot and turn every second position is tried.
     */
    public static Result find(ServerLevel level, Settlement settlement, BuildingDesign design) {
        List<BuildingPlot> plots = settlement.getBuilding().getPlots();
        if (plots.isEmpty()) {
            return Result.fail("no_plots");
        }
        Heights heights = new Heights(level);
        List<BlockPos> points = keepClear(settlement);
        BlockPos hall = settlement.getHallPos();
        List<Candidate> best = new ArrayList<>();
        boolean fitsSomewhere = false;
        String lastError = "no_room";
        for (BuildingPlot plot : plots) {
            for (Direction facing : FACINGS) {
                int[] size = design.rotatedSize(facing);
                int w = size[0];
                int d = size[2];
                if (plot.getWidth() < w || plot.getDepth() < d) {
                    continue;
                }
                fitsSomewhere = true;
                List<int[]> columns = footprintColumns(design, facing);
                int xs = plot.getMaxX() - w + 1 - plot.getMinX() + 1;
                int zs = plot.getMaxZ() - d + 1 - plot.getMinZ() + 1;
                int stride = (long) xs * zs > STRIDE_ABOVE ? 2 : 1;
                for (int x = plot.getMinX(); x + w - 1 <= plot.getMaxX(); x += stride) {
                    for (int z = plot.getMinZ(); z + d - 1 <= plot.getMaxZ(); z += stride) {
                        int x2 = x + w - 1;
                        int z2 = z + d - 1;
                        if (hitsProject(settlement, x, z, x2, z2) || hitsPoint(points, hall, x, z, x2, z2)) {
                            lastError = "overlap";
                            continue;
                        }
                        Ground ground = ground(heights, columns, x, z);
                        if (!ground.ok()) {
                            lastError = ground.error();
                            continue;
                        }
                        double cx = x + w / 2.0D;
                        double cz = z + d / 2.0D;
                        double dist = Math.sqrt((cx - hall.getX()) * (cx - hall.getX()) + (cz - hall.getZ()) * (cz - hall.getZ()));
                        double score = ground.cost() * 2.0D + dist * 0.15D + facingPenalty(facing, towardHall(hall, cx, cz));
                        keep(best, new Candidate(plot, x, z, facing, ground.y(), score));
                    }
                }
            }
        }
        if (!fitsSomewhere) {
            return Result.fail("too_big");
        }
        for (Candidate c : best) {
            int[] size = design.rotatedSize(c.facing());
            BlockPos origin = new BlockPos(c.x(), c.y(), c.z());
            if (hazard(level, origin, size)) {
                lastError = "hazard";
                continue;
            }
            List<BuildProject.Fill> fills = fillsFor(level, heights, footprintColumns(design, c.facing()), c.x(), c.z(), c.y());
            return Result.ok(new Site(c.plot(), origin, c.facing(), fills, c.score()));
        }
        return Result.fail(lastError);
    }

    private static void keep(List<Candidate> best, Candidate candidate) {
        int index = best.size();
        while (index > 0 && best.get(index - 1).score() > candidate.score()) {
            index--;
        }
        if (index >= MAX_CANDIDATES_KEPT) {
            return;
        }
        best.add(index, candidate);
        if (best.size() > MAX_CANDIDATES_KEPT) {
            best.remove(best.size() - 1);
        }
    }

    /**
     * Checks one chosen place (the owner picked the plot, the turn and the position on the Building tab): the lowest north-west corner is the column
     * {@code x}, {@code z}; the height comes from the ground.
     */
    public static Result check(ServerLevel level, Settlement settlement, BuildingDesign design, BuildingPlot plot, Direction facing, int x, int z) {
        int[] size = design.rotatedSize(facing);
        int x2 = x + size[0] - 1;
        int z2 = z + size[2] - 1;
        if (!plot.containsRect(x, z, x2, z2)) {
            return Result.fail("outside_plot");
        }
        if (hitsProject(settlement, x, z, x2, z2)) {
            return Result.fail("overlap");
        }
        if (hitsPoint(keepClear(settlement), settlement.getHallPos(), x, z, x2, z2)) {
            return Result.fail("occupied");
        }
        Heights heights = new Heights(level);
        List<int[]> columns = footprintColumns(design, facing);
        Ground ground = ground(heights, columns, x, z);
        if (!ground.ok()) {
            return Result.fail(ground.error());
        }
        BlockPos origin = new BlockPos(x, ground.y(), z);
        if (hazard(level, origin, size)) {
            return Result.fail("hazard");
        }
        return Result.ok(new Site(plot, origin, facing, fillsFor(level, heights, columns, x, z, ground.y()), ground.cost()));
    }
}
