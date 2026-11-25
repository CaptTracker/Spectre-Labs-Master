/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 3, mine styles).
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.mine;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Base of the patterns that are a fixed sequence of slices (shaft: one layer; tunnels: the shaft layers, then one column of the tunnel with the branches
 * that start there). The saved cursor is the index of the first slice that still has work; the slice is judged from the world, so a reset or a restart
 * only costs a short rescan.
 *
 * <p>What the base does with the cells of the slices from the cursor on (a window of {@value #WINDOW}):
 * <ul>
 * <li>a cell that is a fluid is plugged with cobblestone and never dug (its position is remembered in {@code sealed}); so is any fluid that touches
 * a cell that is open;</li>
 * <li>a solid cell is dug if the tool can: only if it touches open space, or if it is the first one left (the worker has to start somewhere);</li>
 * <li>ladders are placed where the cell is open and the support is there; the workers depend on them to get down, so the slices below are not offered
 * until the ladder of the slice above stands, and when the quarry has none in its ladder slots the mine waits ({@link MineSite#waitingForLadder});
 * torches are placed when the quarry has them in stock, they never hold the digging back;</li>
 * <li>{@link #followsVeins() strip mines} also mine the ores that touch an open cell of a slice and, from what they mine, the ores around it.</li>
 * </ul>
 */
abstract class SlicePattern implements MinePattern {
    private static final int WINDOW = 2;
    private static final int MAX_SEALED = 1024;
    private static final int MAX_VEIN = 256;

    private final MineMode mode;
    private int cursor;
    private boolean finished;
    private final LongLinkedOpenHashSet sealed = new LongLinkedOpenHashSet();
    private final LongLinkedOpenHashSet vein = new LongLinkedOpenHashSet();

    protected SlicePattern(MineMode mode) {
        this.mode = mode;
    }

    @Override
    public final MineMode mode() {
        return mode;
    }

    /** The number of slices with the current area and Y. */
    protected abstract int sliceCount(MineSite site);

    /** The cells of one slice, in the order they are wanted. */
    protected abstract void cells(MineSite site, int slice, List<MineCell> out);

    protected boolean followsVeins() {
        return false;
    }

    @Override
    public void reset(MineSite site) {
        cursor = 0;
        finished = false;
        sealed.clear();
        vein.clear();
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    /** What one look at the slices found. */
    private static final class Found {
        final List<MineOp> seals = new ArrayList<>();
        final List<MineOp> veins = new ArrayList<>();
        final List<MineCell> digs = new ArrayList<>();
        final List<MineOp> ladders = new ArrayList<>();
        final List<MineOp> torches = new ArrayList<>();
        final Set<BlockPos> seen = new HashSet<>();
        int outstanding;
        /** A ladder of the slice is not there yet (and could be: its support is there). */
        boolean ladderPending;
    }

    @Override
    public void plan(MineSite site, List<MineOp> out, int limit) {
        if (finished) {
            return;
        }
        int total = sliceCount(site);
        int guard = 0;
        Found found = new Found();
        while (cursor < total) {
            found = new Found();
            examine(site, cursor, found);
            if (found.outstanding > 0 || ++guard > 64) {
                break;
            }
            cursor++;
        }
        if (cursor >= total) {
            finished = true;
            return;
        }
        if (!found.ladderPending) {
            for (int slice = cursor + 1; slice < Math.min(total, cursor + WINDOW); slice++) {
                examine(site, slice, found);
                if (found.ladderPending) {
                    break; // dig no deeper than the next ladder
                }
            }
        }
        examineVeinQueue(site, found);

        int ladderStock = site.stock(Items.LADDER);
        int torchStock = site.stock(Items.TORCH);
        out.addAll(found.seals);
        out.addAll(found.veins);
        boolean first = true;
        for (MineCell cell : found.digs) {
            if (first || touchesOpen(site.level(), cell.pos())) {
                out.add(new MineOp(cell.pos(), MineOp.Kind.DIG));
            }
            first = false;
        }
        for (MineOp op : found.ladders) {
            if (ladderStock-- > 0) {
                out.add(op);
            }
        }
        for (MineOp op : found.torches) {
            if (torchStock-- > 0) {
                out.add(op);
            }
        }
        if (found.ladderPending && ladderStock <= 0 && out.isEmpty()) {
            site.waitingForLadder();
        }
        if (out.size() > limit) {
            out.subList(limit, out.size()).clear();
        }
        if (!out.isEmpty()) {
            site.focus(out.get(0).pos());
        }
    }

    private void examine(MineSite site, int slice, Found found) {
        Level level = site.level();
        List<MineCell> cells = new ArrayList<>();
        cells(site, slice, cells);
        boolean torches = site.stock(Items.TORCH) > 0;
        for (MineCell cell : cells) {
            BlockPos pos = cell.pos();
            if (!level.isLoaded(pos)) {
                found.outstanding++;
                continue;
            }
            BlockState state = level.getBlockState(pos);
            switch (cell.kind()) {
                case DIG -> examineDig(site, cell, state, found);
                case LADDER -> {
                    MineOp op = new MineOp(pos, cell.kind(), cell.facing());
                    BlockState place = op.placeState();
                    if (place != null && !state.is(Blocks.LADDER) && place.canSurvive(level, pos) && (state.isAir() || site.canDig(pos, state))) {
                        found.ladderPending = true; // there is no ladder yet: nothing below it is dug before it stands
                        if (state.isAir()) {
                            found.outstanding++;
                            found.ladders.add(op);
                        }
                    }
                }
                case TORCH_FLOOR, TORCH_WALL -> {
                    MineOp op = new MineOp(pos, cell.kind(), cell.facing());
                    BlockState place = op.placeState();
                    if (torches && state.isAir() && place != null && place.canSurvive(level, pos)) {
                        found.outstanding++;
                        found.torches.add(op);
                    }
                }
                default -> {
                }
            }
        }
    }

    private void examineDig(MineSite site, MineCell cell, BlockState state, Found found) {
        BlockPos pos = cell.pos();
        if (!state.getFluidState().isEmpty()) {
            if (found.seen.add(pos)) {
                found.seals.add(new MineOp(pos, MineOp.Kind.SEAL));
                found.outstanding++;
            }
            return;
        }
        if (sealed.contains(pos.asLong())) {
            return; // a plug of ours stays
        }
        if (state.is(net.minecraft.world.level.block.Blocks.LADDER) || state.is(net.minecraft.world.level.block.Blocks.TORCH)
                || state.is(net.minecraft.world.level.block.Blocks.WALL_TORCH)) {
            exposure(site, pos, found); // our own ladder or torch: open space, kept
            return;
        }
        if (state.isAir()) {
            exposure(site, pos, found);
            return;
        }
        if (site.canDig(pos, state)) {
            found.outstanding++;
            found.digs.add(cell);
        }
    }

    /** An open cell: plugs the fluids around it, and finds the ores in its walls when the pattern follows veins. */
    private void exposure(MineSite site, BlockPos open, Found found) {
        Level level = site.level();
        for (Direction dir : Direction.values()) {
            BlockPos side = open.relative(dir);
            if (!level.isLoaded(side)) {
                continue;
            }
            BlockState state = level.getBlockState(side);
            if (!state.getFluidState().isEmpty()) {
                if (found.seen.add(side)) {
                    found.seals.add(new MineOp(side, MineOp.Kind.SEAL));
                    found.outstanding++;
                }
            } else if (followsVeins() && site.isOre(state) && site.canDig(side, state) && found.seen.add(side)) {
                found.veins.add(new MineOp(side, MineOp.Kind.VEIN));
                found.outstanding++;
            }
        }
    }

    /** The ores queued next to what the workers mined: they do not hold the cursor back. */
    private void examineVeinQueue(MineSite site, Found found) {
        if (vein.isEmpty()) {
            return;
        }
        Level level = site.level();
        LongArrayList gone = new LongArrayList();
        for (long packed : vein) {
            BlockPos pos = BlockPos.of(packed);
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!site.isOre(state) || !site.canDig(pos, state)) {
                gone.add(packed);
            } else if (found.seen.add(pos)) {
                found.veins.add(new MineOp(pos, MineOp.Kind.VEIN));
            }
        }
        vein.removeAll(gone);
    }

    private static boolean touchesOpen(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos side = pos.relative(dir);
            if (level.isLoaded(side) && level.getBlockState(side).getCollisionShape(level, side).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void completed(MineSite site, MineOp op) {
        if (op.kind() == MineOp.Kind.SEAL) {
            sealed.add(op.pos().asLong());
            while (sealed.size() > MAX_SEALED) {
                sealed.removeFirstLong();
            }
        } else if (followsVeins() && (op.kind() == MineOp.Kind.DIG || op.kind() == MineOp.Kind.VEIN)) {
            Level level = site.level();
            for (Direction dir : Direction.values()) {
                BlockPos side = op.pos().relative(dir);
                if (level.isLoaded(side) && site.isOre(level.getBlockState(side)) && vein.size() < MAX_VEIN) {
                    vein.add(side.asLong());
                }
            }
        }
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putInt("cursor", cursor);
        tag.putBoolean("finished", finished);
        tag.put("sealed", new LongArrayTag(sealed.toLongArray()));
        tag.put("vein", new LongArrayTag(vein.toLongArray()));
    }

    @Override
    public void load(CompoundTag tag) {
        cursor = tag.getInt("cursor");
        finished = tag.getBoolean("finished");
        sealed.clear();
        vein.clear();
        if (tag.contains("sealed", Tag.TAG_LONG_ARRAY)) {
            for (long packed : tag.getLongArray("sealed")) {
                sealed.add(packed);
            }
        }
        if (tag.contains("vein", Tag.TAG_LONG_ARRAY)) {
            for (long packed : tag.getLongArray("vein")) {
                vein.add(packed);
            }
        }
    }
}
