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
 * part of the general pool that player-owned NPCs and factions without a pool of their own draw from; an optional {@code "surnames": [...]} is the surname pool
 * of the same file, rework phase 10). The templates are lang keys
 * {@code npcname.template.<id>} with the first name as the {@code %s}, so a resource pack can change "Guard Captain Rory" to any other form.
 */
public final class NpcNames {
    private static final String[] FALLBACK = {"Rory", "Ren", "Mira", "Tomas", "Ada", "Finn", "Greta", "Odo", "Selim", "Bill", "Hrolf", "Sven"};
    /** The types (first part of the full type) of the player-owned NPCs; anything else is a faction NPC whose full type starts with the faction. */
    private static final Set<String> OWNED = Set.of("worker", "combat", "courier", "guard_captain", "innkeeper", "priest", "bard", "trader",
            "stable_hand", "siege_engineer");

    private static final String[] FALLBACK_SURNAMES = {"Carter", "Miller", "Baker", "Fletcher", "Cooper", "Thatcher", "Mason", "Fisher"};

    private static volatile List<String> general = List.of();
    private static volatile Map<String, List<String>> byFaction = Map.of();
    private static volatile List<String> generalSurnames = List.of();
    private static volatile Map<String, List<String>> surnamesByFaction = Map.of();
    /** The lower case first name of a culture pool to the surnames of the pools it appears in (the culture of an owned NPC with that first name). */
    private static volatile Map<String, List<String>> surnamesByGivenName = Map.of();
    /** The lower case first name of a culture pool to the first names of the pools it appears in (the culture a child of a parent with that first name draws from). */
    private static volatile Map<String, List<String>> namesByGivenName = Map.of();

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

    /**
     * A random surname (rework phase 10) for an NPC of the faction ({@code ""} for none, as for the player-owned NPCs) with the first name. A faction with a
     * pool of its own uses it; otherwise, when the first name is in the name pool of a culture (norse, desert, eastern ...), the surnames of that culture;
     * else the general surnames.
     */
    public static String pickSurname(RandomSource random, String faction, String givenName) {
        List<String> pool = faction == null || faction.isEmpty() ? null : surnamesByFaction.get(faction);
        if ((pool == null || pool.isEmpty()) && givenName != null && !givenName.isEmpty()) {
            pool = surnamesByGivenName.get(givenName.toLowerCase(java.util.Locale.ROOT));
        }
        if (pool == null || pool.isEmpty()) {
            pool = generalSurnames;
        }
        if (pool.isEmpty()) {
            return FALLBACK_SURNAMES[random.nextInt(FALLBACK_SURNAMES.length)];
        }
        return pool.get(random.nextInt(pool.size()));
    }

    /**
     * A first name for a child (rework phase 10) from the culture pool of its parents: one of the parents (chosen by chance) whose first name is in the
     * pool of a culture (norse, desert, eastern ...) gives that pool, else the general pool. The name differs from the names in {@code avoid} (the
     * parents and the children they have) if the pool has another one.
     */
    public static String pickGivenLike(RandomSource random, List<String> parentNames, java.util.Collection<String> avoid) {
        List<String> parents = new ArrayList<>(parentNames);
        java.util.Collections.shuffle(parents, new java.util.Random(random.nextLong()));
        List<String> pool = null;
        for (String parent : parents) {
            List<String> culture = parent == null ? null : namesByGivenName.get(parent.toLowerCase(java.util.Locale.ROOT));
            if (culture != null && !culture.isEmpty()) {
                pool = culture;
                break;
            }
        }
        if (pool == null || pool.isEmpty()) {
            pool = general;
        }
        if (pool.isEmpty()) {
            pool = List.of(FALLBACK);
        }
        List<String> free = new ArrayList<>();
        for (String name : pool) {
            boolean taken = false;
            for (String other : avoid) {
                if (name.equalsIgnoreCase(other)) {
                    taken = true;
                    break;
                }
            }
            if (!taken) {
                free.add(name);
            }
        }
        List<String> from = free.isEmpty() ? pool : free;
        return from.get(random.nextInt(from.size()));
    }

