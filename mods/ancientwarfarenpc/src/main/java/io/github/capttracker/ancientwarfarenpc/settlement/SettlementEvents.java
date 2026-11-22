/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 1): settlements.
 */
package io.github.capttracker.ancientwarfarenpc.settlement;

import io.github.capttracker.ancientwarfarenpc.init.AwNpcSettlements;
import io.github.capttracker.spectrefactions.api.ActorId;
import io.github.capttracker.spectrefactions.api.FactionEvent;
import io.github.capttracker.spectrefactions.api.FactionsApi;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Game bus handlers of the settlements: the once-a-second upkeep of the records, the faction events that move the owner of a settlement with its
 * founder, and the geometry that goes to players who hold a boundary marker or the ledger.
 */
public final class SettlementEvents {
    public static final SettlementEvents INSTANCE = new SettlementEvents();
    private static final int GEOMETRY_INTERVAL = 10;
    private static final int RECORD_INTERVAL = 20;
    private static final double GEOMETRY_RANGE = 300;
    /** The default colour of a settlement that is not owned by a faction. */
    public static final int DEFAULT_COLOR = 0xB02E26;

    private SettlementEvents() {}

    /** The border and banner colour of an owner: the colour of its faction, or the default. */
    public static int colorOf(ActorId actor) {
        if (actor.isFaction()) {
            return FactionsApi.getFaction(actor.id()).map(f -> f.getColor().getColor()).orElse(DEFAULT_COLOR);
        }
        return DEFAULT_COLOR;
    }

    public static boolean isBorderItem(ItemStack stack) {
        return stack.is(AwNpcSettlements.SETTLEMENT_MARKER_ITEM.get()) || stack.is(AwNpcSettlements.SETTLEMENT_LEDGER.get());
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        if (tick % RECORD_INTERVAL == 0) {
            SettlementData.get(server).serverTick(server);
        }
        if (tick % 5 == 0) {
            SettlementData data = SettlementData.get(server);
            for (Settlement settlement : data.all()) {
                if (settlement.isAlarmActive()) {
                    net.minecraft.server.level.ServerLevel level = server.getLevel(settlement.getDimension());
                    if (level != null) {
                        SettlementLife.ringBells(level, settlement);
                    }
                } else {
                    SettlementLife.ringBells(null, settlement);
                }
            }
        }
        if (tick % GEOMETRY_INTERVAL == 0) {
            sendGeometry(server);
        }
    }

    private static void sendGeometry(MinecraftServer server) {
        SettlementData data = null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isBorderItem(player.getMainHandItem()) && !isBorderItem(player.getOffhandItem())) {
                continue;
            }
            if (data == null) {
                data = SettlementData.get(server);
            }
            ActorId actor = FactionsApi.actorOf(player.getUUID());
            List<SettlementNetwork.Geometry> list = new ArrayList<>();
            for (Settlement settlement : data.inDimension(player.level().dimension())) {
                if (!settlement.getOwnerActor().equals(actor) && !settlement.getFounderId().equals(player.getUUID())) {
                    continue;
                }
                if (settlement.getHallPos().distSqr(player.blockPosition()) > GEOMETRY_RANGE * GEOMETRY_RANGE) {
                    continue;
                }
                if (list.size() >= SettlementNetwork.MAX_SETTLEMENTS) {
                    break;
                }
                List<net.minecraft.core.BlockPos> markers = settlement.getMarkers();
                if (markers.size() > SettlementNetwork.MAX_MARKERS) {
                    markers = markers.subList(0, SettlementNetwork.MAX_MARKERS); // the decoder would drop the rest and misread the payload
                }
                list.add(new SettlementNetwork.Geometry(settlement.getId(), settlement.getHallPos(), new ArrayList<>(markers),
                        SettlementConfig.defaultRadius, colorOf(settlement.getOwnerActor())));
            }
            PacketDistributor.sendToPlayer(player, new SettlementNetwork.GeometryPayload(list));
        }
    }

    // ---------------------------------------------------------------- factions

    @SubscribeEvent
    public void onMemberJoined(FactionEvent.MemberJoined event) {
        withData(data -> data.reconcileFounder(event.getPlayer()));
    }

    @SubscribeEvent
    public void onMemberLeft(FactionEvent.MemberLeft event) {
        withData(data -> data.reconcileFounder(event.getPlayer()));
    }

    /** A disbanded faction's settlements revert to their founders. */
    @SubscribeEvent
    public void onDisbanded(FactionEvent.Disbanded event) {
        withData(data -> data.reconcileActor(ActorId.faction(event.getFaction().getId())));
    }

    private static void withData(java.util.function.Consumer<SettlementData> action) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            action.accept(SettlementData.get(server));
        }
    }
}
