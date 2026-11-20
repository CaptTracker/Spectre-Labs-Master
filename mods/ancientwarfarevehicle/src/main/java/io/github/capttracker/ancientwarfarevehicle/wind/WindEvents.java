/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): ticks the wind and tells the players.
 */
package io.github.capttracker.ancientwarfarevehicle.wind;

import io.github.capttracker.ancientwarfarevehicle.AncientWarfareVehicles;
import io.github.capttracker.ancientwarfarevehicle.network.VehicleEntityPayloads;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Drifts the wind every tick, sends it to everyone every 5 seconds and to a player who joins, respawns or changes dimension. */
@EventBusSubscriber(modid = AncientWarfareVehicles.MOD_ID)
public final class WindEvents {
    private static final int SYNC_INTERVAL = 100;
    private static final int SAVE_INTERVAL = 1200;

    private WindEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        WindData wind = WindData.get(server);
        wind.tick();
        long time = server.overworld().getGameTime();
        if (time % SAVE_INTERVAL == 0) {
            wind.touch();
        }
        if (time % SYNC_INTERVAL == 0) {
            PacketDistributor.sendToAllPlayers(payload(wind));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        send(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        send(event.getEntity());
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        send(event.getEntity());
    }

    private static void send(net.minecraft.world.entity.player.Player player) {
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.getServer() != null) {
            PacketDistributor.sendToPlayer(serverPlayer, payload(WindData.get(serverPlayer.getServer())));
        }
    }

    private static VehicleEntityPayloads.WindSync payload(WindData wind) {
        return new VehicleEntityPayloads.WindSync(wind.direction(), wind.strength(), wind.turn());
    }
}
