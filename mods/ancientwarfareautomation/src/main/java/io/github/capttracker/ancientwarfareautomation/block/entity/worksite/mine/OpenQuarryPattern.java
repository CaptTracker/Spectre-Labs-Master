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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The open quarry, today's pattern: one chunk sized slab at a time, layer by layer from the top of the area down, x then z inside the chunk, then the next
 * chunk of the area. The cursor is the position of the next block that may still be there. The tasks are the block at the cursor and the next diggable blocks
 * of its slab.
 */
public final class OpenQuarryPattern implements MinePattern {
    private static final int SCAN_BUDGET = 8192;
    private static final int LOOKAHEAD_STEPS = 512;

    private static final int MAX_SEALED = 1024;

    private BlockPos current = BlockPos.ZERO;
    private boolean finished;
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
