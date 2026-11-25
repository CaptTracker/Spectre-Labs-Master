/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/WorkSiteQuarry (the cursor), as a mine pattern in rework phase 3.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.mine;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The open quarry, today's pattern: one chunk sized slab at a time, layer by layer from the top of the area down, x then z inside the chunk, then the next
 * chunk of the area. The cursor is the position of the next block that may still be there. The tasks are the block at the cursor and the next diggable blocks
 * of its slab.
 *
 * <p>Workers walk down into the pit, so each slab has an access column of ladders on its rim (a corner of the slab with a solid wall behind it, chosen when the
 * slab is started). Before any block of a layer is dug, the ladders of all the layers above it must stand; without ladders in the quarry's ladder slots the
 * quarry waits ({@link MineSite#waitingForLadder}).
 */
public final class OpenQuarryPattern implements MinePattern {
    private static final int SCAN_BUDGET = 8192;
    private static final int LOOKAHEAD_STEPS = 512;

    private static final int MAX_SEALED = 1024;

    private BlockPos current = BlockPos.ZERO;
    private boolean finished;
    /** The access column of the slab of the cursor (not saved: it is chosen again from the world), and the key of that slab. */
    private long accessSlab = Long.MIN_VALUE;
    @Nullable
    private BlockPos accessCell;
    private Direction accessFacing = Direction.EAST;
    /** Plugs of ours (cobblestone over lava or water): they stay, the cursor skips them. */
    private final LongLinkedOpenHashSet sealed = new LongLinkedOpenHashSet();

    @Override
    public MineMode mode() {
        return MineMode.OPEN_QUARRY;
    }

    @Override
    public void reset(MineSite site) {
        BlockPos min = site.boundsMin();
        BlockPos max = site.boundsMax();
        current = new BlockPos(min.getX(), max.getY(), min.getZ());
        finished = false;
        sealed.clear();
        accessCell = null;
        accessSlab = Long.MIN_VALUE;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public void plan(MineSite site, List<MineOp> out, int limit) {
        if (finished) {
            return;
        }
        if (current.equals(BlockPos.ZERO)) {
            reset(site);
        }
        int budget = SCAN_BUDGET;
        boolean cursorSeal = false;
        while (true) {
            chooseAccess(site);
            if (needsPlug(site, current)) {
                cursorSeal = true; // lava or water that could flow into the pit: plugged before the cursor goes on
                break;
            }
            if (diggable(site, current)) {
                break;
            }
            if (--budget <= 0) {
                return; // a long stretch of air: go on with the next call
            }
            if (!advance(site)) {
                finished = true;
                return;
            }
        }
        MineOp missing = missingLadder(site, current.getY());
        if (missing != null) {
            // the ladders above the cursor's layer come first; nothing is dug below the last ladder
            if (site.stock(Items.LADDER) > 0) {
                site.focus(missing.pos());
                out.add(missing);
            } else {
                site.waitingForLadder();
            }
            return;
        }
        site.focus(current);
        out.add(new MineOp(current, cursorSeal ? MineOp.Kind.SEAL : MineOp.Kind.DIG));
        BlockPos probe = current;
        for (int steps = 0; steps < LOOKAHEAD_STEPS && out.size() < limit; steps++) {
            probe = stepInChunk(site, probe);
            if (probe == null) {
                break;
            }
            if (needsPlug(site, probe)) {
                out.add(new MineOp(probe, MineOp.Kind.SEAL));
            } else if (diggable(site, probe)) {
                out.add(new MineOp(probe, MineOp.Kind.DIG));
            }
        }
        MineOp here = ladderAt(site, current.getY());
        if (here != null && site.stock(Items.LADDER) > 0 && out.size() < limit) {
            out.add(here); // the ladder of the layer being dug, as soon as its cell is open
        }
    }

    // ---------------------------------------------------------------- access ladders

    private static BlockState ladderState(Direction facing) {
        return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing);
    }

    /** Chooses the access column of the slab of the cursor: the corner and wall with the longest solid wall below the top of the area. */
    private void chooseAccess(MineSite site) {
        long key = ChunkPos.asLong(current.getX() >> 4, current.getZ() >> 4);
        if (key == accessSlab && accessCell != null) {
            return;
        }
        BlockPos min = site.boundsMin();
        BlockPos max = site.boundsMax();
        int x0 = Math.max(min.getX(), chunkMin(current.getX()));
        int x1 = Math.min(max.getX(), chunkMin(current.getX()) + 15);
        int z0 = Math.max(min.getZ(), chunkMin(current.getZ()));
        int z1 = Math.min(max.getZ(), chunkMin(current.getZ()) + 15);
        int[][] options = {{x0, z0, 0}, {x0, z0, 1}, {x1, z0, 2}, {x1, z0, 1}, {x0, z1, 0}, {x0, z1, 3}, {x1, z1, 2}, {x1, z1, 3}};
        Direction[] faces = {Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH};
        int bestScore = -1;
        for (int[] option : options) {
            Direction facing = faces[option[2]];
            int score = 0;
            for (int y = max.getY(); y >= site.openBottomY(); y--) {
                BlockPos cell = new BlockPos(option[0], y, option[1]);
                if (site.level().isLoaded(cell) && !ladderState(facing).canSurvive(site.level(), cell)) {
                    break;
                }
                score++;
            }
            if (score > bestScore) {
                bestScore = score;
                accessCell = new BlockPos(option[0], 0, option[1]);
                accessFacing = facing;
            }
        }
        accessSlab = key;
    }

    /** The op of the ladder at the layer, or null if it stands, its cell is not open or the wall is missing. */
    @Nullable
    private MineOp ladderAt(MineSite site, int y) {
        chooseAccess(site);
        BlockPos cell = new BlockPos(accessCell.getX(), y, accessCell.getZ());
        Level level = site.level();
        if (!level.isLoaded(cell) || !level.getBlockState(cell).isAir() || !ladderState(accessFacing).canSurvive(level, cell)) {
            return null;
        }
        return new MineOp(cell, MineOp.Kind.LADDER, accessFacing);
    }

    /** The highest missing ladder in the layers above {@code y} (so the next layer can be reached), or null if the column stands. */
    @Nullable
    private MineOp missingLadder(MineSite site, int y) {
        chooseAccess(site);
        for (int ly = site.boundsMax().getY(); ly > y; ly--) {
            MineOp op = ladderAt(site, ly);
            if (op != null) {
                return op;
            }
        }
        return null;
    }

    /** A loaded fluid cell of the slab that touches open space (where it would flow to) and has not been plugged yet. */
    private boolean needsPlug(MineSite site, BlockPos pos) {
        Level level = site.level();
        if (!level.isLoaded(pos) || sealed.contains(pos.asLong()) || level.getBlockState(pos).getFluidState().isEmpty()) {
            return false;
        }
        for (Direction dir : Direction.values()) {
            BlockPos side = pos.relative(dir);
            if (level.isLoaded(side)) {
                BlockState state = level.getBlockState(side);
                if (state.getFluidState().isEmpty() && state.getCollisionShape(level, side).isEmpty()) {
                    return true;
                }
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
        }
    }

    private boolean diggable(MineSite site, BlockPos pos) {
        if (!site.level().isLoaded(pos)) {
            return true; // wait for it rather than skip it
        }
        if (sealed.contains(pos.asLong())) {
            return false; // our plug stays
        }
        BlockState state = site.level().getBlockState(pos);
        if (state.is(Blocks.LADDER) && accessCell != null && pos.getX() == accessCell.getX() && pos.getZ() == accessCell.getZ()) {
            return false; // our access ladder stays
        }
        return site.canDig(pos, state);
    }

    /** The next position inside the current chunk's slab, or null at the bottom of the slab. */
    private static BlockPos stepInChunk(MineSite site, BlockPos p) {
        BlockPos min = site.boundsMin();
        BlockPos max = site.boundsMax();
        if (isMaxInChunk(p.getX()) || p.getX() >= max.getX()) {
            int startX = Math.max(min.getX(), chunkMin(p.getX()));
            if (isMaxInChunk(p.getZ()) || p.getZ() >= max.getZ()) {
                if (p.getY() <= site.openBottomY()) {
                    return null;
                }
                int startZ = Math.max(min.getZ(), chunkMin(p.getZ()));
                return new BlockPos(startX, p.getY() - 1, startZ);
            }
            return new BlockPos(startX, p.getY(), p.getZ() + 1);
        }
        return p.east();
    }

    private boolean advance(MineSite site) {
        BlockPos next = stepInChunk(site, current);
        if (next != null) {
            current = next;
            return true;
        }
        return moveToStartOfNextChunk(site);
    }

    private boolean moveToStartOfNextChunk(MineSite site) {
        BlockPos min = site.boundsMin();
        BlockPos max = site.boundsMax();
        int x = chunkMin(current.getX()) + 16;
        int z = Math.max(min.getZ(), chunkMin(current.getZ()));
        if (x > max.getX()) {
            x = min.getX();
            z = chunkMin(current.getZ()) + 16;
            if (z > max.getZ()) {
                return false;
            }
        }
        current = new BlockPos(x, max.getY(), z);
        return true;
    }

    private static int chunkMin(int coord) {
        return (coord >> 4) * 16;
    }

    private static boolean isMaxInChunk(int coord) {
        return (coord & 15) == 15;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putLong("cursor", current.asLong());
        tag.putBoolean("finished", finished);
        tag.put("sealed", new LongArrayTag(sealed.toLongArray()));
    }

    @Override
    public void load(CompoundTag tag) {
        current = BlockPos.of(tag.getLong("cursor"));
        finished = tag.getBoolean("finished");
        sealed.clear();
        if (tag.contains("sealed", Tag.TAG_LONG_ARRAY)) {
            for (long packed : tag.getLongArray("sealed")) {
                sealed.add(packed);
            }
        }
    }
}
