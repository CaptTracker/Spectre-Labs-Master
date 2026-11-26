/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): a queued, running or finished build of a settlement.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;

/**
 * One build of a settlement: a design at a place and rotation inside a plot, what state it is in and how far the builder got. A {@link Kind#DEMOLISH}
 * project takes an earlier build down again (its blocks go back to the storage). Saved with the settlement. Server side only.
 *
 * <p>The steps of a project ({@link #steps}) are computed from the design: first the earth fills under the building, then the blocks of the design bottom
 * layer first. The project only keeps which steps are done (a bit set). If the design changed since (a reload) and has another number of steps, the
 * progress is dropped; a step whose block is already in the world is skipped by {@link BuildJobs}.
 */
public final class BuildProject {
    public enum Kind { BUILD, DEMOLISH }

    /**
     * {@code PLANNED}: waiting for the owner's approval. {@code QUEUED}: approved, no step done yet (it waits in the queue, and for its materials).
     * {@code BUILDING}: at least one step done. {@code DONE}: all steps done.
     */
    public enum Status { PLANNED, QUEUED, BUILDING, DONE }

    /** A column of ground to fill: the top solid block (the surface) and the block it is made of. */
    public record Fill(BlockPos surface, String top) {}

    private final UUID id;
    private final ResourceLocation designId;
    @Nullable
    private final UUID plotId;
    private final BlockPos origin;
    private final Direction facing;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final Kind kind;
    private Status status;
    private final String reason;
    /** For a build: the project this one replaces (taken down when this is done). For a demolition: the project taken down. */
    @Nullable
    private UUID target;
    private final long created;
    private final List<Fill> fills;
    private BitSet done = new BitSet();
    /** Steps whose block the builders paid for from storage (a block that was already there costs nothing and is not refunded on take-down). */
    private BitSet paid = new BitSet();
    private int doneCount;
    private int total;
    private boolean registered;

    @Nullable
    private transient List<BuildStep> steps;
    @Nullable
    private transient BuildingDesign stepsDesign;

    public BuildProject(UUID id, ResourceLocation designId, @Nullable UUID plotId, BlockPos origin, Direction facing, int[] rotatedSize, Kind kind, Status status,
                        String reason, @Nullable UUID target, long created, List<Fill> fills) {
        this.id = id;
        this.designId = designId;
        this.plotId = plotId;
        this.origin = origin.immutable();
        this.facing = facing;
        this.sizeX = rotatedSize[0];
        this.sizeY = rotatedSize[1];
        this.sizeZ = rotatedSize[2];
        this.kind = kind;
        this.status = status;
        this.reason = reason;
        this.target = target;
        this.created = created;
        this.fills = List.copyOf(fills);
    }

    // ---------------------------------------------------------------- accessors

    public UUID getId() {
        return id;
    }

    public ResourceLocation getDesignId() {
        return designId;
    }

    @Nullable
    public UUID getPlotId() {
        return plotId;
    }

    /** The lowest north-west corner of the placed design (the standing level is {@code origin.y}). */
    public BlockPos getOrigin() {
        return origin;
    }

    public Direction getFacing() {
        return facing;
    }

    /** The size {x, y, z} of the placed design (already turned). */
    public int[] getSize() {
        return new int[]{sizeX, sizeY, sizeZ};
    }

    public int getMaxX() {
        return origin.getX() + sizeX - 1;
    }

    public int getMaxZ() {
        return origin.getZ() + sizeZ - 1;
    }

    public Kind getKind() {
        return kind;
    }

    public Status getStatus() {
        return status;
    }

    /** Why it was made: homeless, storage, chapel, upgrade, manual, business.TAG or role.ROLE. */
    public String getReason() {
        return reason;
    }

    @Nullable
    public UUID getTarget() {
        return target;
    }

    public long getCreated() {
        return created;
    }

    public List<Fill> getFills() {
        return fills;
    }

    public boolean isDone() {
        return status == Status.DONE;
    }

    /** Whether the project is not finished yet (planned, queued or under construction). */
    public boolean isOpen() {
        return status != Status.DONE;
    }

    boolean isRegistered() {
        return registered;
    }

    void setRegistered(boolean registered) {
        this.registered = registered;
    }

    void setTarget(@Nullable UUID target) {
        this.target = target;
    }

    void setStatus(Status status) {
        this.status = status;
    }

    /** Whether the footprint (plus {@code gap} blocks around it) touches the rectangle. */
    public boolean overlaps(int x1, int z1, int x2, int z2, int gap) {
        return origin.getX() - gap <= x2 && getMaxX() + gap >= x1 && origin.getZ() - gap <= z2 && getMaxZ() + gap >= z1;
    }

    // ---------------------------------------------------------------- steps and progress

    /** The steps in order. Computed once for a design object; the design must be the one of {@link #getDesignId()}. */
    public List<BuildStep> steps(BuildingDesign design) {
        List<BuildStep> cached = steps;
        if (cached != null && stepsDesign == design) {
            return cached;
        }
        List<BuildStep> list = new ArrayList<>();
        if (kind == Kind.BUILD) {
            for (Fill fill : fills) {
                BlockPos surface = fill.surface();
                for (int y = surface.getY() + 1; y < origin.getY(); y++) {
                    list.add(new BuildStep(list.size(), BuildStep.Kind.FILL, new BlockPos(surface.getX(), y, surface.getZ()), fillState(fill, y == origin.getY() - 1)));
                }
            }
            for (BuildingDesign.Placement placement : design.blocks(origin, facing)) {
                list.add(new BuildStep(list.size(), placement.state().isAir() ? BuildStep.Kind.CLEAR : BuildStep.Kind.PLACE, placement.pos(), placement.state()));
            }
        } else {
            List<BuildingDesign.Placement> all = design.blocks(origin, facing);
            for (int i = all.size() - 1; i >= 0; i--) {
                BuildingDesign.Placement placement = all.get(i);
                if (!placement.state().isAir()) {
                    list.add(new BuildStep(list.size(), BuildStep.Kind.REMOVE, placement.pos(), placement.state()));
                }
            }
        }
        if (total != list.size()) {
            if (total != 0) {
                done = new BitSet(); // the design changed since the progress was saved
                paid = new BitSet();
                doneCount = 0;
            }
            total = list.size();
        }
        steps = list;
        stepsDesign = design;
        return list;
    }

