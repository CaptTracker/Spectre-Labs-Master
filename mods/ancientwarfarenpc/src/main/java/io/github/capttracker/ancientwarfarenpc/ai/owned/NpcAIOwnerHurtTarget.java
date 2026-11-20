/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/owned/NpcAIOwnerHurtTarget.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfarenpc.ai.AIHelper;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.EnumSet;

/** Target goal: what the owner attacked last (the clone of the vanilla {@code OwnerHurtTargetGoal} for the owner of an NPC). */
public class NpcAIOwnerHurtTarget extends TargetGoal {
    private final NpcPlayerOwned npc;
    @Nullable
    private LivingEntity attacker;
    private int timestamp;

    public NpcAIOwnerHurtTarget(NpcPlayerOwned npc) {
        super(npc, false);
        this.npc = npc;
        setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        Player owner = npc.level().getPlayerByUUID(npc.getOwner().getUUID());
        if (owner == null) {
            return false;
        }
        attacker = owner.getLastHurtMob();
        int i = owner.getLastHurtMobTimestamp();
        return i != timestamp && canAttack(attacker, TargetingConditions.DEFAULT) && AIHelper.isWithinFollowRange(npc, attacker);
    }

    @Override
    public void start() {
        npc.setTarget(attacker);
        targetMob = attacker;
        Player owner = npc.level().getPlayerByUUID(npc.getOwner().getUUID());
        if (owner != null) {
            timestamp = owner.getLastHurtMobTimestamp();
        }
        super.start();
    }

    @Override
    protected boolean canAttack(@Nullable LivingEntity potentialTarget, TargetingConditions targetPredicate) {
        return AIHelper.isTarget(npc, potentialTarget, mustSee);
    }
}
