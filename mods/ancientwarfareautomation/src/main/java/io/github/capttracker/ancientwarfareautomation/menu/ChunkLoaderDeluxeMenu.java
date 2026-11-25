/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/container/ContainerChunkLoaderDeluxe.
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfareautomation.chunkloader.ChunkLoaderDeluxeBlockEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;

import java.util.HashSet;
import java.util.Set;

/**
 * The deluxe chunk loader: a 5 x 5 grid of the chunks around it, the ones the player has chosen to keep loaded are
 * marked. No slots. Protocol (original keys): the server sends {@code chunkList} (list of {@code x}, {@code z}) on open and
 * whenever the set changes; the client sends {@code forced} with {@code x} and {@code z} to toggle one chunk. The server
 * only toggles chunks of the grid around the block (the original toggled any chunk a client named) and never the centre
 * chunk, which is always loaded.
 */
public class ChunkLoaderDeluxeMenu extends AutomationMenu<ChunkLoaderDeluxeBlockEntity> implements ChunkLoaderDeluxeBlockEntity.Viewer {
    private static final String CHUNK_LIST = "chunkList";
    private static final String FORCED = "forced";
    private static final int RADIUS = 2;

    public Set<ChunkPos> ccipSet = new HashSet<>();

    /** Client side. */
    public ChunkLoaderDeluxeMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, ChunkLoaderDeluxeBlockEntity.class));
    }

    public ChunkLoaderDeluxeMenu(int containerId, Inventory inventory, ChunkLoaderDeluxeBlockEntity loader) {
        super(AwAutomationMenus.CHUNK_LOADER_DELUXE.get(), containerId, inventory, loader);
        if (!player.level().isClientSide) {
            ccipSet = loader.getForcedChunks();
            loader.addViewer(this);
        }
    }

    @Override
    public void removed(Player who) {
        blockEntity.removeViewer(this);
        super.removed(who);
    }

    @Override
    protected void sendInitialState() {
        sendChunkList();
    }

    private void sendChunkList() {
        ListTag list = new ListTag();
        for (ChunkPos chunkPos : this.ccipSet) {
            CompoundTag ccipTag = new CompoundTag();
            ccipTag.putInt("x", chunkPos.x);
            ccipTag.putInt("z", chunkPos.z);
            list.add(ccipTag);
        }
        CompoundTag tag = new CompoundTag();
        tag.put(CHUNK_LIST, list);
        sendDataToClient(tag);
    }

    @Override
    protected void handlePacketData(CompoundTag tag) {
        if (isClient()) {
            if (tag.contains(CHUNK_LIST, Tag.TAG_LIST)) {
                ccipSet.clear();
                ListTag list = tag.getList(CHUNK_LIST, Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag ccipTag = list.getCompound(i);
                    ccipSet.add(new ChunkPos(ccipTag.getInt("x"), ccipTag.getInt("z")));
                }
                refreshGui();
            }
            return;
        }
        if (!isRequestAllowed() || !tag.getBoolean(FORCED)) {
            return;
        }
        ChunkPos ccip = new ChunkPos(tag.getInt("x"), tag.getInt("z"));
        ChunkPos own = new ChunkPos(blockEntity.getBlockPos());
        boolean inGrid = Math.abs(ccip.x - own.x) <= RADIUS && Math.abs(ccip.z - own.z) <= RADIUS;
        if (inGrid && !ccip.equals(own)) {
            // The tile re-sends the whole list to its viewers (onChunkLoaderSetUpdated).
            blockEntity.addOrRemoveChunk(ccip);
        }
    }

    /** Client: asks the server to toggle a chunk. */
    public void force(ChunkPos ccip) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(FORCED, true);
        tag.putInt("x", ccip.x);
        tag.putInt("z", ccip.z);
        sendDataToServer(tag);
    }

    /** Server, from the block entity: the chunk set changed. */
    @Override
    public void onChunkLoaderSetUpdated(Set<ChunkPos> chunks) {
        this.ccipSet.clear();
        this.ccipSet.addAll(chunks);
        sendChunkList();
    }
}
