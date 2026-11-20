/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/owned/NpcAIPlayerOwnedCommander.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Commander subtype: every 2 seconds it gives Strength (1 second) to the NPCs within its follow range that its owner can command. Runs from the
 * target goals (slot 0) and sets no flags.
 *
 * <h2>Original bugs fixed</h2>
 * <ul>
 *   <li>{@code canBeCommanded(npc)} of 1.12.2 asked the candidate whether it has command permissions over its own owner (the parameter hid the field), which is
 *       always true: the commander buffed the NPCs of every player. It asks the commander now (the report says "that the owner can command").</li>
 *   <li>The search box was {@code AxisAlignedBB.expand(range, range / 2, range)}, which only extends towards positive x, y and z; it is grown on every side now.</li>
 * </ul>
 */
public class NpcAIPlayerOwnedCommander extends NpcAI<NpcBase> {
    private int lastExecuted = -1;
    protected MobEffectInstance effect = new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20);//apply 1 second strength potion, times 1.3 damage

    public NpcAIPlayerOwnedCommander(NpcBase npc) {
        super(npc);
    }

    /** True if the candidate is one of the NPCs this commander buffs. */
    protected boolean canBeCommanded(NpcBase candidate) {
        return npc.hasCommandPermissions(candidate.getOwner());
    }

    @Override
    public boolean canUse() {
        if (!super.canUse() || isNotCommander(npc)) {
            return false;
        }
        return (lastExecuted == -1 || npc.tickCount - lastExecuted > 40);//Wait 2 seconds
    }

    protected boolean isNotCommander(NpcBase candidate) {
        return !candidate.getNpcSubType().equals("commander");
    }

    @Override
    public void start() {
        lastExecuted = npc.tickCount;
        double dist = npc.getAttributeValue(Attributes.FOLLOW_RANGE);
        AABB bb = npc.getBoundingBox().inflate(dist, dist / 2, dist);
        List<NpcBase> potentialTargets = npc.level().getEntitiesOfClass(NpcBase.class, bb,
                n -> n != null && canBeCommanded(n) && isNotCommander(n) && !n.hasEffect(effect.getEffect()));
        for (NpcBase npcBase : potentialTargets) {
            npcBase.addEffect(new MobEffectInstance(effect));
        }
    }
}
