/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): the saved bookkeeping of the caravan scheduler.
 */
package io.github.capttracker.ancientwarfarenpc.caravan;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the scheduler remembers, saved with the overworld: when it last rolled, which settlements wait for a caravan (a caravan that has been decided on but
 * found no place to appear yet) and which have one on the road. The caravans themselves live on their leader NPCs.
 */
public class CaravanData extends SavedData {
    private static final String NAME = "ancientwarfarenpc_caravans";

    /** A caravan that is decided on and waits for a loaded place near a player. */
    public static final class Pending {
        public final UUID settlement;
        public final String faction;
        public final long expires;
        /** True for a trade ship (rework phase 11), false for a caravan by land. */
        public final boolean sea;

        Pending(UUID settlement, String faction, long expires, boolean sea) {
            this.settlement = settlement;
            this.faction = faction;
            this.expires = expires;
            this.sea = sea;
        }
    }

    /** A trade ship at sea or at the docks: the settlement it goes to and when it started. */
    public static final class SeaActive {
        public final UUID settlement;
        public final long start;

        SeaActive(UUID settlement, long start) {
            this.settlement = settlement;
            this.start = start;
        }
    }

    /** A caravan on the road: its leader and when it started. */
    public static final class Active {
        public final UUID leader;
        public final long start;

        Active(UUID leader, long start) {
            this.leader = leader;
            this.start = start;
        }
    }

    /** Game time of the last roll, -1 before the first. */
    public long lastRoll = -1;
    /** Game time of the last trade ship roll (rework phase 11), -1 before the first. */
    public long lastSeaRoll = -1;
    private final Map<UUID, SeaActive> seaActive = new LinkedHashMap<>();
    private final List<Pending> pending = new ArrayList<>();
    private final Map<UUID, Active> active = new LinkedHashMap<>();

