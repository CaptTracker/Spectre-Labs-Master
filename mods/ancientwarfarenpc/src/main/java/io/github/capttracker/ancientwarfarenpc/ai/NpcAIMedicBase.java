/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/NpcAIMedicBase.
 */
package io.github.capttracker.ancientwarfarenpc.ai;

import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Heals the nearest injured entity that the NPC is not hostile towards (NPCs and players first) by 0.5 health every 20 ticks and looks for one every
 * 20 ticks. A plain {@code heal()} call, not a potion. Only for the subtype "medic" ({@link #isProperSubtype()}).
 *
 * <p>Original bug fixed: the 1.12.2 search box was {@code AxisAlignedBB.expand(range, range / 2, range)}, which only extends the box towards positive x, y
 * and z; the box is grown on every side now.
 */
public class NpcAIMedicBase<T extends NpcBase> extends NpcAI<T> {
    private static final int HEAL_DELAY_MAX = 20;
    private static final int INJURED_RECHECK_DELAY_MAX = 20;
    private static final float AMOUNT_TO_HEAL_EACH_TRY = 0.5f;

    private int injuredRecheckDelay = 0;
    private int healDelay = 0;
    @Nullable
    private LivingEntity targetToHeal = null;
    private final Comparator<LivingEntity> sorter;
    private final Predicate<LivingEntity> selector;

    public NpcAIMedicBase(T npc) {
        super(npc);
        sorter = Comparator.comparingDouble(e -> npc.distanceToSqr(e));
        selector = entity -> entity != null && entity.isAlive() && entity.getHealth() < entity.getMaxHealth() && !NpcAIMedicBase.this.npc.isHostileTowards(entity);
        setMutexBits(MOVE | ATTACK);
    }

    @Override
    public boolean canUse() {
        if (!super.canUse()) {
            return false;
        }
        if (!isProperSubtype()) {
            return false;
        }
        if (validateTarget()) {
            return true;
        }
        if (injuredRecheckDelay-- > 0) {
            return false;
        }
        injuredRecheckDelay = INJURED_RECHECK_DELAY_MAX;
        double dist = npc.getAttributeValue(Attributes.FOLLOW_RANGE);
        AABB bb = npc.getBoundingBox().inflate(dist, dist / 2, dist);
        List<LivingEntity> potentialTargets = npc.level().getEntitiesOfClass(LivingEntity.class, bb, selector);
        if (potentialTargets.isEmpty()) {
            return false;
        }
        potentialTargets.sort(sorter);
        List<LivingEntity> sub = potentialTargets.stream().filter(input -> input instanceof NpcBase || input instanceof Player).collect(Collectors.toList());
        for (LivingEntity base : sub) {
            targetToHeal = base;
            if (validateTarget()) {
                return true;
            }
        }
        targetToHeal = potentialTargets.get(0);
        if (!validateTarget()) {
            targetToHeal = null;
            return false;
        }
        return true;
    }

    private boolean validateTarget() {
        return targetToHeal != null && targetToHeal.isAlive() && targetToHeal.getHealth() < targetToHeal.getMaxHealth();
    }

    protected boolean isProperSubtype() {
        return "medic".equals(npc.getNpcSubType());
    }

    @Override
    public void start() {
        npc.addAITask(TASK_GUARD);
    }

    @Override
    public void tick() {
        LivingEntity patient = targetToHeal;
        if (patient == null) {
            return;
        }
        double dist = npc.distanceToSqr(patient);
        double attackDistance = (npc.getBbWidth() * npc.getBbWidth() * 2.0F * 2.0F) + (patient.getBbWidth() * patient.getBbWidth() * 2.0F * 2.0F);
        if (dist > attackDistance) {
            npc.addAITask(TASK_MOVE);
            moveToEntity(patient, dist);
            healDelay = HEAL_DELAY_MAX;
        } else {
            npc.removeAITask(TASK_MOVE);
            healDelay--;
            npc.workSwing(); // the medic works on the patient all the while
            if (healDelay < 0) {
                healDelay = HEAL_DELAY_MAX;
                if (patient.getHealth() < patient.getMaxHealth()) {
                    npc.gainSkillXp(io.github.capttracker.ancientwarfarenpc.skill.Skill.MEDICINE, io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig.skillXpHeal);
                }
                patient.heal(getAmountToHealEachTry());
            }
        }
    }

    protected float getAmountToHealEachTry() {
        return AMOUNT_TO_HEAL_EACH_TRY * (float) io.github.capttracker.ancientwarfarenpc.skill.SkillEffects.healFactor(npc.getSkillRank(io.github.capttracker.ancientwarfarenpc.skill.Skill.MEDICINE));
    }

    @Override
    public void stop() {
        npc.removeAITask(TASK_MOVE + TASK_GUARD);
        targetToHeal = null;
    }
}
