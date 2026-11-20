/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/NpcAIHurt.
 */
package io.github.capttracker.ancientwarfarenpc.ai;

import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * Target goal: whoever hurt the NPC last, except players and player-owned NPCs (the owned NPCs never retaliate against them), and calls the
 * NPCs of the same kind for help (1.12.2: {@code entityCallsForHelp} true).
 */
public class NpcAIHurt extends HurtByTargetGoal {
    public NpcAIHurt(NpcBase npc) {
        super(npc);
        setAlertOthers();
    }

    @Override
    protected boolean canAttack(@Nullable LivingEntity potentialTarget, TargetingConditions targetPredicate) {
        return !(potentialTarget instanceof Player || potentialTarget instanceof NpcPlayerOwned)
                && AIHelper.isTarget((NpcBase) mob, potentialTarget, mustSee);
    }
}
