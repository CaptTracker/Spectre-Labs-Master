/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/NpcAIAttackNearest.
 */
package io.github.capttracker.ancientwarfarenpc.ai;

import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
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
            if (distSq > maxDistance * maxDistance || distSq >= nearestDistSq) {
                continue;
            }
            if (canAttack(candidate, targetConditions)) {
                nearest = candidate;
                nearestDistSq = distSq;
            }
        }
        target = nearest;
    }
}
