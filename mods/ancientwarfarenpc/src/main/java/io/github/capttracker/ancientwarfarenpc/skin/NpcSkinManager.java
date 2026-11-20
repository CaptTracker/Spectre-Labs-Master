/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/skin/NpcSkinManager (the skin pack scan and the dynamic texture upload became resource pack data).
 */
package io.github.capttracker.ancientwarfarenpc.skin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfare.registry.IRegistryDataParser;
import io.github.capttracker.ancientwarfare.registry.RegistryLoader;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * The skins an NPC type can pick from, by full NPC type ({@code orc.soldier}, {@code worker.farmer}, ...) or {@code custom}.
 *
 * <p>1.12.2 scanned the mod jar's {@code skin_pack} folder and a config folder at start up and uploaded every PNG as a dynamic texture. The PNGs
 * are ordinary resource pack textures now ({@code assets/ancientwarfarenpc/textures/skin_pack/<name>.png}, 3,606 of them) and the mapping from NPC
 * type to skins ({@code skin_pack.meta}) is an index file read by {@link SkinPackParser} as a client registry file
 * ({@code assets/<namespace>/registry/<any name>.json} with {@code "type": "skin_pack"}). A resource pack adds skins by shipping its own PNGs and
 * an index file with a different file name:
 * <pre>{@code
 * { "type": "skin_pack", "folder": "mypack:skin_pack",
 *   "types": { "orc.soldier": ["orc_a", "orc_b"] },     // textures/skin_pack/orc_a.png ...
 *   "custom": ["free_skin"] }                            // skins that no type uses by default, picked in the skin screen
 * }</pre>
 * The bundled index has {@code "default_pack": true} and is skipped when the client option {@code load_default_skin_pack} is false.
 *
 * <p>Client only in practice (the index lives in the assets), but nothing here touches client classes.
 */
public final class NpcSkinManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CUSTOM_TYPE = "custom";

    private static volatile Map<String, List<ResourceLocation>> npcSkins = new HashMap<>();
    private static final Random RNG = new Random();

    private NpcSkinManager() {}

    /** Registers the index parser with Core's client registry loader. Call once during mod setup (safe on a dedicated server, the loader is never run there). */
    public static void registerParser() {
        RegistryLoader.registerClientParser(new SkinPackParser());
    }

    /** A random but stable skin of the type: the same seed (the NPC's UUID bits) always gives the same skin. */
    public static Optional<ResourceLocation> getNpcTexture(String type, long idlsb) {
        List<ResourceLocation> skins = npcSkins.get(type);
        if (skins == null || skins.isEmpty()) {
            return Optional.empty();
        }
        synchronized (RNG) {
            RNG.setSeed(idlsb);
            return Optional.of(skins.get(RNG.nextInt(skins.size())));
        }
    }

    public static Set<String> getSkinTypes() {
        return npcSkins.keySet();
    }

    public static List<ResourceLocation> getTypeSkins(String typeName) {
        return npcSkins.getOrDefault(typeName, new ArrayList<>());
    }

    /** Parser of the skin index files (parser name {@code skin_pack}). */
    public static class SkinPackParser implements IRegistryDataParser {
        @Override
        public String getName() {
            return "skin_pack";
        }

        @Override
        public void beginLoad() {
            npcSkins = new HashMap<>();
        }

        @Override
        public void parse(JsonObject json) {
            if (GsonHelper.getAsBoolean(json, "default_pack", false) && !AWNPCStatics.loadDefaultSkinPack) {
                return;
            }
            ResourceLocation folder = ResourceLocation.parse(GsonHelper.getAsString(json, "folder"));
            Map<String, List<ResourceLocation>> skins = new HashMap<>(npcSkins);
            if (json.has("types")) {
                for (Map.Entry<String, JsonElement> entry : GsonHelper.getAsJsonObject(json, "types").entrySet()) {
                    addSkins(skins, entry.getKey(), folder, entry.getValue());
                }
            }
            if (json.has(CUSTOM_TYPE)) {
                addSkins(skins, CUSTOM_TYPE, folder, json.get(CUSTOM_TYPE));
            }
            npcSkins = skins;
            LOGGER.debug("Loaded skin index of {}", folder);
        }

        private static void addSkins(Map<String, List<ResourceLocation>> skins, String type, ResourceLocation folder, JsonElement names) {
            // copy: the lists of an earlier index file are shared with the previous map
            List<ResourceLocation> list = new ArrayList<>(skins.getOrDefault(type, List.of()));
            for (JsonElement name : GsonHelper.convertToJsonArray(names, type)) {
                list.add(folder.withPath("textures/" + folder.getPath() + "/" + GsonHelper.convertToString(name, "skin") + ".png"));
            }
            skins.put(type, list);
        }
    }
}
