/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/NpcAIAttack.
 */
package io.github.capttracker.ancientwarfarenpc.ai;

import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import javax.annotation.Nullable;

/**
 * Template for the melee and the ranged attack goals. It starts when the attack target (or, if there is none, the revenge target) is within an
 * "adjusted" range: {@code max(follow range / (distance to home / 20), 3)}, or divided by the distance to the followed entity when the NPC follows
 * someone. Each tick it looks at the target and either closes in ({@link #shouldCloseOnTarget}) or clears the path and calls {@link #doAttack}
 * with a countdown {@link #getAttackDelay()}.
 */
public abstract class NpcAIAttack<T extends NpcBase> extends NpcAI<T> {
    @Nullable
    private LivingEntity target;
    private int attackDelay = 0;

    public NpcAIAttack(T npc) {
        super(npc);
    }

    @Override
    public boolean canUse() {
        return super.canUse() && hasTargetInRange();
    }

    private boolean hasTargetInRange() {
        LivingEntity attackTarget = getAttackTarget();
        if (attackTarget == null) {
            return false;
        }
        if (target == null) {
            return attackTarget.isAlive() && isTargetInRange(attackTarget);
        }
        return target.isAlive() && attackTarget.equals(target) && isTargetInRange(attackTarget);
    }

    private boolean isTargetInRange(LivingEntity attackTarget) {
        return npc.distanceTo(attackTarget) < getAdjustedTargetDistance();
    }

    @Nullable
    private LivingEntity getAttackTarget() {
        LivingEntity attackTarget = npc.getTarget();
        return attackTarget != null ? attackTarget : npc.getLastHurtByMob();
    }

    @Override
    public final void start() {
        target = getAttackTarget();
        moveRetryDelay = 0;
        npc.addAITask(TASK_ATTACK);
        if (target != null) {
            npc.think("fight", target.getName());
        }
        npc.setSwingingArms(true);
        attackDelay = 0;
    }

    @Override
    public void stop() {
        target = null;
        moveRetryDelay = 0;
        npc.removeAITask(TASK_MOVE + TASK_ATTACK);
        npc.setSwingingArms(false);
    }

    protected int getCoolDown() {
        return attackDelay;
    }

    @Override
    public final void tick() {
        LivingEntity currentTarget = target;
        if (currentTarget == null) {
            return;
        }
        npc.getLookControl().setLookAt(currentTarget, 30.f, 30.f);
        double distanceToEntitySq = npc.distanceToSqr(currentTarget.getX(), currentTarget.getBoundingBox().minY, currentTarget.getZ());
        if (shouldCloseOnTarget(distanceToEntitySq)) {
            npc.addAITask(TASK_MOVE);
            moveToEntity(currentTarget, distanceToEntitySq);
        } else {
            npc.getNavigation().stop();
            attackDelay--;
            doAttack(distanceToEntitySq);
        }
    }

    private double getAdjustedTargetDistance() {
        LivingEntity followed = npc.getFollowingEntity();
        if (followed != null) {
            return Math.max(getTargetDistance() / Math.sqrt(distanceSq(followed.blockPosition())), 3D);
        }
        return Math.max(getTargetDistance() / (getHomeDistance() / 20), 3D);
    }

    private double getHomeDistance() {
        return Math.sqrt(distanceSq(npc.getHomePosition()));
    }

    private double getTargetDistance() {
        AttributeInstance followRange = npc.getAttribute(Attributes.FOLLOW_RANGE);
        return followRange == null ? 35.0D : followRange.getValue();
    }

    protected abstract boolean shouldCloseOnTarget(double distanceToEntity);

    protected abstract void doAttack(double distanceToEntity);

    /** The entity this goal is attacking (set when the goal starts). */
    @Nullable
    public final LivingEntity getTarget() {
        return target;
    }

    public final void setAttackDelay(int value) {
        attackDelay = value;
    }

    public final int getAttackDelay() {
        return attackDelay;
    }
}
