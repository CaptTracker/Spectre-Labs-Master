/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (Spectre Factions integration).
 */
package io.github.capttracker.ancientwarfarenpc.ai;

import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.faction.AwFactions;
import io.github.capttracker.spectrefactions.api.ActorId;
import io.github.capttracker.spectrefactions.api.GuardMode;
import io.github.capttracker.spectrefactions.api.Relation;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Optional;

/**
 * Target goal: defend friends. Every 10 ticks, in the guard modes DEFEND, GUARD and AGGRESSIVE, the NPC looks for living entities of the
 * same actor or of a FRIENDLY or ALLIED actor within 16 blocks whose last attacker is a valid target, and targets that attacker. For
 * ALLIED actors it also takes the current target of a mob of the ally.
 */
public class NpcAIDefendFriends extends TargetGoal {
    private static final int CHECK_INTERVAL = 10;
    private static final double RANGE = 16D;
    /** An attacker counts for this many ticks after the hit. */
    private static final int ATTACK_MEMORY_TICKS = 100;

    private final NpcBase npc;
    @Nullable
    private LivingEntity pendingTarget;

    public NpcAIDefendFriends(NpcBase npc) {
        super(npc, false);
        this.npc = npc;
        setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if ((npc.tickCount + npc.getId()) % CHECK_INTERVAL != 0) {
            return false;
        }
        LivingEntity current = npc.getTarget();
        if (current != null && current.isAlive()) {
            return false;
        }
        GuardMode mode = npc.getGuardMode();
        if (mode == GuardMode.PASSIVE) {
            return false;
        }
        Optional<ActorId> me = npc.getFactionActor();
        if (me.isEmpty()) {
            return false;
        }
        LivingEntity best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (LivingEntity friend : npc.level().getEntitiesOfClass(LivingEntity.class, npc.getBoundingBox().inflate(RANGE), EntitySelector.NO_SPECTATORS)) {
            if (friend == npc) {
                continue;
            }
            LivingEntity attacker = friend.getLastHurtByMob();
            boolean recentAttacker = attacker != null && friend.tickCount - friend.getLastHurtByMobTimestamp() <= ATTACK_MEMORY_TICKS;
            boolean allyMobTarget = friend instanceof Mob allyMob && allyMob.getTarget() != null;
            if (!recentAttacker && !allyMobTarget) {
                continue;
            }
            Relation relation = relationOf(me.get(), friend);
            if (relation == null || !relation.isFriendlyOrBetter()) {
                continue;
            }
            if (recentAttacker && isValid(attacker, mode)) {
                double d = npc.distanceToSqr(attacker);
                if (d < bestDistSq) {
                    best = attacker;
                    bestDistSq = d;
                }
            }
            if (allyMobTarget && relation == Relation.ALLIED) {
                LivingEntity allyTarget = ((Mob) friend).getTarget();
                if (isValid(allyTarget, mode)) {
                    double d = npc.distanceToSqr(allyTarget);
                    if (d < bestDistSq) {
                        best = allyTarget;
                        bestDistSq = d;
                    }
                }
            }
        }
        pendingTarget = best;
        return best != null;
    }

    @Nullable
    private Relation relationOf(ActorId me, LivingEntity friend) {
        Optional<ActorId> other = AwFactions.actorOf(npc.level(), friend);
        return other.map(actor -> AwFactions.relation(npc.level(), me, actor)).orElse(null);
    }

    private boolean isValid(@Nullable LivingEntity candidate, GuardMode mode) {
        if (candidate == null || !AIHelper.isTarget(npc, candidate, false)) {
            return false;
        }
        // never a friend of ours, even if one of ours was hit by it
        Optional<Relation> relation = npc.getRelationTo(candidate);
        if (relation.isPresent() && relation.get().isFriendlyOrBetter()) {
            return false;
        }
        return mode != GuardMode.GUARD || npc.isWithinGuardArea(candidate);
    }

    @Override
    public void start() {
        npc.setTarget(pendingTarget);
        targetMob = pendingTarget;
        super.start();
    }
}
