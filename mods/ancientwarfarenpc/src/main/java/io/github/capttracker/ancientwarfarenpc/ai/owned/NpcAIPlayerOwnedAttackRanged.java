/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/owned/NpcAIPlayerOwnedAttackRanged.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAIAttack;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.RangedAttackMob;

/**
 * Archer of a player: the attack distance is the squared {@code archer_attack_range} of the config (default 60), it shoots every 35 ticks through
 * {@code RangedAttackMob.performRangedAttack} with a power of {@code range^2 / distance^2} clamped to 0.1 to 1, and honours {@code doNotPursue}.
 * The range is read from the config when it is used (1.12.2: when the class was loaded).
 */
public class NpcAIPlayerOwnedAttackRanged extends NpcAIAttack<NpcBase> {
    private final RangedAttackMob rangedAttacker;

    public NpcAIPlayerOwnedAttackRanged(NpcBase npc) {
        super(npc);
        rangedAttacker = (RangedAttackMob) npc;//will classcastexception if improperly used..
        moveSpeed = 1.d;
        setMutexBits(ATTACK + MOVE);
    }

    private static double getAttackDistance() {
        return AWNPCStatics.archerRange * AWNPCStatics.archerRange;
    }

    @Override
    protected boolean shouldCloseOnTarget(double dist) {
        if (npc.doNotPursue) {
            return (dist < 0);
        }
        LivingEntity target = getTarget();
        return (dist > getAttackDistance() || target == null || !npc.getSensing().hasLineOfSight(target));
    }

    @Override
    protected void doAttack(double dist) {
        npc.removeAITask(TASK_MOVE);
        npc.getNavigation().stop();
        LivingEntity target = getTarget();
        if (target != null && getAttackDelay() <= 0) {
            float pwr = (float) (getAttackDistance() / dist);
            pwr = Mth.clamp(pwr, 0.1f, 1f);
            rangedAttacker.performRangedAttack(target, pwr);
            setAttackDelay(35);
        }
    }
}
