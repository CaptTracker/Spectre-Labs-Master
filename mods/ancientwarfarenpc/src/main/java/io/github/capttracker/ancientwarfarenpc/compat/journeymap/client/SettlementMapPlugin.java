/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (third round): JourneyMap plugin (API 2.0). Compile-only dependency: this class is found and loaded by JourneyMap itself, so it is
 * never loaded when JourneyMap is not installed.
 */
package io.github.capttracker.ancientwarfarenpc.compat.journeymap.client;

import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.settlement.ClientSettlementMap;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementArea;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementMapSync;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.client.display.DisplayType;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.common.event.ClientEventRegistry;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the settlements the player owns or belongs to: a waypoint at each hall and the border as a polygon overlay (the polygon of the markers, or a
 * circle of the default radius while a settlement has fewer than three markers). The list comes from {@link ClientSettlementMap}, which the server
 * keeps current. The client config section {@code journeymap} switches it (and the two parts) off. Nothing here is saved by JourneyMap
 * (non-persistent waypoints; overlays are never saved).
 */
@JourneyMapPlugin(apiVersion = IClientAPI.API_VERSION)
public class SettlementMapPlugin implements IClientPlugin {
    private static final int CHECK_TICKS = 20;
    private static final int CIRCLE_POINTS = 32;

    private static final Logger LOGGER = LogUtils.getLogger();

    private IClientAPI api;
    private boolean failed = false;
    private int shownVersion = -1;
    private boolean shownEnabled = false;
    private int shownFlags = -1;
    private int ticks = 0;

    @Override
    public void initialize(IClientAPI jmClientApi) {
        this.api = jmClientApi;
        ClientEventRegistry.MAPPING_EVENT.subscribe(AncientWarfareNPC.MOD_ID, event -> shownVersion = -1);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
    }

    @Override
    public String getModId() {
        return AncientWarfareNPC.MOD_ID;
    }

    private void onClientTick(ClientTickEvent.Post event) {
        if (failed || ++ticks < CHECK_TICKS) {
            return;
        }
        ticks = 0;
        try {
            refresh();
        } catch (Throwable t) {
            LOGGER.warn("JourneyMap settlement display failed, switching it off for this session", t);
            clear();
            failed = true;
        }
    }

    private void refresh() throws Exception {
        boolean enabled = AWNPCStatics.journeyMapEnabled;
        int flags = (AWNPCStatics.journeyMapWaypoints ? 1 : 0) | (AWNPCStatics.journeyMapBorders ? 2 : 0);
        int version = ClientSettlementMap.version();
        if (enabled == shownEnabled && flags == shownFlags && version == shownVersion) {
            return;
        }
        shownEnabled = enabled;
        shownFlags = flags;
        shownVersion = version;
        clear();
        if (!enabled) {
            return;
        }
        for (SettlementMapSync.Entry entry : ClientSettlementMap.get()) {
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, entry.dimension());
            if (AWNPCStatics.journeyMapWaypoints) {
                Waypoint waypoint = WaypointFactory.createClientWaypoint(AncientWarfareNPC.MOD_ID, entry.hall(), entry.name(), dimension, false);
                waypoint.setColor(entry.color() & 0xFFFFFF);
                api.addWaypoint(AncientWarfareNPC.MOD_ID, waypoint);
            }
            if (AWNPCStatics.journeyMapBorders) {
                MapPolygon polygon = new MapPolygon(border(entry));
                ShapeProperties shape = new ShapeProperties().setStrokeColor(entry.color() & 0xFFFFFF).setStrokeOpacity(0.9F).setStrokeWidth(2.0F)
                        .setFillColor(entry.color() & 0xFFFFFF).setFillOpacity(0.12F);
                PolygonOverlay overlay = new PolygonOverlay(AncientWarfareNPC.MOD_ID, dimension, shape, polygon);
                overlay.setTitle(entry.name()).setLabel(entry.name()).setOverlayGroupName(AncientWarfareNPC.MOD_ID);
                api.show(overlay);
            }
        }
    }

    private static List<BlockPos> border(SettlementMapSync.Entry entry) {
        List<BlockPos> polygon = SettlementArea.polygon(entry.hall(), entry.markers());
        if (!polygon.isEmpty()) {
            return polygon;
        }
        List<BlockPos> circle = new ArrayList<>();
        for (int i = 0; i < CIRCLE_POINTS; i++) {
            double angle = 2 * Math.PI * i / CIRCLE_POINTS;
            circle.add(new BlockPos(entry.hall().getX() + (int) Math.round(Math.cos(angle) * entry.radius()), entry.hall().getY(),
                    entry.hall().getZ() + (int) Math.round(Math.sin(angle) * entry.radius())));
        }
        return circle;
    }

    private void clear() {
        api.removeAll(AncientWarfareNPC.MOD_ID, DisplayType.Polygon);
        api.removeAllWaypoints(AncientWarfareNPC.MOD_ID);
    }
}
