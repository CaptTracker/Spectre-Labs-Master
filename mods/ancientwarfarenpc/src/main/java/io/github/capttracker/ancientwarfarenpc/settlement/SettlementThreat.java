/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 2): threat response.
 */
package io.github.capttracker.ancientwarfarenpc.settlement;

import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.faction.AwFactions;
import io.github.capttracker.spectrefactions.api.ActorId;
import io.github.capttracker.spectrefactions.api.Relation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Once a second, from {@link SettlementData#serverTick}: looks for hostile entities inside the land (loaded chunks only, since only loaded entities
 * exist), keeps the list on the settlement for the NPC goals, and raises and ends the automatic alarm.
 *
 * <p>An entity is a threat if its actor (a player, a faction NPC, an owned NPC, a pet) is HOSTILE to the settlement's owner actor, or, when it has no
 * actor, if it is a hostile mob ({@link Enemy}). Creative and spectator players are never threats.
 */
public final class SettlementThreat {
    private static final int MAX_TRACKED = 32;

    private SettlementThreat() {}

    public static void tick(ServerLevel level, Settlement settlement) {
        List<LivingEntity> found = scan(level, settlement);
        BlockPos hall = settlement.getHallPos();
        found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(hall.getX() + 0.5D, hall.getY(), hall.getZ() + 0.5D)));
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < found.size() && i < MAX_TRACKED; i++) {
            ids.add(found.get(i).getUUID());
        }
        settlement.setThreats(List.copyOf(ids), found.isEmpty() ? null : found.get(0).blockPosition());

        long now = level.getGameTime();
        if (found.isEmpty()) {
            settlement.clearAlarmSuppressed();
            if (settlement.isAlarmAuto()) {
                if (settlement.getThreatClearSince() < 0) {
                    settlement.setThreatClearSince(now);
                } else if (now - settlement.getThreatClearSince() >= settlement.getAlarmClearTicks()) {
                    settlement.setAlarm(false, false);
                }
            }
            return;
        }
        settlement.setThreatClearSince(-1L);
        if (settlement.isAutoAlarmEnabled() && !settlement.isAlarmActive() && !settlement.isAlarmSuppressed()
                && found.size() >= settlement.getAutoAlarmThreshold()) {
            settlement.setAlarm(true, true);
            announce(level, settlement, found.get(0), found.size());
        }
    }

    private static List<LivingEntity> scan(ServerLevel level, Settlement settlement) {
        int[] b = SettlementLife.landBounds(settlement);
        AABB box = new AABB(b[0], level.getMinBuildHeight(), b[1], b[2] + 1, level.getMaxBuildHeight(), b[3] + 1);
        return new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && settlement.contains(e.getX(), e.getZ()) && nearSurface(level, e) && isThreat(level, settlement, e)));
    }

    /**
     * The land reaches from bedrock to the build limit, so without this every cave mob under the settlement would hold the alarm on for good. An entity
     * counts only if it is no more than {@code threat_max_depth} blocks below the surface of its column (houses and walls still count).
     */
    private static boolean nearSurface(ServerLevel level, Entity e) {
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, e.getBlockX(), e.getBlockZ());
        return e.getY() >= surface - SettlementConfig.threatMaxDepth;
    }

    /** Tells the founder, if online, why the automatic alarm went off. */
    private static void announce(ServerLevel level, Settlement settlement, LivingEntity nearest, int count) {
        ServerPlayer founder = level.getServer().getPlayerList().getPlayer(settlement.getFounderId());
        if (founder != null) {
            BlockPos p = nearest.blockPosition();
            founder.sendSystemMessage(Component.translatable("guistrings.settlement.alarm_raised", settlement.getName(), count,
                    nearest.getDisplayName(), p.getX(), p.getY(), p.getZ()));
        }
    }

    /** Whether the entity is hostile to the settlement. */
    public static boolean isThreat(ServerLevel level, Settlement settlement, Entity e) {
        if (e instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        Optional<ActorId> actor = AwFactions.actorOf(level, e);
        if (actor.isPresent()) {
            return AwFactions.relation(level, settlement.getOwnerActor(), actor.get()) == Relation.HOSTILE;
        }
        return !(e instanceof NpcBase) && e instanceof Enemy;
    }
}
