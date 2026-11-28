/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (third round, mod integrations): soldiers with melee weapons stay out of the breath range of a dragon (Ice and Fire).
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfarenpc.ai.MonsterRules;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.entity.NpcCombat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A soldier that fights in melee, is not brave and has no orders ({@link MonsterRules#isKeepAwayType}) does not close in on a dragon: while one is within
 * {@code melee_keep_away_distance} blocks it walks away until it is out of the reach of the breath again, and it drops a dragon as its attack target. The archers
 * and the crews of the siege weapons and ship guns are left alone (they shoot it), and so are the brave ones. Does nothing without Ice and Fire.
 */
public class NpcAIKeepClearOfDragons extends NpcAI<NpcCombat> {
    private static final int SCAN_INTERVAL = 5; // checks, one every second tick
    /** The NPC runs until it is this many blocks beyond the keep away distance. */
    private static final double EXTRA = 8.0D;

    @Nullable
    private LivingEntity dragon;
    @Nullable
    private Vec3 flee;
    private int repath;
    private int scanDelay;

    public NpcAIKeepClearOfDragons(NpcCombat npc) {
        super(npc);
        setMutexBits(MOVE);
    }

    @Nullable
    private LivingEntity nearestDragon(double range) {
        List<LivingEntity> near = npc.level().getEntitiesOfClass(LivingEntity.class, npc.getBoundingBox().inflate(range),
                e -> e != npc && e.isAlive() && MonsterRules.isMajor(e) && npc.isHostileTowards(e));
        LivingEntity best = null;
        for (LivingEntity e : near) {
            if (best == null || npc.distanceToSqr(e) < npc.distanceToSqr(best)) {
                best = e;
            }
        }
        return best != null && npc.distanceToSqr(best) <= range * range ? best : null;
    }

    @Override
    public boolean canUse() {
        if (!super.canUse() || npc.isPassenger() || !MonsterRules.isKeepAwayType(npc) || ++scanDelay < SCAN_INTERVAL) {
            return false;
        }
        scanDelay = 0;
        dragon = nearestDragon(MonsterRules.keepAwayDistance());
        return dragon != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (!super.canUse() || !MonsterRules.isKeepAwayType(npc) || dragon == null || !dragon.isAlive()) {
            return false;
        }
        return npc.distanceToSqr(dragon) <= square(MonsterRules.keepAwayDistance() + EXTRA);
    }

    private static double square(double d) {
        return d * d;
    }

    @Override
    public void start() {
        npc.addAITask(TASK_FLEE);
        repath = 0;
        flee = null;
        if (dragon != null) {
            npc.think("keep_clear", dragon.getName());
        }
    }

    @Override
    public void tick() {
        if (dragon == null) {
            return;
        }
        LivingEntity target = npc.getTarget();
        if (target != null && MonsterRules.isMajor(target)) {
            npc.setTarget(null);
        }
        if (repath-- <= 0 || flee == null) {
            repath = 20;
            flee = DefaultRandomPos.getPosAway(npc, 16, 7, dragon.position());
        }
        if (flee != null) {
            npc.addAITask(TASK_MOVE);
            moveToPosition(flee.x, flee.y, flee.z, npc.distanceToSqr(flee.x, flee.y, flee.z));
        }
    }

    @Override
    public void stop() {
        moveRetryDelay = 0;
        dragon = null;
        flee = null;
        npc.getNavigation().stop();
        npc.removeAITask(TASK_FLEE + TASK_MOVE);
    }
}
