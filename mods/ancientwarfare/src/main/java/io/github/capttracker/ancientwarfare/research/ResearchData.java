/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/research/ResearchData.
 */
package io.github.capttracker.ancientwarfare.research;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every player's research, saved with the overworld. Keyed by player UUID; the original keyed by
 * player name, which lost research on a name change.
 */
public final class ResearchData extends SavedData {
    private static final String FILE_NAME = "ancientwarfare_research";
    private static final Factory<ResearchData> FACTORY = new Factory<>(ResearchData::new, ResearchData::load);

    private final Map<UUID, ResearchEntry> entries = new HashMap<>();

    public static ResearchData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_NAME);
    }

    /** The player's entry, created on first use. Call {@link #setDirty()} after changing it. */
    public ResearchEntry entry(UUID player) {
        return entries.computeIfAbsent(player, id -> {
            setDirty();
            return new ResearchEntry();
        });
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        entries.forEach((player, entry) -> {
            CompoundTag entryTag = entry.save();
            entryTag.putUUID("player", player);
            list.add(entryTag);
        });
        tag.put("entryList", list);
        return tag;
    }

    private static ResearchData load(CompoundTag tag, HolderLookup.Provider registries) {
        ResearchData data = new ResearchData();
        ListTag list = tag.getList("entryList", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            if (entryTag.hasUUID("player")) {
                data.entries.put(entryTag.getUUID("player"), ResearchEntry.load(entryTag));
            }
        }
        return data;
    }
}
