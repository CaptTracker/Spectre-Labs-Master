/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/NpcAIBlockWithShield.
 */
package io.github.capttracker.ancientwarfarenpc.ai;

import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.ItemAbilities;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * Shield blocking. The goal runs if the NPC holds a shield in the off hand that is not on cooldown and a target is swinging within 4 blocks, or holds a
 * drawn bow (any bow user, any distance), or the NPC has just been hit ({@link #onPreDamage}, called from {@code NpcBase.hurt}). It waits a random 0 to 5
 * ticks (0 to 30 for bows), raises the shield, clears the path, stops the NPC's ATTACK goals through the stop flag and walks at half speed towards the
 * attacker if that is not closer than 4.5 blocks. It keeps the shield up for 40 ticks after the threat ended ("withdraw delay").
 *
 * <h2>Changes from 1.12.2</h2>
 * <ul>
 *   <li>1.12.2 raised the shield with {@code setActiveHand} and set the hand's use count to 40 with an access transformer at every tick, which made
 *       the shield block at once and lower itself 40 ticks after the last refresh. Here the shield is raised with {@code startUsingItem} and lowered
 *       in {@link #stop()}; the 5 tick delay of the vanilla blocking check is removed by {@link NpcShieldEvents}.</li>
 *   <li>One instance is created by the NPC and removed and added again when the off-hand item changes (the 1.12.2 NPC added a new one at every change).</li>
 * </ul>
 */
public class NpcAIBlockWithShield extends NpcAI<NpcBase> {
    @Nullable
    private LivingEntity target;
    private static final float SAFE_MELEE_DISTANCE = 4.5F;
    private final int maxReactionDelay = 5; //TODO(original) turn into setting
    private final int maxReactionDelayBow = 30; //TODO(original) turn into setting
    private int reactionDelayTicks = 0;
    private static final int SHIELD_WITHDRAW_DELAY = 40;
    private int shieldWithdrawTicks = 0;

    public NpcAIBlockWithShield(NpcBase npc) {
        super(npc);
    }

    @Override
    public boolean canUse() {
        return super.canUse() && canExecute(e -> e.isAlive() && shouldDefendFrom(e));
    }

    private boolean canExecute(Predicate<LivingEntity> defendFrom) {
        LivingEntity attackTarget = getAttackTarget();
        if (target == null) {
            return hasShieldInOffhand() && npc.getShieldDisabledTick() <= 0
                    && (shieldWithdrawTicks > 0 || attackTarget != null && defendFrom.test(attackTarget));
        }
        return hasShieldInOffhand() && npc.getShieldDisabledTick() <= 0 && target.isAlive() && attackTarget != null
                && (shieldWithdrawTicks > 0 || attackTarget.equals(target) && shouldDefendFrom(target));
    }

    private boolean shouldDefendFrom(@Nullable LivingEntity e) {
        return e != null && isPlayerOrTargetsThisNpc(e) && ((e.swinging && npc.distanceTo(e) < 4) || isAimingWithBow(e));
    }

    private boolean isPlayerOrTargetsThisNpc(LivingEntity e) {
        if (e instanceof Player) {
            return true;
        }
        if (e instanceof Mob living) {
            return living.getTarget() != null && living.getTarget().equals(npc);
        }
        return false;
    }

    @Override
    public void start() {
        if (!npc.isUsingItem()) {
            init();
        }
    }

    private void init() {
        target = getAttackTarget();
        reactionDelayTicks = npc.getRandom().nextInt(target != null && isAimingWithBow(target) ? maxReactionDelayBow : maxReactionDelay);
        shieldWithdrawTicks = SHIELD_WITHDRAW_DELAY;
    }

    @Override
    public void stop() {
        target = null;
        reactionDelayTicks = 0;
        npc.startAIControlFlag(ATTACK);
        if (npc.isUsingItem() && npc.getUsedItemHand() == InteractionHand.OFF_HAND) {
            npc.stopUsingItem();
        }
    }

    @Override
    public void tick() {
        if (reactionDelayTicks > 0) {
            reactionDelayTicks--;
            return;
        }
        if (!shouldDefendFrom(target)) {
            shieldWithdrawTicks--;
            return;
        }
        npc.stopAIControlFlag(ATTACK);
        npc.startUsingItem(InteractionHand.OFF_HAND);
        npc.getNavigation().stop();
        if (target != null) {
            npc.getLookControl().setLookAt(target, 30.f, 30.f);
            double distanceToEntity = npc.distanceToSqr(target.getX(), target.getBoundingBox().minY, target.getZ());
            if (!shouldCloseOnTarget(distanceToEntity) || isAimingWithBow(target)) {
                npc.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), npc.getDefaultMoveSpeed() * 0.5f);
            }
        }
    }

    @Nullable
    private LivingEntity getAttackTarget() {
        LivingEntity attackTarget = npc.getTarget();
        return attackTarget != null ? attackTarget : npc.getLastHurtByMob();
    }

    protected boolean shouldCloseOnTarget(double distanceToEntity) {
        if (target == null) {
            return false;
        }
        double attackDistance = (npc.getBbWidth() / 2D) + (target.getBbWidth() / 2D) + SAFE_MELEE_DISTANCE;
        return (distanceToEntity > (attackDistance * attackDistance)) || !npc.getSensing().hasLineOfSight(target);
    }

    @Nullable
    public final LivingEntity getTarget() {
        return target;
    }

    private boolean hasShieldInOffhand() {
        return npc.getOffhandItem().canPerformAction(ItemAbilities.SHIELD_BLOCK);
    }

    private boolean isAimingWithBow(LivingEntity entity) {
        return (npc.isBow(entity.getMainHandItem().getItem()) && entity.isUsingItem() && entity.getUsedItemHand() == InteractionHand.MAIN_HAND)
                || (npc.isBow(entity.getOffhandItem().getItem()) && entity.isUsingItem() && entity.getUsedItemHand() == InteractionHand.OFF_HAND);
    }

    /** Called before the NPC takes a blow: if the blow can be blocked, the shield goes up at once. */
    public void onPreDamage(DamageSource source, float damage) {
        if (damage > 0 && !source.is(DamageTypeTags.BYPASSES_SHIELD) && canExecute(LivingEntity::isAlive)) {
            init();
            tick();
        }
    }
}
