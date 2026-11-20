/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/NpcAIWander.
 */
package io.github.capttracker.ancientwarfarenpc.ai;

import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * A chance per check to walk to a random point (the NPC's walk target values and home restriction apply, see {@code DefaultRandomPos}); speed 0.625.
 *
 * <p>The chance is 1 in 180 per check: the 1.12.2 goal was checked every third tick with a chance of 1 in 120, this one is checked every second tick,
 * so the NPC wanders as often per second as before.
 */
public class NpcAIWander extends NpcAI<NpcBase> {
    private static final int CHANCE = 180;

    @Nullable
    private Vec3 vec3;

    public NpcAIWander(NpcBase npc) {
        this(npc, 0.625);
    }

    public NpcAIWander(NpcBase npc, double par2) {
        super(npc);
        moveSpeed = par2;
        setMutexBits(MOVE);
    }

    @Override
    public boolean canUse() {
        if (!super.canUse() || npc.shouldBeAtHome() || npc.getRandom().nextInt(CHANCE) != 0 || !npc.getNavigation().isDone()) {
            return false;
        } else {
            vec3 = DefaultRandomPos.getPos(npc, MIN_RANGE, 7);
            return vec3 != null;
        }
    }

    @Override
    public void start() {
        npc.addAITask(NpcAI.TASK_WANDER + NpcAI.TASK_MOVE);
        if (vec3 != null) {
            setPath(vec3.x, vec3.y, vec3.z);
        }
    }

    @Override
    public void stop() {
        npc.removeAITask(NpcAI.TASK_WANDER + NpcAI.TASK_MOVE);
    }
}
