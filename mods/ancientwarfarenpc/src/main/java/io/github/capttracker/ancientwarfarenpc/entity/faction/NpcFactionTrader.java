/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/faction/NpcFactionTrader.
 */
package io.github.capttracker.ancientwarfarenpc.entity.faction;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAIDoor;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIFollowPlayer;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIMoveHome;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIWander;
import io.github.capttracker.ancientwarfarenpc.ai.faction.NpcAICaravanLead;
import io.github.capttracker.ancientwarfarenpc.caravan.CaravanLogic;
import io.github.capttracker.ancientwarfarenpc.caravan.CaravanState;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBridge;
import io.github.capttracker.ancientwarfarenpc.entity.NpcItemIds;
import io.github.capttracker.ancientwarfarenpc.trade.FactionTradeList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * The trader of a faction: anyone can open the trade view (no food needed), the trades come from its {@link FactionTradeList}. A trader that
 * was loaded from a save despawns once its trade list is empty (1.12.2: {@code noTradesDespawn} was set when the NPC was read, never otherwise).
 *
 * <p>Rework phase 5: the trader can lead a caravan ({@link CaravanState}, saved here), see the {@code caravan} package. A caravan trader does not despawn
 * for an empty trade list; its caravan ends it.
 */
public class NpcFactionTrader extends NpcFaction {
    private final FactionTradeList tradeList = new FactionTradeList();
    @Nullable
    private Player trader;
    private boolean noTradesDespawn = false;
    @Nullable
    private CaravanState caravan;

    public NpcFactionTrader(EntityType<? extends NpcFactionTrader> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new NpcAIDoor(this, true));
        goalSelector.addGoal(1, new NpcAICaravanLead(this));
        goalSelector.addGoal(1, new NpcAIFollowPlayer(this));
        goalSelector.addGoal(2, new NpcAIMoveHome(this, 50F, 5F, 30F, 5F));
        //post-100 -- used by delayed shared tasks (look at random stuff, wander)
        goalSelector.addGoal(101, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        goalSelector.addGoal(102, new NpcAIWander(this));
        goalSelector.addGoal(103, new LookAtPlayerGoal(this, Mob.class, 8.0F));
    }

    public FactionTradeList getTradeList() {
        return tradeList;
    }

    /** The caravan this trader leads, or null for an ordinary faction trader. */
    @Nullable
    public CaravanState getCaravan() {
        return caravan;
    }

    public void setCaravan(@Nullable CaravanState caravan) {
        this.caravan = caravan;
    }

    public void startTrade(Player player) {
        trader = player;
    }

    public void closeTrade() {
        trader = null;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (noTradesDespawn && tradeList.isEmpty() && caravan == null) {
                discard();
                return;
            }
            tradeList.tick(level());
            if (caravan != null) {
                CaravanLogic.tick(this, caravan);
            }
        }
    }

    @Override
    protected boolean processInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        boolean baton = !held.isEmpty() && NpcItemIds.isCommandBaton(held);
        if (!baton && isAlive()) {
            if (player.isShiftKeyDown() && held.isEmpty()) {
                if (!player.level().isClientSide) {
                    openTalk(player); // sneak + empty hand: talk instead of trade
                }
                return true;
            }
            if (!player.level().isClientSide && trader == null && player instanceof ServerPlayer serverPlayer) {
                startTrade(player);
                NpcBridge.openGui(serverPlayer, this, NpcBridge.Gui.FACTION_TRADE_VIEW);
            }
            return true;
        }
        return false;
    }

    /** A killed caravan leader drops the cargo of the wagon (rework phase 5). */
    @Override
    public void die(DamageSource damageSource) {
        boolean wasLeader = !level().isClientSide && caravan != null && isAlive();
        super.die(damageSource);
        if (wasLeader && caravan != null) {
            CaravanLogic.onLeaderKilled(this, caravan);
            caravan = null;
        }
    }

    @Override
    public String getNpcType() {
        return "trader";
    }

    @Override
    public boolean isHostileTowards(Entity e) {
        return false;
    }

    @Override
    public boolean canTarget(Entity e) {
        return false;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        tradeList.deserializeNBT(registryAccess(), tag.getCompound("tradeList"));
        noTradesDespawn = true;
        caravan = tag.contains("caravan") ? CaravanState.load(tag.getCompound("caravan")) : null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("tradeList", tradeList.serializeNBT(registryAccess()));
        if (caravan != null) {
            tag.put("caravan", caravan.save());
        }
    }
}
