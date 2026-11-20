/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): how much reputation each buyer has gained from trading with each seller today.
 */
package io.github.capttracker.ancientwarfarenpc.trade;

import io.github.capttracker.spectrefactions.api.ActorId;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * The reputation gained from trading, per (buyer, seller) pair and in-game day (game time / 24000), kept so that the daily cap survives a restart. Saved in
 * the overworld's data storage. A pair's entry is replaced when its day is over; stale entries are dropped when the file is written. Server thread only.
 */
public class TradeReputationData extends SavedData {
    private static final String NAME = "ancientwarfarenpc_trade_reputation";

    private record Gain(long day, int amount) {}

    private final Map<String, Gain> gains = new HashMap<>();

    public static TradeReputationData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(TradeReputationData::new, TradeReputationData::load, null), NAME);
    }

    private static TradeReputationData load(CompoundTag tag, HolderLookup.Provider registries) {
        TradeReputationData data = new TradeReputationData();
        CompoundTag map = tag.getCompound("gains");
        for (String key : map.getAllKeys()) {
            CompoundTag entry = map.getCompound(key);
            data.gains.put(key, new Gain(entry.getLong("day"), entry.getInt("amount")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag map = new CompoundTag();
        for (Map.Entry<String, Gain> entry : gains.entrySet()) {
            CompoundTag value = new CompoundTag();
            value.putLong("day", entry.getValue().day());
            value.putInt("amount", entry.getValue().amount());
            map.put(entry.getKey(), value);
        }
        tag.put("gains", map);
        return tag;
    }

    private static String key(ActorId buyer, ActorId seller) {
        return buyer.kind() + ":" + buyer.id() + "|" + seller.kind() + ":" + seller.id();
    }

    /**
     * Books up to {@code wanted} reputation for the pair on this day, within the daily cap.
     *
     * @return the amount that may be given now (0 when the cap is reached)
     */
    public int take(ActorId buyer, ActorId seller, int wanted, int dailyCap, long gameTime) {
        long day = gameTime / 24000L;
        String key = key(buyer, seller);
        Gain old = gains.get(key);
        int already = old != null && old.day() == day ? old.amount() : 0;
        int grant = Math.max(0, Math.min(wanted, dailyCap - already));
        if (grant > 0) {
            gains.put(key, new Gain(day, already + grant));
            gains.values().removeIf(gain -> gain.day() < day);
            setDirty();
        }
        return grant;
    }
}
