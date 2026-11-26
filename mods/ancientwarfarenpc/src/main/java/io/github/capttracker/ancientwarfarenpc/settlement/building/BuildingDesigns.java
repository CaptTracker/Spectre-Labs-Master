/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): building designs.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The designs of the server: the built-in ones (datapack folder {@code data/<namespace>/settlement_designs/}, loaded on every reload and
 * validated, bad ones are skipped with a warning) and the player blueprints ({@link BlueprintStore}). Also sends the names and numbers of the
 * designs a player may use to that player's client. Server side only (the client keeps {@link DesignMeta} lists).
 */
public final class BuildingDesigns {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The datapack folder of the built-in designs. */
    public static final String FOLDER = "settlement_designs";

    private static volatile Map<ResourceLocation, BuildingDesign> builtIn = Map.of();

    private BuildingDesigns() {}

    public static Collection<BuildingDesign> builtIn() {
        return builtIn.values();
    }

    /** A design by id: a built-in design or any player blueprint. Check {@link #usableBy} before giving it to a player. */
    @Nullable
    public static BuildingDesign get(ResourceLocation id) {
        BuildingDesign design = builtIn.get(id);
        return design != null ? design : BlueprintStore.get(id);
    }

    /** Every design the settlements of this owner may use: all built-in designs and the blueprints of that player. */
    public static List<BuildingDesign> availableTo(UUID owner) {
        List<BuildingDesign> list = new ArrayList<>(builtIn.values());
        list.addAll(BlueprintStore.of(owner));
        return list;
    }

    public static boolean usableBy(BuildingDesign design, UUID owner) {
        return design.getOwner() == null || design.getOwner().equals(owner);
    }

    // ---- sync ----

    public static void sync(ServerPlayer player) {
        List<DesignMeta> metas = new ArrayList<>();
        for (BuildingDesign design : availableTo(player.getUUID())) {
            if (metas.size() >= DesignNetwork.MAX_METAS) {
                break;
            }
            metas.add(design.meta());
        }
        PacketDistributor.sendToPlayer(player, new DesignNetwork.ListPayload(metas));
    }

    private static void syncAll(MinecraftServer server) {
        if (server.getPlayerList() != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                sync(player);
            }
        }
    }

    // ---- loading ----

    @EventBusSubscriber(modid = AncientWarfareNPC.MOD_ID)
    public static final class Events {
        private Events() {}

        @SubscribeEvent
        public static void onAddReloadListeners(AddReloadListenerEvent event) {
            event.addListener(new Loader());
        }

        @SubscribeEvent
        public static void onServerStarted(ServerStartedEvent event) {
            BlueprintStore.load(event.getServer());
        }

        @SubscribeEvent
        public static void onServerStopped(ServerStoppedEvent event) {
            BlueprintStore.unload();
        }

        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                sync(player);
            }
        }
    }

    private static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() {
            super(new Gson(), FOLDER);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, BuildingDesign> loaded = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet()) {
                ResourceLocation id = file.getKey();
                if (!file.getValue().isJsonObject()) {
                    LOGGER.warn("Settlement design {}: the file is not a JSON object, skipped", id);
                    continue;
                }
                JsonObject json = file.getValue().getAsJsonObject();
                try {
                    BuildingDesign design = BuildingDesign.parse(id, json, null);
                    for (String warning : design.getWarnings()) {
                        LOGGER.warn("Settlement design {}: {}", id, warning);
                    }
                    loaded.put(id, design);
                } catch (DesignException | RuntimeException e) {
                    LOGGER.warn("Settlement design {} skipped: {}", id, e.getMessage());
                }
            }
            builtIn = Map.copyOf(loaded);
            LOGGER.info("Loaded {} settlement designs", loaded.size());
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                syncAll(server);
            }
        }
    }
}
