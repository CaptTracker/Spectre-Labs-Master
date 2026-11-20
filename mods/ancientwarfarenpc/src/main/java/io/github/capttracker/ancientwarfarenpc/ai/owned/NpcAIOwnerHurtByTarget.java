/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/owned/NpcAIOwnerHurtByTarget.
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

/**
 * Target goal: whoever hurt the owner last (the vanilla {@code OwnerHurtByTargetGoal} needs a {@code TamableAnimal}, so this is the clone for the
 * owner of an NPC). The owner is looked up by UUID among the players in the level.
 */
public class NpcAIOwnerHurtByTarget extends TargetGoal {
    private final NpcPlayerOwned npc;
    @Nullable
    private LivingEntity attacker;
    private int timestamp;

    public NpcAIOwnerHurtByTarget(NpcPlayerOwned npc) {
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
        attacker = owner.getLastHurtByMob();
        int i = owner.getLastHurtByMobTimestamp();
        return i != timestamp && canAttack(attacker, TargetingConditions.DEFAULT) && AIHelper.isWithinFollowRange(npc, attacker);
    }

    @Override
    public void start() {
        npc.setTarget(attacker);
        targetMob = attacker;
        Player owner = npc.level().getPlayerByUUID(npc.getOwner().getUUID());
        if (owner != null) {
            timestamp = owner.getLastHurtByMobTimestamp();
        }
        super.start();
    }

    @Override
    protected boolean canAttack(@Nullable LivingEntity potentialTarget, TargetingConditions targetPredicate) {
        return AIHelper.isTarget(npc, potentialTarget, mustSee);
    }
}
