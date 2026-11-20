/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/container/ContainerNpcBard and ContainerNpcFactionBard (identical apart from the NPC class).
 */
package io.github.capttracker.ancientwarfarenpc.menu;

import io.github.capttracker.ancientwarfare.api.ISinger;
import io.github.capttracker.ancientwarfare.util.SongPlayData;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import javax.annotation.Nullable;

/**
 * The song list editor of a singing NPC ({@link ISinger}): 1.12.2 {@code GUI_NPC_BARD} (43) and {@code GUI_NPC_FACTION_BARD} (49). The whole
 * {@link SongPlayData} is one NBT {@code tuneData}: the server sends it when the menu opens, the client sends it back when the screen closes
 * (the last screen to close wins).
 *
 * <p>Changes from 1.12.2: the server only takes the data from a player that may edit the NPC and checks it first: at most
 * {@value #MAX_ENTRIES} tunes, sound names of at most {@value #MAX_NAME_LENGTH} characters, a length of 0 to {@value #MAX_LENGTH} seconds, a volume of
 * 0 to {@value #MAX_VOLUME} percent and delays of 0 to {@value #MAX_DELAY} ticks (the original had no limits).
 *
 * @param <T> the singing NPC class
 */
public abstract class NpcSongMenu<T extends NpcBase & ISinger> extends NpcMenuBase<T> {
    public static final int MAX_ENTRIES = 128;
    public static final int MAX_NAME_LENGTH = 200;
    public static final float MAX_LENGTH = 36000.0F;
    public static final int MAX_VOLUME = 1000;
    public static final int MAX_DELAY = 24000 * 100;

    /** The songs: the NPC's own list on the server, a list filled from the server's data on the client. */
    public final SongPlayData data;

    protected NpcSongMenu(MenuType<?> type, int containerId, Inventory inventory, @Nullable T npc) {
        super(type, containerId, inventory, npc);
        // The client's NPC has no song data of its own until the server sends it, so the client works on a copy.
        data = npc != null && !inventory.player.level().isClientSide ? npc.getSongs() : new SongPlayData();
        if (npc != null && inventory.player.level().isClientSide) {
            data.readFromNBT(npc.getSongs().writeToNBT(new CompoundTag()));
        }
    }

    @Override
    protected void sendInitialState() {
        CompoundTag tag = new CompoundTag();
        tag.put("tuneData", data.writeToNBT(new CompoundTag()));
        sendDataToClient(tag);
    }

    /** Client side: sends the edited songs to the server when the screen closes. */
    public void sendTuneDataToServer() {
        if (isClient()) {
            CompoundTag tag = new CompoundTag();
            tag.put("tuneData", data.writeToNBT(new CompoundTag()));
            sendDataToServer(tag);
        }
    }

    @Override
    protected void handlePacketData(CompoundTag tag) {
        if (!tag.contains("tuneData", Tag.TAG_COMPOUND)) {
            if (isClient()) {
                refreshGui();
            }
            return;
        }
        if (isClient()) {
            data.readFromNBT(tag.getCompound("tuneData"));
            refreshGui();
        } else if (isRequestAllowed()) {
            data.readFromNBT(sanitize(tag.getCompound("tuneData")));
        }
    }

    /** A copy of the song data with the limits applied. */
    static CompoundTag sanitize(CompoundTag sent) {
        CompoundTag clean = new CompoundTag();
        clean.putInt("minDelay", Mth.clamp(sent.getInt("minDelay"), 0, MAX_DELAY));
        clean.putInt("maxDelay", Mth.clamp(sent.getInt("maxDelay"), 0, MAX_DELAY));
        clean.putBoolean("random", sent.getBoolean("random"));
        clean.putBoolean("playerEntry", sent.getBoolean("playerEntry"));
        ListTag entries = new ListTag();
        ListTag sentEntries = sent.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < sentEntries.size() && i < MAX_ENTRIES; i++) {
            CompoundTag entry = sentEntries.getCompound(i);
            CompoundTag cleanEntry = new CompoundTag();
            if (entry.contains("name", Tag.TAG_STRING)) {
                String name = entry.getString("name");
                if (name.length() <= MAX_NAME_LENGTH) {
                    cleanEntry.putString("name", name);
                }
            }
            float length = entry.getFloat("length");
            cleanEntry.putFloat("length", Float.isNaN(length) ? 0.0F : Mth.clamp(length, 0.0F, MAX_LENGTH));
            cleanEntry.putInt("volume", Mth.clamp(entry.getInt("volume"), 0, MAX_VOLUME));
            entries.add(cleanEntry);
        }
        clean.put("entries", entries);
        return clean;
    }
}
