/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/NpcAIAttackNearest.
 */
package io.github.capttracker.ancientwarfarenpc.ai;

import io.github.capttracker.ancientwarfarenpc.compat.ModCompat;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.spectrefactions.api.GuardMode;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * Target goal: the nearest entity the selector accepts that the NPC may target ({@link AIHelper#isTarget}, line of sight required). Every check
 * runs (target chance 0). The search area is a cube around the NPC, not the flat box of the vanilla goal, and a dead attack target is cleared.
 *
 * <p>The vanilla suitability test ({@code TargetingConditions}, which includes the home restriction of the mob) is replaced by
 * {@link AIHelper#isTarget}, as the 1.12.2 class replaced {@code isSuitableTarget}. The class stays a {@link NearestAttackableTargetGoal}
 * of {@code LivingEntity}, which {@code NpcEventHandler} relies on for the hostile AI check (it reads {@code targetType}).
 */
public class NpcAIAttackNearest extends NearestAttackableTargetGoal<LivingEntity> {
    @Nullable
    private final Predicate<? super LivingEntity> targetSelector;

    public NpcAIAttackNearest(NpcBase npc, @Nullable Predicate<? super LivingEntity> targetSelector) {
        super(npc, LivingEntity.class, 0, true, false, null);
        this.targetSelector = targetSelector;
    }

    @Override
    public boolean canUse() {
        boolean ret = super.canUse();
        LivingEntity attackTarget = mob.getTarget();
        if (!ret && attackTarget != null && !attackTarget.isAlive()) {
            mob.setTarget(null);
        }
        return ret;
    }

    /** An archer or a crew of a siege weapon or ship gun that is busy with a nearer target looks for a dragon once a second (Ice and Fire integration) and switches to it. */
    @Override
    public boolean canContinueToUse() {
        if (ModCompat.MONSTERS.isActive() && mob.tickCount % 20 == 0 && SettlementConfig.iceAndFireRangedPrefersDragons
                && MonsterRules.fightsFromDistance((NpcBase) mob) && !MonsterRules.isMajor(mob.getTarget())) {
            findTarget();
            if (target != null && MonsterRules.isMajor(target)) {
                mob.setTarget(target);
            }
        }
        return super.canContinueToUse();
    }

    @Override
    protected boolean canAttack(@Nullable LivingEntity potentialTarget, TargetingConditions targetPredicate) {
        return AIHelper.isTarget((NpcBase) mob, potentialTarget, mustSee);
    }

    @Override
    protected AABB getTargetSearchArea(double targetDistance) {
        return mob.getBoundingBox().inflate(targetDistance, targetDistance, targetDistance);
    }

    /**
     * 1.12.2: the entities of the cube that the selector accepts and that are within the follow range (80 % if sneaking, scaled by the armor
     * cover if invisible), sorted by distance; the first one that is a suitable target.
     */
    @Override
    protected void findTarget() {
        // Spectre Factions: the guard mode of the actor. PASSIVE and DEFEND do not attack on sight, GUARD only inside the guard area.
        GuardMode mode = ((NpcBase) mob).getGuardMode();
        if (mode == GuardMode.PASSIVE || mode == GuardMode.DEFEND) {
            target = null;
            return;
        }
        double followDistance = getFollowDistance();
        LivingEntity nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        LivingEntity dragon = null; // archers and crews of siege weapons and ship guns take a dragon first (Ice and Fire integration)
        double dragonDistSq = Double.MAX_VALUE;
        for (LivingEntity candidate : mob.level().getEntitiesOfClass(LivingEntity.class, getTargetSearchArea(followDistance), EntitySelector.NO_SPECTATORS)) {
            if (targetSelector != null && !targetSelector.test(candidate)) {
                continue;
            }
            if (mode == GuardMode.GUARD && !((NpcBase) mob).isWithinGuardArea(candidate)) {
                continue;
            }
            double maxDistance = followDistance;
            if (candidate.isShiftKeyDown()) {
                maxDistance *= 0.8D;
            }
            if (candidate.isInvisible()) {
                float cover = candidate.getArmorCoverPercentage();
                if (cover < 0.1F) {
                    cover = 0.1F;
                }
                maxDistance *= 0.7F * cover;
            }
            double distSq = mob.distanceToSqr(candidate);
            boolean preferred = MonsterRules.rangedPrefers((NpcBase) mob, candidate) && distSq < dragonDistSq;
            if (distSq > maxDistance * maxDistance || distSq >= nearestDistSq && !preferred) {
                continue;
            }
            if (canAttack(candidate, targetConditions)) {
                if (distSq < nearestDistSq) {
                    nearest = candidate;
                    nearestDistSq = distSq;
                }
                if (preferred) {
                    dragon = candidate;
                    dragonDistSq = distSq;
                }
            }
        }
        target = dragon != null ? dragon : nearest;
    }
}
