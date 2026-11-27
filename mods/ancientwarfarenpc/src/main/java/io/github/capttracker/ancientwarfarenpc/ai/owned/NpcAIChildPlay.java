/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 10, children): what a child does in the day.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.entity.NpcWorker;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.settlement.family.FamilyApi;
import io.github.capttracker.ancientwarfarenpc.settlement.family.Household;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A child plays by day (the schedule goal runs the sleep, the evening at the gathering point and the rain shelter before this goal, the alarm and the
 * flight from hostiles before that): it runs around near the home of its household (else near a gathering point or the hall), follows another child, or
 * follows one of its parents. A choice lasts 5 to 20 seconds. Time with another child or a parent counts as social time for the morale. The child stays
 * on the land of its settlement and inside {@code child_play_radius} blocks of its place.
 */
public class NpcAIChildPlay extends NpcAI<NpcWorker> {
    private enum Mode { WANDER, FOLLOW_PARENT, FOLLOW_CHILD }

    private static final int CHECK_INTERVAL = 10;
    private static final double FOLLOW_STOP = 2.5D * 2.5D;

    private Mode mode = Mode.WANDER;
    private long nextCheck = 0L;
    private int choiceTicks;
    @Nullable
    private UUID followed;
    @Nullable
    private BlockPos spot;
    @Nullable
    private BlockPos anchor;
    private int pauseTicks;
    private int stuckTicks;

    public NpcAIChildPlay(NpcWorker npc) {
        super(npc);
        moveSpeed = 0.85D;
        setMutexBits(MOVE);
    }

    private boolean fits() {
        return npc.getIsAIEnabled() && npc.isChild() && !npc.isSleeping() && !npc.isAlarmed && npc.getTarget() == null && !npc.isPassenger()
                && npc.level() instanceof ServerLevel level && npc.getSettlement().filter(s -> s.getDimension().equals(level.dimension())).isPresent();
    }

    @Override
    public boolean canUse() {
        if (npc.tickCount < nextCheck) {
            return false;
        }
        nextCheck = npc.tickCount + CHECK_INTERVAL;
        return super.canUse() && fits();
    }

    @Override
    public boolean canContinueToUse() {
        return super.canUse() && fits();
    }

    @Override
    public void start() {
        choiceTicks = 0;
        spot = null;
        followed = null;
        pauseTicks = 0;
        moveRetryDelay = 0;
    }

    @Override
    public void stop() {
        moveRetryDelay = 0;
        spot = null;
        followed = null;
        npc.getNavigation().stop();
        npc.removeAITask(TASK_WANDER + TASK_MOVE);
    }

    @Override
    public void tick() {
        if (!(npc.level() instanceof ServerLevel level)) {
            return;
        }
        Settlement settlement = npc.getSettlement().orElse(null);
        if (settlement == null) {
            return;
        }
        if (--choiceTicks <= 0) {
            choose(level, settlement);
        }
        switch (mode) {
            case FOLLOW_PARENT, FOLLOW_CHILD -> tickFollow(level, settlement);
            default -> tickWander(level, settlement);
        }
    }

    // ------------------------------------------------------------ choosing

    private void choose(ServerLevel level, Settlement settlement) {
        choiceTicks = 100 + npc.getRandom().nextInt(300);
        spot = null;
        followed = null;
        pauseTicks = 0;
        anchor = pickAnchor(settlement);
        Entity parent = findParent(level, settlement);
        Entity other = findPlaymate(level, settlement);
        int roll = npc.getRandom().nextInt(100);
        if (parent != null && roll < 30) {
            mode = Mode.FOLLOW_PARENT;
            followed = parent.getUUID();
        } else if (other != null && roll < 65) {
            mode = Mode.FOLLOW_CHILD;
            followed = other.getUUID();
        } else {
            mode = Mode.WANDER;
        }
    }

    /** The place the child plays around: the home of its household most of the time, else a gathering point or the hall. */
    private BlockPos pickAnchor(Settlement settlement) {
        if (SettlementConfig.familiesEnabled) {
            Optional<Household> household = FamilyApi.getHousehold(settlement, npc.getUUID());
            if (household.isPresent()) {
                Optional<FamilyApi.Home> home = FamilyApi.homeOf(settlement, household.get());
                if (home.isPresent() && npc.getRandom().nextInt(4) != 0) {
                    return home.get().centre();
                }
            }
        }
        List<BlockPos> points = settlement.getGatherPoints();
        if (points.isEmpty()) {
            return settlement.getHallPos();
        }
        return points.get(npc.getRandom().nextInt(points.size()));
    }

