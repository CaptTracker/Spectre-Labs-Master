/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gamedata/WorldData.
 */
package io.github.capttracker.ancientwarfare.gamedata;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Small world-wide flags, and which players were already given the manual. Saved with the overworld.
 *
 * <p>The 1.12.2 class was per dimension; a player who crafted something in the Nether before the
 * overworld would get a second manual. One copy for the world is the intended behaviour.
 */
public class WorldData extends SavedData {
    private static final String FILE_NAME = AWGameData.fileName(WorldData.class);
    private static final SavedData.Factory<WorldData> FACTORY = new SavedData.Factory<>(WorldData::new, WorldData::load);

    private CompoundTag dataTag = new CompoundTag();
    private final Set<UUID> playersGivenManual = new HashSet<>();

    public static WorldData get(MinecraftServer server) {
        return AWGameData.INSTANCE.getData(server, FACTORY, FILE_NAME);
    }

    public final boolean get(String key) {
        return dataTag.getBoolean(key);
    }

    public final void set(String name, boolean val) {
        dataTag.putBoolean(name, val);
        setDirty();
    }

    public final void addPlayerThatWasGivenManual(Player player) {
        playersGivenManual.add(player.getUUID());
        setDirty();
    }

    public boolean wasPlayerGivenManual(Player player) {
        return playersGivenManual.contains(player.getUUID());
    }

    private static WorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldData data = new WorldData();
        CompoundTag inner = tag.getCompound("AWWorldData");
        data.dataTag = inner;
        ListTag players = inner.getList("playersGivenManual", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag entry = players.getCompound(i);
            if (entry.hasUUID("uuid")) {
                data.playersGivenManual.add(entry.getUUID("uuid"));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        for (UUID id : playersGivenManual) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", id);
            players.add(entry);
        }
        dataTag.put("playersGivenManual", players);
        tag.put("AWWorldData", dataTag);
        return tag;
    }
}
