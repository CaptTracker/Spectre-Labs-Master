/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 7): the brave trait lets a worker fight.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfarenpc.ai.AIHelper;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIAttackMeleeLongRange;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.trait.Trait;
import io.github.capttracker.ancientwarfarenpc.trait.TraitEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.List;

/**
 * A brave worker with a sword or an axe in its hand does not run from hostiles: it picks the nearest one that it sees (within 12 blocks, 20 while the
 * alarm of its settlement is on) as its attack target and fights it with the melee attack of the soldiers. It never starts to fight without the weapon, and
 * stops when the target is gone.
 */
public class NpcAIBraveFight extends NpcAIAttackMeleeLongRange {
    private static final double RANGE = 12.0D;
    private static final double ALARM_RANGE = 20.0D;
    private static final int SCAN_INTERVAL = 10;

    private final NpcPlayerOwned owned;

    public NpcAIBraveFight(NpcPlayerOwned npc) {
        super(npc);
        this.owned = npc;
    }

    private boolean eligible() {
        return owned.hasTrait(Trait.BRAVE) && TraitEffects.isArmed(owned) && !owned.isPassenger();
    }

    @Override
    public boolean canUse() {
        if (!eligible()) {
            return false;
        }
        LivingEntity current = owned.getTarget();
        if (current != null && !current.isAlive()) {
            owned.setTarget(null);
            current = null;
        }
        if (current == null && owned.tickCount % SCAN_INTERVAL == 0) {
            double range = owned.isAlarmed ? ALARM_RANGE : RANGE;
            List<Mob> hostiles = owned.level().getEntitiesOfClass(Mob.class, owned.getBoundingBox().inflate(range, 3.0D, range),
                    mob -> mob != owned && mob.isAlive() && owned.isHostileTowards(mob) && AIHelper.isTarget(owned, mob, true));
            Mob nearest = null;
            for (Mob mob : hostiles) {
                if (nearest == null || owned.distanceToSqr(mob) < owned.distanceToSqr(nearest)) {
                    nearest = mob;
                }
            }
            if (nearest != null) {
                owned.setTarget(nearest);
            }
        }
        return super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return eligible() && super.canContinueToUse();
    }
}
