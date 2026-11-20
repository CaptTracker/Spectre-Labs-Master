/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/TileChunkLoaderDeluxe.
 */
package io.github.capttracker.ancientwarfareautomation.chunkloader;

import io.github.capttracker.ancientwarfare.api.IInteractableTile;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import io.github.capttracker.ancientwarfareautomation.menu.ChunkLoaderDeluxeMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The deluxe chunk loader: forces its own chunk plus a set of chunks the player picks in its screen.
 *
 * <p>The screen's menu is {@code menu.ChunkLoaderDeluxeMenu(int, Inventory, ChunkLoaderDeluxeBlockEntity)} (the
 * GUI slice writes it; it implements {@link Viewer} and registers itself with {@link #addViewer}).
 */
public class ChunkLoaderDeluxeBlockEntity extends ChunkLoaderSimpleBlockEntity implements IInteractableTile {
    private static final String CHUNK_LIST_TAG = "chunkList";

    /** Somebody watching the chunk set (an open menu); told whenever it changes. */
    public interface Viewer {
        void onChunkLoaderSetUpdated(Set<ChunkPos> chunks);
    }

    private final Set<ChunkPos> ccipSet = new HashSet<>();

    private final List<Viewer> viewers = new ArrayList<>();

    public ChunkLoaderDeluxeBlockEntity(BlockPos pos, BlockState state) {
        super(AwAutomationBase.CHUNK_LOADER_DELUXE_BE.get(), pos, state);
    }

    @Override
    public boolean onBlockClicked(Player player, @Nullable InteractionHand hand) {
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                    new SimpleMenuProvider((id, inventory, p) -> new ChunkLoaderDeluxeMenu(id, inventory, this), getBlockState().getBlock().getName()),
                    buf -> buf.writeBlockPos(worldPosition));
        }
        return true;
    }

    public void addViewer(Viewer viewer) {
        viewers.add(viewer);
    }

    public void removeViewer(Viewer viewer) {
        viewers.remove(viewer);
    }

    /** Toggles a chunk of the user selected set (server side). */
    public void addOrRemoveChunk(ChunkPos ccip) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (ccipSet.contains(ccip)) {
            ccipSet.remove(ccip);
            // The own chunk stays forced: the original unforced it here as well, leaving the loader's own chunk unloaded.
            if (!ccip.equals(new ChunkPos(worldPosition))) {
                tickets.unforce(serverLevel, worldPosition, ccip);
            }
        } else {
            ccipSet.add(ccip);
            tickets.force(serverLevel, worldPosition, ccip);
        }
        setChanged();
        informViewers();
    }

    private void informViewers() {
        for (Viewer viewer : new ArrayList<>(viewers)) {
            viewer.onChunkLoaderSetUpdated(getForcedChunks());
        }
    }

    /** The chunks the user selected (not counting the loader's own chunk unless it was selected too). */
    public Set<ChunkPos> getForcedChunks() {
        return new HashSet<>(ccipSet);
    }

    @Override
    protected void forceTicketChunks(ServerLevel level) {
        tickets.force(level, worldPosition, new ChunkPos(worldPosition));
        for (ChunkPos ccip : ccipSet) {
            tickets.force(level, worldPosition, ccip);
        }
        setChanged();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ListTag list = tag.getList(CHUNK_LIST_TAG, Tag.TAG_COMPOUND);
        ccipSet.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag ccipTag = list.getCompound(i);
            ccipSet.add(new ChunkPos(ccipTag.getInt("x"), ccipTag.getInt("z")));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (ChunkPos chunkPos : this.ccipSet) {
            CompoundTag ccipTag = new CompoundTag();
            ccipTag.putInt("x", chunkPos.x);
            ccipTag.putInt("z", chunkPos.z);
            list.add(ccipTag);
        }
        tag.put(CHUNK_LIST_TAG, list);
    }
}
