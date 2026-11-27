/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 2): the daily schedule.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.npccommand.NpcCommand.Command;
import io.github.capttracker.ancientwarfarenpc.settlement.Activity;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementLife;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * The daily schedule of a member of a settlement. It sits below hunger, alarms, player commands and orders (an NPC with orders is not scheduled)
 * and above the work and wander goals, so outside of the work band it takes the NPC away from its job:
 * <ul>
 *   <li>sleep: lies down in the bed the settlement assigned, or rests by the hall when it has none (homeless); a combat NPC on night watch patrols
 *       the border instead;</li>
 *   <li>socialise: idles around the gathering point it picked (the hall, a campfire or bell inside the land, a bard) and looks at the others;</li>
 *   <li>rain: an NPC that is not working stands under a roof inside the land;</li>
 *   <li>wake and work: nothing, the other goals run (the morning meal is part of the food goal).</li>
 * </ul>
 * The plan is chosen every 10 ticks.
 */
public class NpcAIScheduleGoal extends NpcAI<NpcPlayerOwned> {
    private enum Plan { NONE, SLEEP, SOCIAL, SHELTER, WATCH }

    private static final int DECIDE_INTERVAL = 10;
    private static final int HURT_COOLDOWN = 200;

    private Plan plan = Plan.NONE;
    private long nextDecision = 0;
    private long noSleepUntil = 0;
    @Nullable
    private Settlement settlement;

    // working state of the plans
    @Nullable
    private BlockPos spot;
    private int spotTicks;
    private int idleTicks;
    private int lookTicks;
    @Nullable
    private NpcBase lookTarget;
    private int patrolIndex;
    private int pauseTicks;
    private long shelterRetry;

    public NpcAIScheduleGoal(NpcPlayerOwned npc) {
        super(npc);
        setMutexBits(MOVE + ATTACK);
    }

    private Plan decide() {
        settlement = null;
        if (!SettlementConfig.scheduleEnabled || npc.isAlarmed || npc.getTarget() != null || npc.isPassenger()
                || !npc.ordersStack.isEmpty() || npc.getCurrentCommand() != Command.NONE || !(npc.level() instanceof ServerLevel level)) {
            return Plan.NONE;
        }
        Optional<Settlement> found = npc.getSettlement();
        if (found.isEmpty() || !found.get().getDimension().equals(level.dimension())) {
            return Plan.NONE;
        }
        settlement = found.get();
        boolean raining = SettlementConfig.rainShelter && level.isRaining();
        switch (npc.getActivity()) {
            case SLEEP:
                if (npc.isOnNightWatch()) {
                    return Plan.WATCH;
                }
                return level.getGameTime() < noSleepUntil ? Plan.NONE : Plan.SLEEP;
            case SOCIALISE:
                if (npc.getNpcType().equals("bard")) {
                    return Plan.NONE; // the bard is a gathering point and keeps singing
                }
                return raining ? Plan.SHELTER : Plan.SOCIAL;
            default:
                if (raining && !npc.isOnDuty()) {
                    return Plan.SHELTER;
                }
                return npc.shouldPatrolBorder() ? Plan.WATCH : Plan.NONE; // border patrol by day
        }
    }

    @Override
    public boolean canUse() {
        if (!super.canUse() || npc.tickCount < nextDecision) {
            return false;
        }
        nextDecision = npc.tickCount + DECIDE_INTERVAL;
        plan = decide();
        return plan != Plan.NONE;
    }

    @Override
    public boolean canContinueToUse() {
        if (!npc.getIsAIEnabled() || npc.isAIFlagStopped(getMutexBits())) {
            return false;
        }
        if (plan == Plan.SLEEP && npc.hurtTime > 0) {
            noSleepUntil = npc.level().getGameTime() + HURT_COOLDOWN;
            return false;
        }
        if (npc.tickCount >= nextDecision) {
            nextDecision = npc.tickCount + DECIDE_INTERVAL;
            Plan next = decide();
            if (next != plan) {
                plan = next;
                if (next != Plan.NONE) {
                    resetPlanState();
                }
            }
        }
        return plan != Plan.NONE;
    }

