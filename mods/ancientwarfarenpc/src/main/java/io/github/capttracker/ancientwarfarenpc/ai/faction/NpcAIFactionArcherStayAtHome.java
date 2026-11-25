/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/faction/NpcAIFactionArcherStayAtHome.
 */
package io.github.capttracker.ancientwarfarenpc.ai.faction;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import net.minecraft.world.entity.LivingEntity;

/** Archers and spell casters walk home when they have no target. */
public class NpcAIFactionArcherStayAtHome extends NpcAI<NpcBase> {
    public NpcAIFactionArcherStayAtHome(NpcBase npc) {
        super(npc);
        setMutexBits(ATTACK + MOVE);
    }

    @Override
    public boolean canUse() {
        LivingEntity target = npc.getTarget();
        return super.canUse() && (target == null || !target.isAlive() && npc.hasHome()) && !isAtHome();
    }

    private boolean isAtHome() {
        return npc.getDistanceSqFromHome() <= MIN_RANGE;
    }

    @Override
    public void start() {
        npc.addAITask(TASK_MOVE);
    }

    @Override
    public void tick() {
        moveToPosition(npc.getHomePosition(), npc.getDistanceSqFromHome());
    }

    @Override
    public void stop() {
        npc.removeAITask(TASK_MOVE);
    }
}
