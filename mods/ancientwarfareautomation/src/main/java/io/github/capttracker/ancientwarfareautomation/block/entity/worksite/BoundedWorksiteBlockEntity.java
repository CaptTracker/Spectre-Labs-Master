/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/TileWorksiteBounded.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import io.github.capttracker.ancientwarfare.api.IBoundedSite;
import io.github.capttracker.ancientwarfare.api.IChunkLoaderTile;
import io.github.capttracker.ancientwarfare.upgrade.WorksiteUpgrade;
import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfareautomation.chunkloader.ChunkTicketSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Set;

/**
 * A worksite with a rectangular work area ({@link IBoundedSite}) that can be chunk loaded by an upgrade.
 *
 * <p><b>Bounds</b>: {@code bbMin}/{@code bbMax} are block positions; {@link BlockPos#ZERO} means "not set" (the
 * original used {@code BlockPos.ORIGIN}, compared by identity). They are saved as longs and sent to clients.
 * {@code ItemBlockWorksiteStatic} (the {@code WorksiteBlockItem}) sets them when the block is placed. When a block
 * entity loads without saved bounds it gets the single position behind itself on the Z axis ({@code pos + (0, 0, 1)}),
 * as in the original.
 *
 * <p><b>Chunk loading</b> (the {@code BASIC_CHUNK_LOADER} and {@code QUARRY_CHUNK_LOADER} upgrades): the block entity
 * forces its own chunk and, through {@link #chunkLoadWorkBounds()}, every chunk its bounds touch, using the
 * {@code AWChunkLoader} ticket controller. The forced chunks are remembered in a {@link ChunkTicketSet} saved with the
 * block entity. The quarry overrides {@link #chunkLoadWorkBounds()} to force only the chunk it is mining and uses
 * {@link #forceChunk}/{@link #unforceChunk} to move along (1.12.2: {@code ForgeChunkManager.forceChunk(chunkTicket, ...)}).
 */
public abstract class BoundedWorksiteBlockEntity extends WorksiteBlockEntity implements IBoundedSite, IChunkLoaderTile {
    private static final String BB_MIN_TAG = "bbMin";
    private static final String BB_MAX_TAG = "bbMax";
    private static final String Y_RANGE_TAG = "yRange";
    /** The most blocks above and below the site that a vertical range can reach. */
    public static final int MAX_VERTICAL_RANGE = 4;

    /* minimum position of the work area bounding box; ZERO if not set */
    private BlockPos bbMin = BlockPos.ZERO;

    /* maximum position of the work bounding box; ZERO if not set */
    private BlockPos bbMax = BlockPos.ZERO;

    /* blocks above and below the site that the work area reaches (only for sites that say supportsVerticalRange) */
    private int verticalRange = MAX_VERTICAL_RANGE;

    private final ChunkTicketSet chunkTickets = new ChunkTicketSet();

    protected BoundedWorksiteBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public Set<WorksiteUpgrade> getValidUpgrades() {
        return EnumSet.of(WorksiteUpgrade.ENCHANTED_TOOLS_1, WorksiteUpgrade.ENCHANTED_TOOLS_2, WorksiteUpgrade.SIZE_MEDIUM, WorksiteUpgrade.SIZE_LARGE,
                WorksiteUpgrade.TOOL_QUALITY_1, WorksiteUpgrade.TOOL_QUALITY_2, WorksiteUpgrade.TOOL_QUALITY_3, WorksiteUpgrade.BASIC_CHUNK_LOADER);
    }

    @Override
    public void onBlockBroken(BlockState state) {
        super.onBlockBroken(state);
        if (level instanceof ServerLevel serverLevel) {
            releaseChunkTickets(serverLevel);
        }
    }

    @Override
    public void addUpgrade(WorksiteUpgrade upgrade) {
        super.addUpgrade(upgrade);
        if (upgrade == WorksiteUpgrade.BASIC_CHUNK_LOADER || upgrade == WorksiteUpgrade.QUARRY_CHUNK_LOADER) {
            setupInitialTicket();//setup chunkloading for the worksite
        }
    }

    @Override
    public final void removeUpgrade(WorksiteUpgrade upgrade) {
        super.removeUpgrade(upgrade);
        if (upgrade == WorksiteUpgrade.BASIC_CHUNK_LOADER || upgrade == WorksiteUpgrade.QUARRY_CHUNK_LOADER) {
            if (level instanceof ServerLevel serverLevel) {
                releaseChunkTickets(serverLevel);//release any existing tickets
            }
        }
    }

    @Override
    public final boolean hasWorkBounds() {
        return !bbMin.equals(BlockPos.ZERO) && !bbMax.equals(BlockPos.ZERO);
    }

