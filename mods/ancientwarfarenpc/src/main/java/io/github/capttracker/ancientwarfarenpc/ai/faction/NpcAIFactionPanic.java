/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/faction/NpcAIFactionPanic.
 */
package io.github.capttracker.ancientwarfarenpc.ai.faction;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

/**
 * Panic that flees away from the revenge target (the last attacker). Civilians only. Like {@code EntityAIPanic} of 1.12.2 it starts while the NPC has a
 * revenge target or burns, not on the damage types of the vanilla 1.21 panic tag.
 */
public class NpcAIFactionPanic extends PanicGoal {
    public NpcAIFactionPanic(PathfinderMob creature, double speedIn) {
        super(creature, speedIn);
    }

    @Override
    protected boolean shouldPanic() {
        return mob.getLastHurtByMob() != null || mob.isOnFire();
    }

    @Override
    protected boolean findRandomPosition() {
        Vec3 vec3d;
        if (mob.getLastHurtByMob() != null) {
            vec3d = DefaultRandomPos.getPosAway(mob, 10, 4, mob.getLastHurtByMob().position());
        } else {
            vec3d = DefaultRandomPos.getPos(mob, 5, 4);
        }
        if (vec3d == null) {
            return false;
        } else {
            posX = vec3d.x;
            posY = vec3d.y;
            posZ = vec3d.z;
            return true;
        }
    }
}