    @Nullable
    private Entity findParent(ServerLevel level, Settlement settlement) {
        if (!SettlementConfig.familiesEnabled) {
            return null;
        }
        List<Entity> parents = new ArrayList<>(2);
        for (UUID id : FamilyApi.getParents(settlement, npc.getUUID())) {
            Entity entity = level.getEntity(id);
            if (entity instanceof LivingEntity living && living.isAlive() && !living.isSleeping() && npc.distanceToSqr(entity) < 40.0D * 40.0D
                    && settlement.contains(entity.getX(), entity.getZ())) {
                parents.add(entity);
            }
        }
        return parents.isEmpty() ? null : parents.get(npc.getRandom().nextInt(parents.size()));
    }

    @Nullable
    private Entity findPlaymate(ServerLevel level, Settlement settlement) {
        List<NpcWorker> others = level.getEntitiesOfClass(NpcWorker.class, npc.getBoundingBox().inflate(16.0D),
                o -> o != npc && o.isAlive() && o.isChild() && !o.isSleeping() && settlement.getId().equals(o.getSettlementId()));
        return others.isEmpty() ? null : others.get(npc.getRandom().nextInt(others.size()));
    }

    // ------------------------------------------------------------ doing

    private void tickFollow(ServerLevel level, Settlement settlement) {
        Entity target = followed == null ? null : level.getEntity(followed);
        if (target == null || !target.isAlive() || !settlement.contains(target.getX(), target.getZ())) {
            choiceTicks = 0; // it left: choose again
            return;
        }
        npc.setDoing(mode == Mode.FOLLOW_PARENT ? "play.parent" : "play.child", target.blockPosition(), target.getName());
        npc.getLookControl().setLookAt(target, 20.0F, 20.0F);
        double dist = npc.distanceToSqr(target);
        if (dist > FOLLOW_STOP) {
            npc.addAITask(TASK_WANDER + TASK_MOVE);
            moveToEntity(target, dist);
        } else {
            npc.getNavigation().stop();
            npc.removeAITask(TASK_MOVE);
            npc.markSocial();
        }
    }

    private void tickWander(ServerLevel level, Settlement settlement) {
        BlockPos place = anchor == null ? settlement.getHallPos() : anchor;
        npc.setDoing("play", place, Component.empty());
        if (pauseTicks > 0) {
            pauseTicks--;
            npc.getNavigation().stop();
            npc.removeAITask(TASK_MOVE);
            return;
        }
        if (spot == null) {
            spot = randomSpot(level, settlement, place);
            stuckTicks = 0;
            moveRetryDelay = 0;
        }
        double dist = npc.distanceToSqr(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D);
        if (dist < 2.0D || ++stuckTicks > 200 || npc.getNavigation().isDone() && stuckTicks > 20) {
            spot = null;
            pauseTicks = 20 + npc.getRandom().nextInt(80);
            return;
        }
        npc.addAITask(TASK_WANDER + TASK_MOVE);
        moveToPosition(spot, dist);
    }

    /** A free spot near the place, on the land of the settlement and within the play radius of the config. */
    private BlockPos randomSpot(ServerLevel level, Settlement settlement, BlockPos place) {
        int radius = Math.max(3, SettlementConfig.childPlayRadius);
        for (int i = 0; i < 10; i++) {
            int x = place.getX() + npc.getRandom().nextInt(radius * 2 + 1) - radius;
            int z = place.getZ() + npc.getRandom().nextInt(radius * 2 + 1) - radius;
            if (!settlement.contains(x + 0.5D, z + 0.5D)) {
                continue;
            }
            for (int dy = 2; dy >= -2; dy--) {
                BlockPos q = new BlockPos(x, place.getY() + dy, z);
                if (level.isLoaded(q) && level.getBlockState(q).getCollisionShape(level, q).isEmpty()
                        && level.getBlockState(q.above()).getCollisionShape(level, q.above()).isEmpty()
                        && level.getBlockState(q.below()).isFaceSturdy(level, q.below(), net.minecraft.core.Direction.UP)) {
                    return q;
                }
            }
        }
        return place;
    }
}
