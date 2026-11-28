/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): building designs.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * The player blueprints of a world. Each is a JSON file (the design format) in
 * {@code <world>/ancientwarfarenpc/blueprints/<owner uuid>/<name>.json}; all are loaded when the server starts. The id of a blueprint is
 * {@code ancientwarfarenpc:blueprint/<owner uuid without dashes>/<file name>}. Server thread only.
 */
public final class BlueprintStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, Map<ResourceLocation, BuildingDesign>> BY_OWNER = new ConcurrentHashMap<>();

    private BlueprintStore() {}

    private static Path root(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(AncientWarfareNPC.MOD_ID).resolve("blueprints");
    }

    public static ResourceLocation idOf(UUID owner, String slug) {
        return ResourceLocation.fromNamespaceAndPath(AncientWarfareNPC.MOD_ID, "blueprint/" + owner.toString().replace("-", "") + "/" + slug);
    }

    /** Loads every blueprint file of the world, dropping what was loaded before. Bad files are skipped with a warning. */
    public static void load(MinecraftServer server) {
        BY_OWNER.clear();
        Path root = root(server);
        if (!Files.isDirectory(root)) {
            return;
        }
        int count = 0;
        try (Stream<Path> owners = Files.list(root)) {
            for (Path ownerDir : (Iterable<Path>) owners::iterator) {
                UUID owner;
                try {
                    owner = UUID.fromString(ownerDir.getFileName().toString());
                } catch (IllegalArgumentException e) {
                    continue;
                }
                if (!Files.isDirectory(ownerDir)) {
                    continue;
                }
                try (Stream<Path> files = Files.list(ownerDir)) {
                    for (Path file : (Iterable<Path>) files::iterator) {
                        String fileName = file.getFileName().toString();
                        if (!fileName.endsWith(".json")) {
                            continue;
                        }
                        String slug = fileName.substring(0, fileName.length() - 5);
                        ResourceLocation id = idOf(owner, slug);
                        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                            BuildingDesign design = BuildingDesign.parse(id, json, owner);
                            for (String warning : design.getWarnings()) {
                                LOGGER.warn("Blueprint {}: {}", file, warning);
                            }
                            BY_OWNER.computeIfAbsent(owner, k -> new ConcurrentHashMap<>()).put(id, design);
                            count++;
                        } catch (DesignException | RuntimeException | IOException e) {
                            LOGGER.warn("Skipping blueprint {}: {}", file, e.getMessage());
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Could not read the blueprints folder {}: {}", root, e.getMessage());
        }
        LOGGER.info("Loaded {} player blueprints", count);
    }

    public static void unload() {
        BY_OWNER.clear();
    }

    public static Collection<BuildingDesign> of(UUID owner) {
        Map<ResourceLocation, BuildingDesign> map = BY_OWNER.get(owner);
        return map == null ? List.of() : new ArrayList<>(map.values());
    }

    @Nullable
    public static BuildingDesign get(ResourceLocation id) {
        for (Map<ResourceLocation, BuildingDesign> map : BY_OWNER.values()) {
            BuildingDesign design = map.get(id);
            if (design != null) {
                return design;
            }
        }
        return null;
    }

    public static int count(UUID owner) {
        Map<ResourceLocation, BuildingDesign> map = BY_OWNER.get(owner);
        return map == null ? 0 : map.size();
    }

    /**
     * Parses and saves a blueprint for a player. {@code json} is the design in the design format (its owner is set here). Throws when the
     * design is invalid, the player has too many blueprints, or the file cannot be written.
     */
    public static BuildingDesign save(MinecraftServer server, UUID owner, String name, JsonObject json) throws DesignException {
        if (count(owner) >= SettlementConfig.blueprintMaxPerPlayer) {
            throw new DesignException("too many blueprints (at most " + SettlementConfig.blueprintMaxPerPlayer + ")");
        }
        String base = slug(name);
        String slug = base;
        int n = 2;
        while (BY_OWNER.getOrDefault(owner, Map.of()).containsKey(idOf(owner, slug))) {
            slug = base + "_" + n++;
        }
        ResourceLocation id = idOf(owner, slug);
        BuildingDesign design = BuildingDesign.parse(id, json, owner);
        Path file = root(server).resolve(owner.toString()).resolve(slug + ".json");
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(design.toJson(), writer);
            }
        } catch (IOException e) {
            throw new DesignException("could not write the blueprint file: " + e.getMessage());
        }
        BY_OWNER.computeIfAbsent(owner, k -> new ConcurrentHashMap<>()).put(id, design);
        return design;
    }

    /**
     * Gives a blueprint of a player a new display name. The id and the file name stay as they are, so builds and plans that use the blueprint keep working.
     * Throws when the blueprint does not exist or the file cannot be written.
     */
    public static BuildingDesign rename(MinecraftServer server, UUID owner, ResourceLocation id, String name) throws DesignException {
        Map<ResourceLocation, BuildingDesign> map = BY_OWNER.get(owner);
        BuildingDesign old = map == null ? null : map.get(id);
        if (old == null) {
            throw new DesignException("that blueprint does not exist");
        }
        JsonObject json = old.toJson();
        json.addProperty("name", name);
        BuildingDesign design = BuildingDesign.parse(id, json, owner);
        String path = id.getPath();
        String slug = path.substring(path.lastIndexOf('/') + 1);
        Path file = root(server).resolve(owner.toString()).resolve(slug + ".json");
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(design.toJson(), writer);
            }
        } catch (IOException e) {
            throw new DesignException("could not write the blueprint file: " + e.getMessage());
        }
        map.put(id, design);
        return design;
    }

    /** Deletes a blueprint of a player. Returns whether it existed. */
    public static boolean delete(MinecraftServer server, UUID owner, ResourceLocation id) {
        Map<ResourceLocation, BuildingDesign> map = BY_OWNER.get(owner);
        if (map == null || map.remove(id) == null) {
            return false;
        }
        String path = id.getPath();
        String slug = path.substring(path.lastIndexOf('/') + 1);
        try {
            Files.deleteIfExists(root(server).resolve(owner.toString()).resolve(slug + ".json"));
        } catch (IOException e) {
            LOGGER.warn("Could not delete the blueprint file of {}: {}", id, e.getMessage());
        }
        return true;
    }

    private static String slug(String name) {
        String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (s.length() > 40) {
            s = s.substring(0, 40);
        }
        return s.isEmpty() ? "blueprint" : s;
    }
}
