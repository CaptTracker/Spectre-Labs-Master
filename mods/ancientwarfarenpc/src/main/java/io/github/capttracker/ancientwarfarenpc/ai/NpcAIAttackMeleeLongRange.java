/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/NpcAIAttackMeleeLongRange.
 */
package io.github.capttracker.ancientwarfarenpc.ai;

import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.item.IExtendedReachWeapon;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * Melee attack. The reach is 2.5 by default, or the reach of the held weapon minus 1, plus both half widths. It needs line of sight. The attack
 * is {@code swing} and {@code doHurtTarget}, repeated every {@code 1 / attack speed * 20} ticks, and gives the NPC attack experience.
 *
 * <h2>Weapon reach</h2>
 * As in 1.12.2 the reach of an {@link IExtendedReachWeapon} is its {@code getReach()} minus 1. Any other weapon that carries an entity interaction range
 * attribute modifier gives 3.0 (the base range of a player) plus the added value, minus 1.
 */
public class NpcAIAttackMeleeLongRange extends NpcAIAttack<NpcBase> {
    /** The entity interaction range of a player without modifiers. */
    public static final float PLAYER_BASE_REACH = 3.0F;
    private static final float DEFAULT_ATTACK_REACH = 2.5F;

    private float attackReach = DEFAULT_ATTACK_REACH;

    public NpcAIAttackMeleeLongRange(NpcBase npc) {
        super(npc);
        setMutexBits(ATTACK + MOVE);
    }

    public void setAttackReachFromWeapon(ItemStack weapon) {
        attackReach = getWeaponReach(weapon);
    }

    private static float getWeaponReach(ItemStack weapon) {
        if (weapon.isEmpty()) {
            return DEFAULT_ATTACK_REACH;
        }
        if (weapon.getItem() instanceof IExtendedReachWeapon reachWeapon) {
            return reachWeapon.getReach() - 1F;
        }
        double[] added = {0D};
        weapon.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ENTITY_INTERACTION_RANGE) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                added[0] += modifier.amount();
            }
        });
        if (added[0] != 0D) {
            return (float) (PLAYER_BASE_REACH + added[0]) - 1F;
        }
        return DEFAULT_ATTACK_REACH;
    }

    @Override
    protected boolean shouldCloseOnTarget(double distanceToEntity) {
        LivingEntity target = getTarget();
        if (target == null) {
            return false;
        }
        double attackDistance = (npc.getBbWidth() / 2D) + (target.getBbWidth() / 2D) + attackReach;
        return (distanceToEntity > (attackDistance * attackDistance)) || !npc.getSensing().hasLineOfSight(target);
    }

    @Override
    protected void doAttack(double distanceToEntity) {
        npc.removeAITask(TASK_MOVE);
        LivingEntity target = getTarget();
        if (target != null && getAttackDelay() <= 0) {
            npc.swing(InteractionHand.MAIN_HAND);
            npc.doHurtTarget(target);
            setAttackDelay(getCoolDown());
            npc.addExperience(AWNPCStatics.npcXpFromAttack);
        }
    }

    @Override
    protected int getCoolDown() {
        AttributeInstance attackSpeed = npc.getAttribute(Attributes.ATTACK_SPEED);
        int entityAttackCooldown = attackSpeed == null ? 0 : (int) (1.0D / attackSpeed.getValue() * 20.0D);
        return entityAttackCooldown > 0 ? entityAttackCooldown : 20;
    }
}
