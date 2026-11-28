/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/AIHelper.
 */
package io.github.capttracker.ancientwarfarenpc.ai;

import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
 * Target checks shared by the target goals, and the global list of entities every NPC treats as hostile
 * (kept like the original: it is filled by other mods through {@link #addHostileEntityToTarget} and never saved).
 */
public final class AIHelper {
    private AIHelper() {
    }

    private static final Set<Integer> additionalHostileEntitiesToTarget = new HashSet<>();

    /**
     * True if the target is alive, is not the NPC, may be targeted by the NPC, is not an invulnerable (creative or spectator) player and,
     * if asked, is in line of sight of the NPC (1.12.2: {@code capabilities.disableDamage}, {@code getEntitySenses().canSee}). A melee soldier does not pick a dragon (Ice and Fire integration, see {@link MonsterRules}).
     */
    public static boolean isTarget(NpcBase npc, @Nullable LivingEntity target, boolean checkSight) {
        return target != null && !(target == npc || !target.isAlive() || !npc.canTarget(target)
                || target instanceof Player player && player.getAbilities().invulnerable
                || checkSight && !npc.getSensing().hasLineOfSight(target)
                || MonsterRules.keepsAwayFrom(npc, target));
    }

    public static boolean isWithinFollowRange(Mob entity, LivingEntity target) {
        return entity.distanceTo(target) <= entity.getAttributeValue(Attributes.FOLLOW_RANGE);
    }

    public static void addHostileEntityToTarget(Entity e) {
        additionalHostileEntitiesToTarget.add(e.getId());
    }

    public static void removeHostileEntityToTarget(Entity e) {
        additionalHostileEntitiesToTarget.remove(e.getId());
    }

    public static boolean isAdditionalEntityToTarget(Entity e) {
        return additionalHostileEntitiesToTarget.contains(e.getId());
    }
}
