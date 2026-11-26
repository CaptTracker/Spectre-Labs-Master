/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): building designs. Client only.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building.client;

import com.google.gson.JsonObject;
import io.github.capttracker.ancientwarfarenpc.settlement.building.BuildingDesign;
import io.github.capttracker.ancientwarfarenpc.settlement.building.DesignMeta;
import io.github.capttracker.ancientwarfarenpc.settlement.building.DesignNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the client knows of the designs: the names and numbers of the designs the player may use (sent by the server on login, on a reload and
 * when a blueprint is saved) and the full blocks of a design when it was asked for (for ghosts). Also the entry for the scanner screen.
 */
public final class DesignClientHooks {
    private static volatile Map<ResourceLocation, DesignMeta> metas = Map.of();
    private static final Map<ResourceLocation, BuildingDesign> FULL = new HashMap<>();
    private static final Set<ResourceLocation> REQUESTED = new HashSet<>();

    private DesignClientHooks() {}

    public static void setMetas(List<DesignMeta> list) {
        Map<ResourceLocation, DesignMeta> map = new LinkedHashMap<>();
        for (DesignMeta meta : list) {
            map.put(meta.id(), meta);
        }
        metas = map;
        FULL.clear();
        REQUESTED.clear();
    }

    public static List<DesignMeta> metas() {
        return List.copyOf(metas.values());
    }

    @Nullable
    public static DesignMeta meta(ResourceLocation id) {
        return metas.get(id);
    }

    public static void setFull(ResourceLocation id, String json) {
        JsonObject object = DesignNetwork.parseObject(json);
        if (object != null) {
            try {
                FULL.put(id, BuildingDesign.parse(id, object, null));
            } catch (Exception ignored) {
                // a design the client cannot parse (a block it does not know) just has no ghost
            }
        }
    }

    /** The full design when it was received, else {@code null} (it is requested from the server, once). */
    @Nullable
    public static BuildingDesign full(ResourceLocation id) {
        BuildingDesign design = FULL.get(id);
        if (design == null && metas.containsKey(id) && REQUESTED.add(id)) {
            PacketDistributor.sendToServer(new DesignNetwork.RequestPayload(id));
        }
        return design;
    }

    public static void openScanner(DesignNetwork.OpenScannerPayload payload) {
        Minecraft.getInstance().setScreen(new GuiBlueprintScanner(payload));
    }
}
