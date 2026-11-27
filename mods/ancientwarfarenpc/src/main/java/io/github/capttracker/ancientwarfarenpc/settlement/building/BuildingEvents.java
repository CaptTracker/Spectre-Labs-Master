/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): building zones.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcSettlements;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementApi;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Sends the plots and build footprints to the players who hold the building zone tool (every half second). */
@EventBusSubscriber(modid = AncientWarfareNPC.MOD_ID)
public final class BuildingEvents {
    private static final int INTERVAL = 10;
    private static final double RANGE = 300;

    private BuildingEvents() {}

    private static boolean isZoneTool(ItemStack stack) {
        return stack.is(AwNpcSettlements.BUILDING_ZONE_TOOL.get());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % INTERVAL != 0) {
            return;
        }
        SettlementData data = null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isZoneTool(player.getMainHandItem()) && !isZoneTool(player.getOffhandItem())) {
                continue;
            }
            if (data == null) {
                data = SettlementData.get(server);
            }
            List<BuildingNetwork.SettlementPlots> list = new ArrayList<>();
            for (Settlement settlement : data.inDimension(player.level().dimension())) {
                if (!SettlementApi.mayManage(settlement, player) || settlement.getHallPos().distSqr(player.blockPosition()) > RANGE * RANGE) {
                    continue;
                }
                if (list.size() >= BuildingNetwork.MAX_SETTLEMENTS) {
                    break;
                }
                SettlementBuilding building = settlement.getBuilding();
                List<BuildingNetwork.PlotView> plots = new ArrayList<>();
                for (BuildingPlot plot : building.getPlots()) {
                    plots.add(new BuildingNetwork.PlotView(plot.getId(), plot.getNumber(), plot.getMinX(), plot.getMinZ(), plot.getMaxX(), plot.getMaxZ(), plot.getY()));
                }
                List<BuildingNetwork.ProjectView> projects = new ArrayList<>();
                for (BuildProject project : building.getProjects()) {
                    int[] size = project.getSize();
                    projects.add(new BuildingNetwork.ProjectView(project.getOrigin(), size[0], size[1], size[2], project.getFacing(), project.getStatus().ordinal(),
                            project.getKind().ordinal()));
                }
                list.add(new BuildingNetwork.SettlementPlots(settlement.getId(), plots, projects));
            }
            PacketDistributor.sendToPlayer(player, new BuildingNetwork.PlotsPayload(list));
        }
    }
}