    @Override
    public final BlockPos getWorkBoundsMin() {
        return bbMin;
    }

    @Override
    public final BlockPos getWorkBoundsMax() {
        return bbMax;
    }

    @Override
    public final void setBounds(BlockPos min, BlockPos max) {
        setWorkBoundsMin(BlockTools.getMin(min, max));
        setWorkBoundsMax(BlockTools.getMax(min, max));
        invalidateTasks();
        onBoundsSet();
    }

    @Override
    public int getBoundsMaxWidth() {
        if (getUpgrades().contains(WorksiteUpgrade.SIZE_LARGE)) {
            return 16;
        } else if (getUpgrades().contains(WorksiteUpgrade.SIZE_MEDIUM)) {
            return 9;
        } else {
            return 5;
        }
    }

    @Override
    public int getBoundsMaxHeight() {
        return 1;
    }

    // ************************************** VERTICAL RANGE ***************************************//

    /**
     * True for the sites that work the terrain around them (rework phase 3: the farms): their work area is the bounds horizontally and
     * {@link #getVerticalRange()} blocks above and below the site vertically, settable in the bounds screen.
     */
    public boolean supportsVerticalRange() {
        return false;
    }

    /** Blocks above and below the site that are worked, 0 to {@value #MAX_VERTICAL_RANGE} (4 by default). */
    public final int getVerticalRange() {
        return verticalRange;
    }

    /** Sets the vertical range (clamped to 0..4); revalidates what the site has queued. */
    public final void setVerticalRange(int range) {
        int clamped = Math.max(0, Math.min(MAX_VERTICAL_RANGE, range));
        if (clamped != verticalRange) {
            verticalRange = clamped;
            invalidateTasks();
            setChanged();
            syncToClients();
            onBoundsAdjusted();
        }
    }

    /** The lowest block Y of the work area of a site with a vertical range. */
    public final int getScanMinY() {
        return worldPosition.getY() - verticalRange;
    }

    /** The highest block Y of the work area of a site with a vertical range. */
    public final int getScanMaxY() {
        return worldPosition.getY() + verticalRange;
    }

    // ************************************** CHUNK LOADING ***************************************//

    /** Forces the chunk of the block entity and the chunks of the work bounds. Called when a chunk loader upgrade is installed and after bounds changes. */
    @Override
    public void requestChunkTickets(ServerLevel level) {
        forceChunk(new ChunkPos(worldPosition));
        if (hasWorkBounds()) {
            chunkLoadWorkBounds();
        }
    }

    /** Stops forcing every chunk this block entity forced. */
    @Override
    public void releaseChunkTickets(ServerLevel level) {
        chunkTickets.releaseAll(level, worldPosition);
        setChanged();
    }

