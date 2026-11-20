/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/faction/NpcFactionSpellcaster (NpcFactionSpellcasterWizardry is not ported).
 */
package io.github.capttracker.ancientwarfarenpc.entity.faction;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAIAttackNearest;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIDefendFriends;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIDoor;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIFollowPlayer;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIMoveHome;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIWander;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIWatchClosest;
import io.github.capttracker.ancientwarfarenpc.ai.faction.NpcAIFactionArcherStayAtHome;
import io.github.capttracker.ancientwarfarenpc.ai.faction.NpcAIFactionHurt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The spell caster of a faction. In 1.12.2 this was the class used when Electroblob's Wizardry is not installed; it casts nothing. The
 * Wizardry variant (same registry name, with a spell list) is not ported: the mod does not exist for 1.21.1.
 */
public class NpcFactionSpellcaster extends NpcFaction {
    public NpcFactionSpellcaster(EntityType<? extends NpcFactionSpellcaster> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new NpcAIDoor(this, true));
        goalSelector.addGoal(1, new NpcAIFollowPlayer(this));
        goalSelector.addGoal(2, new NpcAIMoveHome(this, 50.f, 5.f, 30.f, 5.f));
        goalSelector.addGoal(2, new NpcAIFactionArcherStayAtHome(this));
        //post-100 -- used by delayed shared tasks (look at random stuff, wander)
        goalSelector.addGoal(101, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        goalSelector.addGoal(102, new NpcAIWander(this));
        goalSelector.addGoal(103, new NpcAIWatchClosest(this, Mob.class, 8.0F));

        targetSelector.addGoal(1, new NpcAIFactionHurt(this, this::isHostileTowards));
        targetSelector.addGoal(2, new NpcAIAttackNearest(this, this::isHostileTowards));
        targetSelector.addGoal(3, new NpcAIDefendFriends(this));
    }

    @Override
    public boolean canAttackType(EntityType<?> type) {
        return true;
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
        return "spellcaster";
    }

    @Override
    public boolean shouldSleep() {
        return false;
    }
}
