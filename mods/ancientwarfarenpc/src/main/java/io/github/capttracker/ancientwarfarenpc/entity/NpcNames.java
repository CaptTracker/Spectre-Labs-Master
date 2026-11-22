/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (playtest fixes): generated names.
 */
package io.github.capttracker.ancientwarfarenpc.entity;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The generated first names of the NPCs and the templates that turn a name into the displayed name. The name pools are data files
 * ({@code data/ancientwarfarenpc/npc_names/*.json}: {@code "names": [...]} and an optional {@code "factions": [...]}; a file without factions is
 * part of the general pool that player-owned NPCs and factions without a pool of their own draw from). The templates are lang keys
 * {@code npcname.template.<id>} with the first name as the {@code %s}, so a resource pack can change "Guard Captain Rory" to any other form.
 */
public final class NpcNames {
    private static final String[] FALLBACK = {"Rory", "Ren", "Mira", "Tomas", "Ada", "Finn", "Greta", "Odo", "Selim", "Bill", "Hrolf", "Sven"};
    /** The types (first part of the full type) of the player-owned NPCs; anything else is a faction NPC whose full type starts with the faction. */
    private static final Set<String> OWNED = Set.of("worker", "combat", "courier", "guard_captain", "innkeeper", "priest", "bard", "trader",
            "stable_hand", "siege_engineer");

    private static volatile List<String> general = List.of();
    private static volatile Map<String, List<String>> byFaction = Map.of();

    private NpcNames() {}

    // ---- the pools ----

    /** A random first name for an NPC of the faction ({@code ""} for none). */
    public static String pick(RandomSource random, String faction) {
        List<String> pool = faction == null || faction.isEmpty() ? null : byFaction.get(faction);
        if (pool == null || pool.isEmpty()) {
            pool = general;
        }
        if (pool.isEmpty()) {
            return FALLBACK[random.nextInt(FALLBACK.length)];
        }
        return pool.get(random.nextInt(pool.size()));
    }

    // ---- the displayed name ----

    /** The displayed name of an NPC of the full type ({@code worker.miner}, {@code norska.archer}) with the first name. */
    public static Component display(String fullType, String givenName) {
        String given = givenName == null ? "" : givenName;
        if (given.isEmpty()) {
            return Component.translatable("entity.ancientwarfarenpc." + fullType + ".name");
        }
        String key = templateKey(fullType);
        if (key == null) {
            return Component.translatable("npcname.template.generic", Component.translatable("entity.ancientwarfarenpc." + fullType + ".name"), given);
        }
        return Component.translatable(key, given);
    }

    /** The lang key of the template of a player-owned type, or null for the faction NPCs (type name plus first name). */
    private static String templateKey(String fullType) {
        int dot = fullType.indexOf('.');
        String type = dot < 0 ? fullType : fullType.substring(0, dot);
        String sub = dot < 0 ? "" : fullType.substring(dot + 1);
        if (!OWNED.contains(type)) {
            return null;
        }
        return "npcname.template." + switch (type) {
            case "worker" -> sub.isEmpty() ? "worker" : "worker." + sub;
            case "combat" -> switch (sub) {
                case "archer", "medic", "engineer", "commander" -> "combat." + sub;
                default -> "combat.soldier";
            };
            default -> type;
        };
    }

    // ---- loading ----

    @EventBusSubscriber(modid = AncientWarfareNPC.MOD_ID)
    public static final class Events {
        private Events() {}

        @SubscribeEvent
        public static void onAddReloadListeners(AddReloadListenerEvent event) {
            event.addListener(new Loader());
        }
    }

    private static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() {
            super(new Gson(), "npc_names");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            List<String> all = new ArrayList<>();
            Map<String, List<String>> factions = new HashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet()) {
                if (!file.getKey().getNamespace().equals(AncientWarfareNPC.MOD_ID) || !file.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject json = file.getValue().getAsJsonObject();
                List<String> names = strings(json.get("names"));
                List<String> owners = strings(json.get("factions"));
                if (owners.isEmpty()) {
                    all.addAll(names);
                } else {
                    for (String faction : owners) {
                        factions.computeIfAbsent(faction, k -> new ArrayList<>()).addAll(names);
                    }
                }
            }
            general = List.copyOf(all);
            byFaction = Map.copyOf(factions);
        }

        private static List<String> strings(JsonElement element) {
            List<String> out = new ArrayList<>();
            if (element != null && element.isJsonArray()) {
                JsonArray array = element.getAsJsonArray();
                for (JsonElement e : array) {
                    if (e.isJsonPrimitive() && !e.getAsString().isBlank()) {
                        out.add(e.getAsString().trim());
                    }
                }
            }
            return out;
        }
    }
}
