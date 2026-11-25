/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/registry/RegistryLoader.
 */
package io.github.capttracker.ancientwarfare.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfare.util.parsing.JsonHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Loads the JSON registry data files and hands each one to the parser named by its {@code type} field (or by
 * its file name). It replaces the 1.12.2 loader that walked the mod jar at start up; now it is a reload
 * listener, so data comes from the resource manager and can be overridden or added to by packs.
 *
 * <ul>
 *   <li>{@link #SERVER}: files in {@code data/<namespace>/registry/**.json} (data packs), reloaded with
 *       {@code /reload}. Register it with {@code AddReloadListenerEvent}.</li>
 *   <li>{@link #CLIENT}: files in {@code assets/<namespace>/registry/**.json} (resource packs), reloaded with
 *       F3+T. Register it with {@code RegisterClientReloadListenersEvent}. Used for the manual text.</li>
 * </ul>
 *
 * <p>The registry name of a file is {@code <namespace>:<path below registry/, without .json>}. Per-file
 * switches kept from the original: {@code "mod": "<modid>"} (parsed only if that mod is loaded),
 * {@code "disabled": true} (skipped, but it still counts as a loaded file of its parser) and
 * {@code "load_after": ["<parser name>", ...]} (parsed only after every named parser has loaded a file).
 * The config folder override of the original is gone: to replace a file, a pack ships a file with the same path.
 */
public final class RegistryLoader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonObject>> {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Folder below {@code data/<namespace>/} or {@code assets/<namespace>/}. */
    public static final String FOLDER = "registry";
    private static final String EXTENSION = ".json";

    public static final RegistryLoader SERVER = new RegistryLoader("server");
    public static final RegistryLoader CLIENT = new RegistryLoader("client");

    private final String side;
    private final Map<String, IRegistryDataParser> parsers = new ConcurrentHashMap<>();
    /** Registry name of every file seen in the last load, with the name of the parser it belongs to. */
    private final Map<ResourceLocation, String> loadedRegistries = new HashMap<>();

    private RegistryLoader(String side) {
        this.side = side;
    }

    /** Registers a parser for data pack files (server side). Call during mod construction or common setup. */
    public static void registerParser(IRegistryDataParser parser) {
        SERVER.addParser(parser);
    }

    /** Registers a parser for resource pack files (client side only). */
    public static void registerClientParser(IRegistryDataParser parser) {
        CLIENT.addParser(parser);
    }

    public void addParser(IRegistryDataParser parser) {
        parsers.put(parser.getName(), parser);
    }

    // ---- reload listener ----

    @Override
    protected Map<ResourceLocation, JsonObject> prepare(ResourceManager manager, ProfilerFiller profiler) {
        return readFiles(manager);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonObject> files, ResourceManager manager, ProfilerFiller profiler) {
        load(files, parser -> true);
    }

    /**
     * Forgets what one parser loaded and parses its files again from the resource manager (was
     * {@code reload(type)}, used by the manual reload command). Main thread.
     */
    public void reload(ResourceManager manager, String parserName) {
        load(readFiles(manager), parser -> parser.getName().equals(parserName));
    }

    private Map<ResourceLocation, JsonObject> readFiles(ResourceManager manager) {
        // Sorted so that the files of one reload are parsed in a stable order.
        Map<ResourceLocation, JsonObject> files = new TreeMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources(FOLDER, id -> id.getPath().endsWith(EXTENSION)).entrySet()) {
            ResourceLocation registryName = toRegistryName(entry.getKey());
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                // JsonParser reads leniently, which the manual files need (one has raw control characters in a string).
                JsonElement json = JsonParser.parseReader(reader);
                if (json.isJsonObject()) {
                    files.put(registryName, json.getAsJsonObject());
                } else {
                    LOGGER.error("Registry {} ({} side) is not a JSON object, skipping", registryName, side);
                }
            } catch (JsonParseException e) {
                LOGGER.error("Parsing error loading registry {}", registryName, e);
            } catch (IOException e) {
                LOGGER.error("Couldn't read registry {} from {}", registryName, entry.getKey(), e);
            }
        }
        return files;
    }

    /** {@code ns:registry/a/b.json} becomes {@code ns:a/b}. */
    private static ResourceLocation toRegistryName(ResourceLocation file) {
        String path = file.getPath();
        path = path.substring(FOLDER.length() + 1, path.length() - EXTENSION.length());
        return file.withPath(path);
    }

    // ---- parsing ----

    private void load(Map<ResourceLocation, JsonObject> files, Predicate<IRegistryDataParser> include) {
        loadedRegistries.entrySet().removeIf(entry -> {
            IRegistryDataParser parser = parsers.get(entry.getValue());
            return parser != null && include.test(parser);
        });
        for (IRegistryDataParser parser : parsers.values()) {
            if (include.test(parser)) {
                parser.beginLoad();
            }
        }

        List<DependentFile> loadLater = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonObject> file : files.entrySet()) {
            loadFile(file.getKey(), file.getValue(), include, true, loadLater);
        }
        loadDependents(include, loadLater);
    }

    private void loadDependents(Predicate<IRegistryDataParser> include, List<DependentFile> loadLater) {
        int lastCountLoadLater = loadLater.size();
        while (!loadLater.isEmpty()) {
            Iterator<DependentFile> iterator = new ArrayList<>(loadLater).iterator();
            while (iterator.hasNext()) {
                DependentFile dependentFile = iterator.next();
                if (areDependenciesLoaded(dependentFile.dependencies())) {
                    loadLater.remove(dependentFile);
                    loadFile(dependentFile.name(), dependentFile.json(), include, false, loadLater);
                }
            }
            if (lastCountLoadLater <= loadLater.size()) {
                for (DependentFile dependentFile : loadLater) {
                    LOGGER.error("Non existent or circular load after dependencies in {} - {}", dependentFile.name(), String.join(",", dependentFile.dependencies()));
                }
                break;
            }
            lastCountLoadLater = loadLater.size();
        }
    }

    private void loadFile(ResourceLocation registryName, JsonObject json, Predicate<IRegistryDataParser> include, boolean checkDependencies,
                          List<DependentFile> loadLater) {
        String path = registryName.getPath();
        String shortName = path.substring(path.lastIndexOf('/') + 1);

        try {
            Optional<IRegistryDataParser> parser = getParser(shortName, json);
            if (parser.isEmpty()) {
                LOGGER.error("No {} parser defined for file name {} ({})", side, shortName, registryName);
                return;
            }
            if (!include.test(parser.get())) {
                return;
            }

            if (checkDependencies && json.has("load_after")) {
                Set<String> dependencies = JsonHelper.setFromJson(json.get("load_after"), e -> GsonHelper.convertToString(e, "load_after"));
                if (!areDependenciesLoaded(dependencies)) {
                    loadLater.add(new DependentFile(registryName, json, dependencies));
                    return;
                }
            }

            loadedRegistries.put(registryName, parser.get().getName());

            if (isDisabled(json) || !isModLoaded(json)) {
                return;
            }

            parser.get().parse(json);
        } catch (JsonParseException e) {
            LOGGER.error("Parsing error loading registry {}", registryName, e);
        } catch (MissingResourceException e) {
            LOGGER.error("Registry {}: {}", registryName, e.getMessage());
        } catch (RuntimeException e) {
            // A malformed entry must not take the other files down with it.
            LOGGER.error("Unexpected error loading registry {}", registryName, e);
        }
    }

    private boolean areDependenciesLoaded(Set<String> dependencies) {
        for (String dependency : dependencies) {
            if (!loadedRegistries.containsValue(dependency)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isModLoaded(JsonObject json) {
        return !GsonHelper.isValidNode(json, "mod") || ModList.get().isLoaded(GsonHelper.getAsString(json, "mod"));
    }

    private static boolean isDisabled(JsonObject json) {
        return json.has("disabled") && GsonHelper.getAsBoolean(json, "disabled");
    }

    private Optional<IRegistryDataParser> getParser(String fileName, JsonObject json) {
        String parserName = fileName;
        if (json.has("type")) {
            parserName = GsonHelper.getAsString(json, "type");
        }
        return Optional.ofNullable(parsers.get(parserName));
    }

    private record DependentFile(ResourceLocation name, JsonObject json, Set<String> dependencies) {}
}
