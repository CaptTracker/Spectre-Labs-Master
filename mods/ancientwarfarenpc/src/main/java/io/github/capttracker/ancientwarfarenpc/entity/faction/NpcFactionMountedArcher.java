/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/faction/NpcFactionMountedArcher.
 */
package io.github.capttracker.ancientwarfarenpc.entity.faction;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAIAttackNearest;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIDefendFriends;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIDoor;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIFollowPlayer;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIWander;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIWatchClosest;
import io.github.capttracker.ancientwarfarenpc.ai.faction.NpcAIFactionArcherStayAtHome;
import io.github.capttracker.ancientwarfarenpc.ai.faction.NpcAIFactionHurt;
import io.github.capttracker.ancientwarfarenpc.ai.faction.NpcAIFactionRangedAttack;
import io.github.capttracker.ancientwarfarenpc.entity.RangeAttackHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** The mounted archer of a faction: shoots from its mount and stays within 30 blocks of its home. */
public class NpcFactionMountedArcher extends NpcFactionMounted implements RangedAttackMob {
    public NpcFactionMountedArcher(EntityType<? extends NpcFactionMountedArcher> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // the same selector as the archer: hostile and within 30 blocks of the home
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new NpcAIDoor(this, true));
        goalSelector.addGoal(1, new NpcAIFollowPlayer(this));
        goalSelector.addGoal(2, new NpcAIFactionArcherStayAtHome(this));
        goalSelector.addGoal(3, new NpcAIFactionRangedAttack(this));
        //post-100 -- used by delayed shared tasks (look at random stuff, wander)
        goalSelector.addGoal(101, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        goalSelector.addGoal(102, new NpcAIWander(this));
        goalSelector.addGoal(103, new NpcAIWatchClosest(this, Mob.class, 8.0F));

        targetSelector.addGoal(1, new NpcAIFactionHurt(this, this::isHostileTowardsNearHome));
        targetSelector.addGoal(2, new NpcAIAttackNearest(this, this::isHostileTowardsNearHome));
        targetSelector.addGoal(3, new NpcAIDefendFriends(this));
    }

    @Override
    public String getNpcType() {
        return "mounted_archer";
    }

    @Override
    public void performRangedAttack(LivingEntity target, float force) {
        RangeAttackHelper.doRangedAttack(this, target, force, 1.0f);
    }
}
