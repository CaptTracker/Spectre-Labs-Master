/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/faction/NpcFactionLeader.
 */
package io.github.capttracker.ancientwarfarenpc.entity.faction;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAIAttackMeleeLongRange;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIAttackNearest;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIDefendFriends;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIDoor;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIFollowPlayer;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIMoveHome;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIWander;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIWatchClosest;
import io.github.capttracker.ancientwarfarenpc.ai.faction.NpcAIFactionCommander;
import io.github.capttracker.ancientwarfarenpc.ai.faction.NpcAIFactionHurt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/** The melee leader of a faction; {@code NpcAIFactionCommander} buffs the faction NPCs around it. */
public class NpcFactionLeader extends NpcFaction {
    // Assigned in registerGoals (server only), see NpcBase: no initialiser, and null on the client.
    @Nullable
    private NpcAIAttackMeleeLongRange meleeAI;

    public NpcFactionLeader(EntityType<? extends NpcFactionLeader> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        meleeAI = new NpcAIAttackMeleeLongRange(this);
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new NpcAIDoor(this, true));
        goalSelector.addGoal(1, new NpcAIFactionCommander(this));
        goalSelector.addGoal(1, new NpcAIFollowPlayer(this));
        goalSelector.addGoal(3, meleeAI);
        goalSelector.addGoal(4, new NpcAIMoveHome(this, 50F, 5F, 30F, 5F));
        //post-100 -- used by delayed shared tasks (look at random stuff, wander)
        goalSelector.addGoal(101, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        goalSelector.addGoal(102, new NpcAIWander(this));
        goalSelector.addGoal(103, new NpcAIWatchClosest(this, Mob.class, 8.0F));

        targetSelector.addGoal(1, new NpcAIFactionHurt(this, this::isHostileTowards));
        targetSelector.addGoal(2, new NpcAIAttackNearest(this, this::isHostileTowards));
        targetSelector.addGoal(3, new NpcAIDefendFriends(this));
    }

    @Override
    public boolean worksInRain() {
        return true;
    }

    @Override
    public boolean isPassive() {
        return false;
    }

    @Override
    public String getNpcType() {
        return "leader";
    }

    @Override
    public boolean shouldSleep() {
        return false;
    }

    @Override
    public void onWeaponInventoryChanged() {
        super.onWeaponInventoryChanged();
        if (meleeAI != null) {
            meleeAI.setAttackReachFromWeapon(getMainHandItem());
        }
    }
}
