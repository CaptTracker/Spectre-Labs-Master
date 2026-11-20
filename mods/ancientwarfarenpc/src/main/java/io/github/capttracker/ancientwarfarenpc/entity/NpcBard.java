/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/NpcBard.
 */
package io.github.capttracker.ancientwarfarenpc.entity;

import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIScheduleGoal;
import io.github.capttracker.ancientwarfare.api.ISinger;
import io.github.capttracker.ancientwarfare.util.SongPlayData;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIDoor;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIFleeHostiles;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIFollowPlayer;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIMoveHome;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAISing;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIWander;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedAlarmResponse;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedFollowCommand;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedGetFood;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedRideHorse;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/** Sings the songs of its {@link SongPlayData} (AI {@code NpcAISing}). */
public class NpcBard extends NpcPlayerOwned implements ISinger {

    // The AI is created inside Mob's constructor, before the fields of this class are initialised: the data is created on first use.
    @Nullable
    private SongPlayData tuneData;

    public NpcBard(EntityType<? extends NpcBard> type, Level level) {
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

        goalSelector.addGoal(7, new NpcAIMoveHome(this, 50F, 3F, 30F, 3F));
        goalSelector.addGoal(8, new NpcAISing(this));

        //post-100 -- used by delayed shared tasks (look at random stuff, wander)
        goalSelector.addGoal(101, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        goalSelector.addGoal(102, new NpcAIWander(this));
        goalSelector.addGoal(103, new LookAtPlayerGoal(this, Mob.class, 8.0F));
    }

    /** Game time until which the last song started lasts (not saved): the settlement morale asks whether the bard has performed lately. */
    private long performedUntil = Long.MIN_VALUE;

    public void markPerformed(long until) {
        performedUntil = until;
    }

    /** True if the bard is singing, or sang until the given game time or later. */
    public boolean hasPerformedSince(long gameTime) {
        return performedUntil >= gameTime;
    }

    @Override
    public SongPlayData getSongs() {
        if (tuneData == null) {
            tuneData = new SongPlayData();
        }
        return tuneData;
    }

    @Override
    public String getNpcSubType() {
        return "";
    }

    @Override
    public String getNpcType() {
        return "bard";
    }

    @Override
    public boolean hasAltGui() {
        return true;
    }

    @Override
    public void openAltGui(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            NpcBridge.openGui(serverPlayer, this, NpcBridge.Gui.BARD);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        getSongs().readFromNBT(tag.getCompound("tuneData"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("tuneData", getSongs().writeToNBT(new CompoundTag()));
    }

}
