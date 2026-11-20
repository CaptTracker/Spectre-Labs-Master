package io.github.capttracker.spectrefactions.data;

import io.github.capttracker.spectrefactions.api.ActorId;
import io.github.capttracker.spectrefactions.api.GuardMode;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * NPC factions registered by code. Registration can happen before the world (and so the saved data) exists, for
 * example in {@code ServerAboutToStartEvent}, so registrations are kept here and applied to the saved data when
 * it is loaded.
 */
public final class NpcRegistry {
    record Entry(ResourceLocation key, String name, ChatFormatting color, GuardMode guardMode, int defaultReputation) {}

    private static final Map<ResourceLocation, Entry> NPCS = new LinkedHashMap<>();
    private static final Map<FactionsSavedData.PairKey, Integer> PAIR_DEFAULTS = new LinkedHashMap<>();
    private static int version = 0;

    private NpcRegistry() {}

    public static UUID idFor(ResourceLocation key) {
        return UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static ActorId actorFor(ResourceLocation key) {
        return ActorId.faction(idFor(key));
    }

    public static synchronized void register(ResourceLocation key, String name, ChatFormatting color, GuardMode guard, int defaultReputation) {
        NPCS.put(key, new Entry(key, name, color, guard, defaultReputation));
        version++;
    }

    public static synchronized void setPairDefault(ResourceLocation a, ResourceLocation b, int reputation) {
        PAIR_DEFAULTS.put(FactionsSavedData.PairKey.of(actorFor(a), actorFor(b)), reputation);
        version++;
    }

    static synchronized Integer pairDefault(ActorId a, ActorId b) {
        return PAIR_DEFAULTS.get(FactionsSavedData.PairKey.of(a, b));
    }

    static synchronized java.util.List<Entry> entries() {
        return new java.util.ArrayList<>(NPCS.values());
    }

    static synchronized int version() {
        return version;
    }
}
