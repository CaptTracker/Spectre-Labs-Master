/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/faction/NpcAIFactionRangedAttack.
 */
package io.github.capttracker.ancientwarfarenpc.ai.faction;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAIAttack;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.ItemStack;

/**
 * Ranged attack of the faction archers and priests: the attack distance is the squared follow range (or the given value), the delay between two shots 45 ticks
 * (or the given value), the power is {@code distance^2 / dist^2} clamped to 0.1 to 1. The bow is drawn with {@code startUsingItem} and released before the shot.
 * The priest uses (0.8, 20, 80): speed, attack distance squared, delay.
 *
 * <h2>Changes from 1.12.2</h2>
 * <ul>
 *   <li>The default attack distance follows the follow range when it is used (the goal is created before the NPC defaults set the range); a value of
 *       -1 or less means that.</li>
 *   <li>If the main hand cannot be drawn (empty, or an item without use time) the NPC shoots when the delay is over; the 1.12.2 goal waited for an
 *       active hand that never came (the priest's potion throw).</li>
 *   <li>The draw is stopped when the goal stops (1.12.2 left the bow drawn, and the NPC slowed down, until the next shot).</li>
 * </ul>
 */
public class NpcAIFactionRangedAttack extends NpcAIAttack<NpcBase> {
    private final RangedAttackMob rangedAttacker;
    private int attackDistanceSq;
    private final int rangedAttackDelay;

    public <T extends NpcBase & RangedAttackMob> NpcAIFactionRangedAttack(T npc, double moveSpeed, int attackDistanceSq, int attackDelay) {
        super(npc);
        rangedAttacker = npc;
        this.moveSpeed = moveSpeed;
        this.attackDistanceSq = attackDistanceSq;
        this.rangedAttackDelay = attackDelay;
    }

    public <T extends NpcBase & RangedAttackMob> NpcAIFactionRangedAttack(T npc) {
        this(npc, 1, -1, 45);
    }

    @Override
    protected boolean shouldCloseOnTarget(double dist) {
        LivingEntity target = getTarget();
        return (dist > getAttackDistanceSq() || target == null || !npc.getSensing().hasLineOfSight(target));
    }

    private double getAttackDistanceSq() {
        if (attackDistanceSq <= -1) {
            double distance = npc.getAttributeValue(Attributes.FOLLOW_RANGE);
            return (int) (distance * distance);
        }
        return attackDistanceSq;
    }

    @Override
    protected void doAttack(double dist) {
        LivingEntity target = getTarget();
        if (target == null) {
            return;
        }
        ItemStack mainHand = npc.getMainHandItem();
        boolean canDraw = !mainHand.isEmpty() && mainHand.getUseDuration(npc) > 0;
        if (canDraw && !npc.isUsingItem()) {
            npc.startUsingItem(InteractionHand.MAIN_HAND);
        } else if (getAttackDelay() <= 0) {
            if (npc.isUsingItem()) {
                npc.stopUsingItem();
            }
            float pwr = (float) (getAttackDistanceSq() / dist);
            pwr = Math.min(Math.max(pwr, 0.1F), 1F);
            rangedAttacker.performRangedAttack(target, pwr);
            setAttackDelay(rangedAttackDelay);
        }
    }

    @Override
    public void stop() {
        super.stop();
        if (npc.isUsingItem() && npc.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            npc.stopUsingItem();
        }
    }
}