    /** The nameplate of a child ("Child Tom", {@code npcname.child}; just "Child" without a first name). */
    public static Component child(String givenName) {
        return givenName == null || givenName.isEmpty() ? Component.translatable("npcname.child.unnamed") : Component.translatable("npcname.child", givenName);
    }

    /** The first name and the surname in the order of the language ({@code npcname.full}: "Bill Carter"); the first name alone when there is no surname. */
    public static Component full(String givenName, String surname) {
        String given = givenName == null ? "" : givenName;
        if (surname == null || surname.isEmpty()) {
            return Component.literal(given);
        }
        if (given.isEmpty()) {
            return Component.literal(surname);
        }
        return Component.translatable("npcname.full", given, surname);
    }

    // ---- the displayed name ----

    /** The displayed name of an NPC of the full type ({@code worker.miner}, {@code norska.archer}) with the first name. */
    public static Component display(String fullType, String givenName) {
        return display(fullType, givenName, -1);
    }

    /**
     * As {@link #display(String, String)} with the rank (0 to 4) of the main skill in front: the template {@code npcname.ranked.<id>} of the type takes the
     * rank name and the first name ("Apprentice Miner Bill"). A negative rank gives the plain name.
     */
    public static Component display(String fullType, String givenName, int rank) {
        String given = givenName == null ? "" : givenName;
        if (given.isEmpty()) {
            Component typeName = Component.translatable("entity.ancientwarfarenpc." + fullType + ".name");
            return rank >= 0 && templateKey(fullType) != null
                    ? Component.translatable("npcname.ranked.unnamed", Component.translatable("npc.skill.rank." + io.github.capttracker.ancientwarfarenpc.skill.Rank.byIndex(rank).id()), typeName)
                    : typeName;
        }
        String key = templateKey(fullType);
        if (rank >= 0 && key != null) {
            return Component.translatable(key.replace("npcname.template.", "npcname.ranked."), Component.translatable("npc.skill.rank." + io.github.capttracker.ancientwarfarenpc.skill.Rank.byIndex(rank).id()), given);
        }
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
            List<String> allSurnames = new ArrayList<>();
            Map<String, List<String>> surnameFactions = new HashMap<>();
            Map<String, List<String>> surnameByName = new HashMap<>();
            Map<String, List<String>> namesByName = new HashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet()) {
                if (!file.getKey().getNamespace().equals(AncientWarfareNPC.MOD_ID) || !file.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject json = file.getValue().getAsJsonObject();
                List<String> names = strings(json.get("names"));
                List<String> owners = strings(json.get("factions"));
                List<String> surnames = strings(json.get("surnames"));
                if (owners.isEmpty()) {
                    all.addAll(names);
                    allSurnames.addAll(surnames);
                } else {
                    for (String faction : owners) {
                        factions.computeIfAbsent(faction, k -> new ArrayList<>()).addAll(names);
                        if (!surnames.isEmpty()) {
                            surnameFactions.computeIfAbsent(faction, k -> new ArrayList<>()).addAll(surnames);
                        }
                    }
                    for (String name : names) {
                        List<String> culture = namesByName.computeIfAbsent(name.toLowerCase(java.util.Locale.ROOT), k -> new ArrayList<>());
                        for (String other : names) {
                            if (!culture.contains(other)) {
                                culture.add(other);
                            }
                        }
                    }
                    if (!surnames.isEmpty()) {
                        for (String name : names) {
                            List<String> list = surnameByName.computeIfAbsent(name.toLowerCase(java.util.Locale.ROOT), k -> new ArrayList<>());
                            for (String surname : surnames) {
                                if (!list.contains(surname)) {
                                    list.add(surname);
                                }
                            }
                        }
                    }
                }
            }
            general = List.copyOf(all);
            byFaction = Map.copyOf(factions);
            generalSurnames = List.copyOf(allSurnames);
            surnamesByFaction = Map.copyOf(surnameFactions);
            surnamesByGivenName = Map.copyOf(surnameByName);
            namesByGivenName = Map.copyOf(namesByName);
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