    /** Forces every chunk the work bounds touch (if the chunk loader upgrade is installed, which the caller checks). */
    protected void chunkLoadWorkBounds() {
        int minX = getWorkBoundsMin().getX() >> 4;
        int minZ = getWorkBoundsMin().getZ() >> 4;
        int maxX = getWorkBoundsMax().getX() >> 4;
        int maxZ = getWorkBoundsMax().getZ() >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                forceChunk(new ChunkPos(x, z));
            }
        }
    }

    /** Forces one chunk for this block entity. Server only (does nothing on the client). */
    protected final void forceChunk(ChunkPos chunk) {
        if (level instanceof ServerLevel serverLevel) {
            chunkTickets.force(serverLevel, worldPosition, chunk);
            setChanged();
        }
    }

    /** Stops forcing one chunk for this block entity. Server only. */
    protected final void unforceChunk(ChunkPos chunk) {
        if (level instanceof ServerLevel serverLevel) {
            chunkTickets.unforce(serverLevel, worldPosition, chunk);
            setChanged();
        }
    }

    /** Releases the forced chunks and, if a chunk loader upgrade is installed, forces them again (the bounds may have changed). */
    protected void setupInitialTicket() {
        if (level instanceof ServerLevel serverLevel) {
            releaseChunkTickets(serverLevel);
            if (hasChunkLoaderUpgrade()) {
                requestChunkTickets(serverLevel);
            }
        }
    }

    protected boolean hasChunkLoaderUpgrade() {
        return getUpgrades().contains(WorksiteUpgrade.BASIC_CHUNK_LOADER) || getUpgrades().contains(WorksiteUpgrade.QUARRY_CHUNK_LOADER);
    }

    // ************************************** BOUNDS HOOKS ***************************************//

    /*
     * Used by the user-set-blocks farms to set all default harvest-checks to true when bounds are FIRST set
     */
    protected void onBoundsSet() {

    }

    @Override
    public void onBoundsAdjusted() {
        //implement in subclasses to check target blocks, clear invalid ones
    }

    @Override
    public void onPostBoundsAdjusted() {
        setupInitialTicket();
    }

    /** Whether the position is inside the bounds, horizontally (the height is not checked). */
    protected boolean isInBounds(BlockPos pos) {
        return pos.getX() >= bbMin.getX() && pos.getX() <= bbMax.getX() && pos.getZ() >= bbMin.getZ() && pos.getZ() <= bbMax.getZ();
    }

    /** Removes the positions that are not inside the bounds (all of them if the bounds are not set). */
    protected void validateCollection(Collection<BlockPos> blocks) {
        if (!hasWorkBounds()) {
            blocks.clear();
            return;
        }
        Iterator<BlockPos> it = blocks.iterator();
        BlockPos pos;
        while (it.hasNext() && (pos = it.next()) != null) {
            if (!isInBounds(pos)) {
                it.remove();
            }
        }
    }

    @Override
    public final void setWorkBoundsMin(BlockPos min) {
        if (!min.equals(bbMin)) {
            bbMin = min;
            setChanged();
            syncToClients();
        }
    }

    @Override
    public final void setWorkBoundsMax(BlockPos max) {
        if (!max.equals(bbMax)) {
            bbMax = max;
            setChanged();
            syncToClients();
        }
    }

    /**
     * The box a renderer should draw for the work area: the bounds plus the block itself (1.12.2
     * {@code getRenderBoundingBox}). A block entity renderer returns true from {@code shouldRenderOffScreen}
     * so the box stays visible when the block itself is out of view.
     */
    public AABB getRenderBounds() {
        if (hasWorkBounds()) {
            BlockPos min = getWorkBoundsMin();
            int minX = Math.min(min.getX(), worldPosition.getX());
            int minY = Math.min(supportsVerticalRange() ? getScanMinY() : min.getY(), worldPosition.getY());
            int minZ = Math.min(min.getZ(), worldPosition.getZ());
            BlockPos max = getWorkBoundsMax();
            int maxX = Math.max(max.getX() + 1, worldPosition.getX() + 1);
            int maxY = Math.max(supportsVerticalRange() ? getScanMaxY() + 1 : max.getY() + 1, worldPosition.getY() + 1);
            int maxZ = Math.max(max.getZ() + 1, worldPosition.getZ() + 1);

            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }
        return new AABB(worldPosition);
    }

    // ************************************** NBT AND PACKET DATA METHODS ***************************************//

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(BB_MIN_TAG)) {
            bbMin = BlockPos.of(tag.getLong(BB_MIN_TAG));
        }
        if (tag.contains(BB_MAX_TAG)) {
            bbMax = BlockPos.of(tag.getLong(BB_MAX_TAG));
        }
        if (bbMax.equals(BlockPos.ZERO)) {
            bbMax = worldPosition.offset(0, 0, 1);
        }
        if (bbMin.equals(BlockPos.ZERO)) {
            bbMin = worldPosition.offset(0, 0, 1);
        }
        if (tag.contains(Y_RANGE_TAG)) {
            verticalRange = Math.max(0, Math.min(MAX_VERTICAL_RANGE, tag.getInt(Y_RANGE_TAG)));
        }
        chunkTickets.load(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!bbMin.equals(BlockPos.ZERO)) {
            tag.putLong(BB_MIN_TAG, bbMin.asLong());
        }
        if (!bbMax.equals(BlockPos.ZERO)) {
            tag.putLong(BB_MAX_TAG, bbMax.asLong());
        }
        tag.putInt(Y_RANGE_TAG, verticalRange);
        chunkTickets.save(tag);
    }

    @Override
    protected void writeUpdateNBT(CompoundTag tag) {
        super.writeUpdateNBT(tag);
        tag.putInt(Y_RANGE_TAG, verticalRange);
        if (!bbMin.equals(BlockPos.ZERO)) {
            tag.putLong(BB_MIN_TAG, bbMin.asLong());
        }
        if (!bbMax.equals(BlockPos.ZERO)) {
            tag.putLong(BB_MAX_TAG, bbMax.asLong());
        }
    }

    @Override
    protected void handleUpdateNBT(CompoundTag tag) {
        super.handleUpdateNBT(tag);
        if (tag.contains(Y_RANGE_TAG)) {
            verticalRange = Math.max(0, Math.min(MAX_VERTICAL_RANGE, tag.getInt(Y_RANGE_TAG)));
        }
        if (tag.contains(BB_MIN_TAG)) {
            bbMin = BlockPos.of(tag.getLong(BB_MIN_TAG));
        }
        if (tag.contains(BB_MAX_TAG)) {
            bbMax = BlockPos.of(tag.getLong(BB_MAX_TAG));
        }
    }
}