    /** The ground block to put at a height of a fill column: dirt under the original top block, the top block itself (grass and so on) at the top. */
    private static BlockState fillState(Fill fill, boolean topmost) {
        ResourceLocation id = ResourceLocation.tryParse(fill.top());
        Block block = id == null ? Blocks.DIRT : BuiltInRegistries.BLOCK.get(id);
        BlockState state = block.defaultBlockState();
        if (state.isAir() || !state.getFluidState().isEmpty() || !state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
            return Blocks.DIRT.defaultBlockState();
        }
        boolean dirtLike = block == Blocks.GRASS_BLOCK || block == Blocks.PODZOL || block == Blocks.MYCELIUM || block == Blocks.DIRT_PATH || block == Blocks.FARMLAND
                || block == Blocks.COARSE_DIRT || block == Blocks.ROOTED_DIRT;
        if (dirtLike && !topmost) {
            return Blocks.DIRT.defaultBlockState();
        }
        return state;
    }

    public boolean isStepDone(int index) {
        return done.get(index);
    }

    /** Marks a step as done. Returns true if it was not done before. */
    boolean markStepDone(int index) {
        if (index < 0 || done.get(index)) {
            return false;
        }
        done.set(index);
        doneCount++;
        return true;
    }

    /** Records that the block of a step was paid for with materials from storage. */
    void markPaid(int index) {
        if (index >= 0) {
            paid.set(index);
        }
    }

    /** Whether the block this build placed at a position was paid for (so taking it down may refund it). */
    public boolean isPaidAt(BlockPos pos, BuildingDesign design) {
        List<BuildStep> list = steps(design);
        for (BuildStep step : list) {
            if (step.pos().equals(pos) && step.kind() != BuildStep.Kind.REMOVE && step.kind() != BuildStep.Kind.CLEAR) {
                if (paid.get(step.index())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The index of the first step that is not done, or -1 when all are. */
    public int firstOpenStep(int stepCount) {
        int next = done.nextClearBit(0);
        return next >= stepCount ? -1 : next;
    }

    public int getDoneCount() {
        return doneCount;
    }

    /** The number of steps as of the last time they were computed (0 before). */
    public int getTotal() {
        return total;
    }

    /** Progress 0 to 100. */
    public int getProgress() {
        if (status == Status.DONE) {
            return 100;
        }
        return total <= 0 ? 0 : Math.min(100, doneCount * 100 / total);
    }

    // ---------------------------------------------------------------- saving

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("design", designId.toString());
        if (plotId != null) {
            tag.putUUID("plot", plotId);
        }
        tag.put("origin", NbtUtils.writeBlockPos(origin));
        tag.putString("facing", facing.getName());
        tag.putIntArray("size", new int[]{sizeX, sizeY, sizeZ});
        tag.putString("kind", kind.name());
        tag.putString("status", status.name());
        tag.putString("reason", reason);
        if (target != null) {
            tag.putUUID("target", target);
        }
        tag.putLong("created", created);
        tag.putLongArray("done", done.toLongArray());
        tag.putLongArray("paid", paid.toLongArray());
        tag.putInt("doneCount", doneCount);
        tag.putInt("total", total);
        tag.putBoolean("registered", registered);
        ListTag fillList = new ListTag();
        for (Fill fill : fills) {
            CompoundTag f = new CompoundTag();
            f.put("pos", NbtUtils.writeBlockPos(fill.surface()));
            f.putString("top", fill.top());
            fillList.add(f);
        }
        tag.put("fills", fillList);
        return tag;
    }

    @Nullable
    public static BuildProject load(CompoundTag tag) {
        ResourceLocation design = ResourceLocation.tryParse(tag.getString("design"));
        BlockPos origin = NbtUtils.readBlockPos(tag, "origin").orElse(null);
        Direction facing = Direction.byName(tag.getString("facing"));
        int[] size = tag.getIntArray("size");
        if (!tag.hasUUID("id") || design == null || origin == null || facing == null || size.length != 3) {
            return null;
        }
        Kind kind;
        Status status;
        try {
            kind = Kind.valueOf(tag.getString("kind"));
            status = Status.valueOf(tag.getString("status"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        List<Fill> fills = new ArrayList<>();
        ListTag fillList = tag.getList("fills", Tag.TAG_COMPOUND);
        for (int i = 0; i < fillList.size(); i++) {
            CompoundTag f = fillList.getCompound(i);
            NbtUtils.readBlockPos(f, "pos").ifPresent(pos -> fills.add(new Fill(pos, f.getString("top"))));
        }
        BuildProject project = new BuildProject(tag.getUUID("id"), design, tag.hasUUID("plot") ? tag.getUUID("plot") : null, origin, facing, size, kind, status,
                tag.getString("reason"), tag.hasUUID("target") ? tag.getUUID("target") : null, tag.getLong("created"), fills);
        project.done = BitSet.valueOf(tag.getLongArray("done"));
        project.paid = BitSet.valueOf(tag.getLongArray("paid"));
        project.doneCount = tag.getInt("doneCount");
        project.total = tag.getInt("total");
        project.registered = tag.getBoolean("registered");
        return project;
    }
}
