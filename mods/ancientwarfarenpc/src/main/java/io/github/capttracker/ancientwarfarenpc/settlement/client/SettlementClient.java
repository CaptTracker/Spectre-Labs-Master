/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 1): settlements.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.client;

import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcSettlements;
import io.github.capttracker.ancientwarfarenpc.settlement.ClientSettlementView;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementArea;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementEvents;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementMarkerBlockEntity;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

import java.util.List;

/** Client side of the settlements: the banner tint of the markers and the border particles shown while holding a marker or the ledger. */
public final class SettlementClient {
    private static final double POINT_SPACING = 2.0D;
    private static final double VIEW_RANGE = 40.0D;

    private SettlementClient() {}

    @EventBusSubscriber(modid = AncientWarfareNPC.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
            event.register((state, level, pos, tintIndex) -> {
                if (level != null && pos != null && level.getBlockEntity(pos) instanceof SettlementMarkerBlockEntity marker) {
                    return marker.getColor();
                }
                return SettlementEvents.DEFAULT_COLOR;
            }, AwNpcSettlements.SETTLEMENT_MARKER.get());
        }

        @SubscribeEvent
        public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
            event.register((stack, tintIndex) -> 0xFF000000 | SettlementEvents.DEFAULT_COLOR, AwNpcSettlements.SETTLEMENT_MARKER_ITEM.get());
        }
    }

    @EventBusSubscriber(modid = AncientWarfareNPC.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        private GameBus() {}

        @SubscribeEvent
        public static void onLoggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
            io.github.capttracker.ancientwarfarenpc.settlement.ClientSettlementMap.clear();
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null || mc.level == null || mc.isPaused() || mc.level.getGameTime() % 4 != 0) {
                return;
            }
            if (!SettlementEvents.isBorderItem(player.getMainHandItem()) && !SettlementEvents.isBorderItem(player.getOffhandItem())) {
                return;
            }
            for (SettlementNetwork.Geometry geometry : ClientSettlementView.get()) {
                drawBorder(mc, player, geometry);
            }
        }
    }

    private static void drawBorder(Minecraft mc, LocalPlayer player, SettlementNetwork.Geometry geometry) {
        Vector3f color = Vec3.fromRGB24(geometry.color()).toVector3f();
        DustParticleOptions particle = new DustParticleOptions(color, 1.3F);
        List<BlockPos> polygon = SettlementArea.polygon(geometry.hall(), geometry.markers());
        if (!polygon.isEmpty()) {
            for (int i = 0; i < polygon.size(); i++) {
                BlockPos a = polygon.get(i);
                BlockPos b = polygon.get((i + 1) % polygon.size());
                double ax = a.getX() + 0.5D;
                double az = a.getZ() + 0.5D;
                double dx = b.getX() + 0.5D - ax;
                double dz = b.getZ() + 0.5D - az;
                int steps = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dz * dz) / POINT_SPACING));
                for (int s = 0; s < steps; s++) {
                    spawn(mc, player, particle, ax + dx * s / steps, az + dz * s / steps);
                }
            }
        } else {
            double cx = geometry.hall().getX() + 0.5D;
            double cz = geometry.hall().getZ() + 0.5D;
            int steps = Math.max(8, (int) Math.ceil(2 * Math.PI * geometry.radius() / POINT_SPACING));
            for (int s = 0; s < steps; s++) {
                double angle = 2 * Math.PI * s / steps;
                spawn(mc, player, particle, cx + Math.cos(angle) * geometry.radius(), cz + Math.sin(angle) * geometry.radius());
            }
        }
    }

    private static void spawn(Minecraft mc, LocalPlayer player, DustParticleOptions particle, double x, double z) {
        double dx = x - player.getX();
        double dz = z - player.getZ();
        if (dx * dx + dz * dz > VIEW_RANGE * VIEW_RANGE) {
            return;
        }
        double y = player.getY() - 1.0D + mc.level.random.nextDouble() * 5.0D;
        mc.level.addParticle(particle, x, y, z, 0, 0, 0);
    }
}