    public static CaravanData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(CaravanData::new, CaravanData::load, null), NAME);
    }

    private static CaravanData load(CompoundTag tag, HolderLookup.Provider registries) {
        CaravanData data = new CaravanData();
        data.lastRoll = tag.contains("lastRoll") ? tag.getLong("lastRoll") : -1;
        data.lastSeaRoll = tag.contains("lastSeaRoll") ? tag.getLong("lastSeaRoll") : -1;
        ListTag pendingList = tag.getList("pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < pendingList.size(); i++) {
            CompoundTag entry = pendingList.getCompound(i);
            if (entry.hasUUID("settlement")) {
                data.pending.add(new Pending(entry.getUUID("settlement"), entry.getString("faction"), entry.getLong("expires"), entry.getBoolean("sea")));
            }
        }
        ListTag activeList = tag.getList("active", Tag.TAG_COMPOUND);
        for (int i = 0; i < activeList.size(); i++) {
            CompoundTag entry = activeList.getCompound(i);
            if (entry.hasUUID("settlement") && entry.hasUUID("leader")) {
                data.active.put(entry.getUUID("settlement"), new Active(entry.getUUID("leader"), entry.getLong("start")));
            }
        }
        ListTag seaList = tag.getList("seaActive", Tag.TAG_COMPOUND);
        for (int i = 0; i < seaList.size(); i++) {
            CompoundTag entry = seaList.getCompound(i);
            if (entry.hasUUID("settlement") && entry.hasUUID("leader")) {
                data.seaActive.put(entry.getUUID("leader"), new SeaActive(entry.getUUID("settlement"), entry.getLong("start")));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("lastRoll", lastRoll);
        tag.putLong("lastSeaRoll", lastSeaRoll);
        ListTag pendingList = new ListTag();
        for (Pending entry : pending) {
            CompoundTag item = new CompoundTag();
            item.putUUID("settlement", entry.settlement);
            item.putString("faction", entry.faction);
            item.putLong("expires", entry.expires);
            item.putBoolean("sea", entry.sea);
            pendingList.add(item);
        }
        tag.put("pending", pendingList);
        ListTag activeList = new ListTag();
        for (Map.Entry<UUID, Active> entry : active.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putUUID("settlement", entry.getKey());
            item.putUUID("leader", entry.getValue().leader);
            item.putLong("start", entry.getValue().start);
            activeList.add(item);
        }
        tag.put("active", activeList);
        ListTag seaList = new ListTag();
        for (Map.Entry<UUID, SeaActive> entry : seaActive.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putUUID("leader", entry.getKey());
            item.putUUID("settlement", entry.getValue().settlement);
            item.putLong("start", entry.getValue().start);
            seaList.add(item);
        }
        tag.put("seaActive", seaList);
        return tag;
    }

    public void markRolled(long gameTime) {
        lastRoll = gameTime;
        setDirty();
    }

    public List<Pending> pending() {
        return new ArrayList<>(pending);
    }

    /** True if a caravan by land is decided on for the settlement and waits for a place. */
    public boolean isWaiting(UUID settlement) {
        for (Pending entry : pending) {
            if (!entry.sea && entry.settlement.equals(settlement)) {
                return true;
            }
        }
        return false;
    }

    /** How many pending requests there are for caravans by land ({@code sea} false) or trade ships ({@code sea} true). */
    public int pendingCount(boolean sea) {
        int count = 0;
        for (Pending entry : pending) {
            if (entry.sea == sea) {
                count++;
            }
        }
        return count;
    }

    /** How many trade ships are decided on for the settlement and wait for a place at sea. */
    public int seaPendingFor(UUID settlement) {
        int count = 0;
        for (Pending entry : pending) {
            if (entry.sea && entry.settlement.equals(settlement)) {
                count++;
            }
        }
        return count;
    }

    public void addPending(UUID settlement, String faction, long expires) {
        pending.add(new Pending(settlement, faction, expires, false));
        setDirty();
    }

    public void addSeaPending(UUID settlement, String faction, long expires) {
        pending.add(new Pending(settlement, faction, expires, true));
        setDirty();
    }

    // ---- trade ships (rework phase 11) ----

    public void markSeaRolled(long gameTime) {
        lastSeaRoll = gameTime;
        setDirty();
    }

    public Map<UUID, SeaActive> seaActive() {
        return new LinkedHashMap<>(seaActive);
    }

    public int seaActiveCount() {
        return seaActive.size();
    }

    /** How many trade ships of the settlement are on their way or at its docks. */
    public int seaActiveFor(UUID settlement) {
        int count = 0;
        for (SeaActive entry : seaActive.values()) {
            if (entry.settlement.equals(settlement)) {
                count++;
            }
        }
        return count;
    }

    public void setSeaActive(UUID settlement, UUID leader, long start) {
        seaActive.put(leader, new SeaActive(settlement, start));
        setDirty();
    }

    /** The trade ship of this leader is over (it left, was killed, sank or vanished). */
    public void finishSea(UUID leader) {
        if (seaActive.remove(leader) != null) {
            setDirty();
        }
    }

    public void removePending(Pending entry) {
        if (pending.remove(entry)) {
            setDirty();
        }
    }

    public boolean hasActive(UUID settlement) {
        return active.containsKey(settlement);
    }

    public int activeCount() {
        return active.size();
    }

    public Map<UUID, Active> active() {
        return new LinkedHashMap<>(active);
    }

    public void setActive(UUID settlement, UUID leader, long start) {
        active.put(settlement, new Active(leader, start));
        setDirty();
    }

    /** The caravan of the settlement is over (it left, was killed or vanished). */
    public void finish(UUID settlement, UUID leader) {
        Active entry = active.get(settlement);
        if (entry != null && entry.leader.equals(leader) && active.remove(settlement) != null) {
            setDirty();
        }
    }

    /** Drops an entry without checking its leader (stale records). */
    public void forget(UUID settlement) {
        if (active.remove(settlement) != null) {
            setDirty();
        }
    }
}
