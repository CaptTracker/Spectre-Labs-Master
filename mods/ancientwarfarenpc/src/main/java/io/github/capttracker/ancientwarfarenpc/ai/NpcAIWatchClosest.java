/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/NpcAIWatchClosest.
 */
package io.github.capttracker.ancientwarfarenpc.ai;

import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;

/** The vanilla look-at goal with one change: it always prefers to watch the attack target. */
public class NpcAIWatchClosest extends LookAtPlayerGoal {
    public NpcAIWatchClosest(NpcBase npc, Class<? extends LivingEntity> watchTargetClass, float maxDistance) {
        super(npc, watchTargetClass, maxDistance);
    }

    @Override
    public boolean canUse() {
        //just a minor modification from vanilla that will always prefer to watch attacked target
        lookAt = mob.getTarget();
        return lookAt != null || super.canUse();
    }
}
