/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/NpcPriest.
 */
package io.github.capttracker.ancientwarfarenpc.entity;

import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIScheduleGoal;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIDoor;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIFleeHostiles;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIFollowPlayer;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIMoveHome;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIWander;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedAlarmResponse;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedFollowCommand;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedGetFood;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedPriest;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedRideHorse;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Respawns the dead NPCs of its town hall (AI {@code NpcAIPlayerOwnedPriest}); the orders slot is unused. */
public class NpcPriest extends NpcPlayerOwned {

    public NpcPriest(EntityType<? extends NpcPriest> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new NpcAIDoor(this, true));
        goalSelector.addGoal(0, (horseAI = new NpcAIPlayerOwnedRideHorse(this)));
        goalSelector.addGoal(2, new NpcAIFollowPlayer(this));
        goalSelector.addGoal(2, new NpcAIPlayerOwnedFollowCommand(this));
        goalSelector.addGoal(3, new NpcAIFleeHostiles(this));
        goalSelector.addGoal(3, new NpcAIPlayerOwnedAlarmResponse(this));
        goalSelector.addGoal(4, new NpcAIPlayerOwnedGetFood(this));
        goalSelector.addGoal(5, new NpcAIScheduleGoal(this));
        goalSelector.addGoal(6, new NpcAIMoveHome(this, 50F, 8F, 30F, 3F));
        goalSelector.addGoal(7, new NpcAIPlayerOwnedPriest(this));

        //post-100 -- used by delayed shared tasks (look at random stuff, wander)
        goalSelector.addGoal(101, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        goalSelector.addGoal(102, new NpcAIWander(this));
        goalSelector.addGoal(103, new LookAtPlayerGoal(this, Mob.class, 8.0F));
    }

    @Override
    public void onOrdersInventoryChanged() {

    }

    @Override
    public String getNpcSubType() {
        return "";
    }

    @Override
    public String getNpcType() {
        return "priest";
    }

}
