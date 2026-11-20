/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/registry/IRegistryDataParser.
 */
package io.github.capttracker.ancientwarfare.registry;

import com.google.gson.JsonObject;

/**
 * Reads one kind of registry data file (farm block tables, faction definitions, manual content, ...).
 * Registered by name with {@link RegistryLoader#registerParser} (data packs, server side) or
 * {@link RegistryLoader#registerClientParser} (resource packs, client side).
 */
public interface IRegistryDataParser {
    /** The parser name: the {@code type} field of a file, or the file's short name if it has none. */
    String getName();

    /**
     * Called before the files of a (re)load are parsed, so a parser can clear what an earlier load put into its tables.
     * New in the port: data now reloads with {@code /reload}, 1.12.2 loaded once at start up.
     */
    default void beginLoad() {}

    void parse(JsonObject json);
}