    private void resetPlanState() {
        spot = null;
        spotTicks = 0;
        idleTicks = 0;
        lookTicks = 0;
        lookTarget = null;
        pauseTicks = 0;
        npc.restingAtHall = false;
        npc.removeAITask(TASK_SLEEP + TASK_RAIN + TASK_PATROL + TASK_MOVE);
    }

    @Override
    public void start() {
        resetPlanState();
        patrolIndex = npc.getRandom().nextInt(8);
        shelterRetry = 0;
    }

    @Override
    public void stop() {
        moveRetryDelay = 0;
        plan = Plan.NONE;
        npc.restingAtHall = false;
        npc.getNavigation().stop();
        if (npc.isSleeping()) {
            npc.wakeUp();
        }
        npc.removeAITask(TASK_SLEEP + TASK_RAIN + TASK_PATROL + TASK_MOVE);
        lookTarget = null;
    }

    @Override
    public void tick() {
        Settlement s = settlement;
        if (s == null || !(npc.level() instanceof ServerLevel level)) {
            return;
        }
        switch (plan) {
            case SLEEP -> tickSleep(level, s);
            case SOCIAL -> tickSocial(level, s);
            case SHELTER -> tickShelter(level, s);
            case WATCH -> tickWatch(level, s);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------ helpers

    /** Walks to the position until it is within the range; returns true when there. */
    private boolean walkTo(BlockPos pos, double range) {
        double dist = npc.distanceToSqr(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        if (dist > range * range) {
            npc.addAITask(TASK_MOVE);
            moveToPosition(pos, dist);
            return false;
        }
        npc.removeAITask(TASK_MOVE);
        return true;
    }

    private void standStill() {
        npc.getNavigation().stop();
        npc.removeAITask(TASK_MOVE);
    }

    /** A stable pseudo-random spot on a ring around a centre, one per NPC and day. */
    private BlockPos ringSpot(ServerLevel level, BlockPos centre, int minR, int maxR, boolean fresh) {
        for (int i = 0; i < 8; i++) {
            double angle = npc.getRandom().nextDouble() * Math.PI * 2;
            double r = minR + npc.getRandom().nextDouble() * Math.max(0.5, maxR - minR);
            BlockPos p = BlockPos.containing(centre.getX() + 0.5 + Math.cos(angle) * r, centre.getY(), centre.getZ() + 0.5 + Math.sin(angle) * r);
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos q = p.above(dy);
                if (level.isLoaded(q) && level.getBlockState(q).getCollisionShape(level, q).isEmpty()
                        && level.getBlockState(q.above()).getCollisionShape(level, q.above()).isEmpty()) {
                    return q;
                }
            }
        }
        return centre;
    }

    // ------------------------------------------------------------ sleep

    private void tickSleep(ServerLevel level, Settlement s) {
        npc.addAITask(TASK_SLEEP);
        if (npc.isSleeping()) {
            if (npc.tickCount % 10 == 0) {
                if (npc.isBedCacheValid()) {
                    npc.setPositionToBed();
                } else {
                    npc.wakeUp();
                }
            }
            return;
        }
        Optional<BlockPos> bed = s.getBedOf(npc.getUUID());
        if (bed.isPresent() && level.isLoaded(bed.get())) {
            BlockState state = level.getBlockState(bed.get());
            if (state.getBlock() instanceof BedBlock && !state.getValue(BedBlock.OCCUPIED)) {
                npc.restingAtHall = false;
                npc.assignBed(bed.get());
                if (walkTo(bed.get(), AWNPCStatics.npcActionRange)) {
                    if (npc.lieDown(bed.get())) { // the thought "slept" is written when it gets up
                        npc.setPositionToBed();
                        npc.getNavigation().stop();
                    }
                }
                return;
            }
        }
        // homeless (or the bed is taken tonight): rest by the hall
        BlockPos hall = s.getHallPos();
        if (spot == null) {
            spot = ringSpot(level, hall, 2, 4, true);
        }
        if (walkTo(spot, 1.5D)) {
            standStill();
            npc.restingAtHall = true;
            npc.think("rested_hall");
        }
        npc.think("homeless");
    }

    // ------------------------------------------------------------ socialise

    @Nullable
    private BlockPos gatherPoint(Settlement s) {
        List<BlockPos> points = s.getGatherPoints();
        if (points.isEmpty()) {
            return s.getHallPos();
        }
        long day = npc.level().getDayTime() / 24000L;
        // the two partners of a couple go to the same place (rework phase 10)
        java.util.UUID key = npc.getUUID();
        if (SettlementConfig.familiesEnabled) {
            java.util.Optional<java.util.UUID> partner = io.github.capttracker.ancientwarfarenpc.settlement.family.FamilyApi.getPartner(s, key);
            if (partner.isPresent() && partner.get().compareTo(key) < 0) {
                key = partner.get();
            }
        }
        int index = (int) Math.floorMod(key.hashCode() + day, (long) points.size());
        return points.get(index);
    }

    private void tickSocial(ServerLevel level, Settlement s) {
        BlockPos point = gatherPoint(s);
        if (point == null) {
            return;
        }
        int radius = SettlementConfig.gatherRadius;
        if (spot == null) {
            if (!walkTo(point, radius)) {
                return;
            }
            spot = ringSpot(level, point, 1, radius, true);
            idleTicks = 0;
        }
        if (!point.closerThan(spot, radius + 2.0)) {
            spot = null; // the point moved (a bard): follow it
            return;
        }
        boolean there = walkTo(spot, 1.2D);
        if (there || npc.getNavigation().isDone()) {
            standStill();
            idleTicks++;
            npc.markSocial();
            lookAtOthers(level);
            if (idleTicks > 100 + npc.getRandom().nextInt(120)) {
                spot = ringSpot(level, point, 1, radius, true);
                idleTicks = 0;
            }
        }
    }

    private void lookAtOthers(ServerLevel level) {
        lookTicks--;
        if (lookTicks <= 0 || lookTarget == null || !lookTarget.isAlive()) {
            lookTicks = 40 + npc.getRandom().nextInt(60);
            lookTarget = null;
            AABB box = npc.getBoundingBox().inflate(8.0D);
            double best = Double.MAX_VALUE;
            for (NpcBase other : level.getEntitiesOfClass(NpcBase.class, box, o -> o != npc && o.isAlive() && !o.isSleeping())) {
                double d = npc.distanceToSqr(other);
                if (d < best && (lookTarget == null || npc.getRandom().nextInt(3) != 0)) {
                    best = d;
                    lookTarget = other;
                }
            }
        }
        if (lookTarget != null) {
            npc.getLookControl().setLookAt(lookTarget, 30.0F, 30.0F);
        }
    }

    // ------------------------------------------------------------ rain shelter

    private static boolean isRoofed(ServerLevel level, BlockPos pos) {
        return SettlementLife.isRoofed(level, pos);
    }

    private void tickShelter(ServerLevel level, Settlement s) {
        npc.addAITask(TASK_RAIN);
        npc.think("rain");
        if (spot == null) {
            if (level.getGameTime() < shelterRetry) {
                return;
            }
            spot = findShelter(level, s);
            spotTicks = 0;
            if (spot == null) {
                shelterRetry = level.getGameTime() + 100;
                return;
            }
        }
        spotTicks++;
        if (walkTo(spot, 1.5D)) {
            standStill();
        } else if (spotTicks > 600) {
            spot = null; // unreachable
            shelterRetry = level.getGameTime() + 200;
        }
    }

    private BlockPos findShelter(ServerLevel level, Settlement s) {
        return SettlementLife.findShelter(level, s, npc.blockPosition(), null, npc.getRandom());
    }

    // ------------------------------------------------------------ night watch

    private void tickWatch(ServerLevel level, Settlement s) {
        npc.addAITask(TASK_PATROL);
        npc.think("watch");
        if (pauseTicks > 0) {
            pauseTicks--;
            standStill();
            return;
        }
        List<BlockPos> points = SettlementLife.patrolPoints(level, s);
        BlockPos target = points.get(Math.floorMod(patrolIndex, points.size()));
        spotTicks++;
        if (walkTo(target, 3.0D) || spotTicks > 600) {
            patrolIndex++;
            spotTicks = 0;
            pauseTicks = 60 + npc.getRandom().nextInt(60);
        }
    }

    /** Used by tests and other goals: whether this goal currently holds the NPC. */
    public boolean isActive() {
        return plan != Plan.NONE;
    }
}
