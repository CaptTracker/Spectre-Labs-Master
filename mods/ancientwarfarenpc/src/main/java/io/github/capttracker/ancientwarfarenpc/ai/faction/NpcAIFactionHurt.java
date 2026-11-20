/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/faction/NpcAIFactionHurt.
 */
package io.github.capttracker.ancientwarfarenpc.ai.faction;

import io.github.capttracker.ancientwarfarenpc.ai.AIHelper;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFaction;
import io.github.capttracker.spectrefactions.api.GuardMode;
import io.github.capttracker.spectrefactions.api.Relation;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Target goal: whoever hurt the faction NPC last, if the caller's predicate accepts it ({@code isHostileTowards}); calls the NPCs of the same kind for help.
 *
 * <p>Spectre Factions: the call for help also reaches every other NPC of the same actor and of FRIENDLY or ALLIED actors that have no target.
 */
public class NpcAIFactionHurt extends HurtByTargetGoal {
    private final NpcFaction npc;
    private final Predicate<? super LivingEntity> targetSelector;

    public NpcAIFactionHurt(NpcFaction npc, @Nullable Predicate<? super LivingEntity> targetSelector) {
        super(npc);
        this.npc = npc;
        setAlertOthers();
        if (targetSelector == null) {
            this.targetSelector = entity -> true;
        } else {
            this.targetSelector = targetSelector;
        }
    }

    @Override
    protected boolean canAttack(@Nullable LivingEntity potentialTarget, TargetingConditions targetPredicate) {
        return potentialTarget != null && targetSelector.test(potentialTarget);
    }

    @Override
    protected void alertOthers() {
        super.alertOthers();
        LivingEntity attacker = npc.getLastHurtByMob();
        if (attacker == null) {
            return;
        }
        double range = getFollowDistance();
        AABB area = AABB.unitCubeFromLowerCorner(npc.position()).inflate(range, 10.0, range);
        for (NpcBase other : npc.level().getEntitiesOfClass(NpcBase.class, area, EntitySelector.NO_SPECTATORS)) {
            if (other == npc || other.getTarget() != null || other.isPassive() || other.getGuardMode() == GuardMode.PASSIVE) {
                continue;
            }
            Optional<Relation> relation = npc.getRelationTo(other);
            if (relation.isPresent() && relation.get().isFriendlyOrBetter() && AIHelper.isTarget(other, attacker, false)) {
                alertOther(other, attacker);
            }
        }
    }
}
