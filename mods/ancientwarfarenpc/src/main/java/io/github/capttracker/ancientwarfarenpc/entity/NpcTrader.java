/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/NpcTrader.
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
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIMarketStall;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedGetFood;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedRideHorse;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedTrader;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAISeaTrade;
import io.github.capttracker.ancientwarfarenpc.orders.SeaRoute;
import io.github.capttracker.ancientwarfarenpc.orders.TradeOrder;
import io.github.capttracker.ancientwarfarenpc.trade.MarketApi;
import io.github.capttracker.ancientwarfarenpc.trade.POTradeList;
import io.github.capttracker.ancientwarfarenpc.trade.SeaTrade;
import io.github.capttracker.ancientwarfarenpc.trade.SeaTradeState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
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
import java.util.UUID;

/** The shop NPC: the trade list comes from the trade order in the orders slot; strangers trade with it while it is fed. */
public class NpcTrader extends NpcPlayerOwned {

    @Nullable
    private Player trader;//used by guis/containers to prevent further interaction
    @Nullable
    private POTradeList tradeList;
    // Assigned in registerGoals (server only), see NpcBase: no initialiser, and null on the client.
    @Nullable
    private NpcAIPlayerOwnedTrader tradeAI;

    /** The market stall this trader stands at (server only, not saved: the goal sets it when it starts and clears it when it ends). */
    @Nullable
    private BlockPos marketStallPos;

    public NpcTrader(EntityType<? extends NpcTrader> type, Level level) {
        super(type, level);
        tradeList = new POTradeList();
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
        goalSelector.addGoal(3, new NpcAISeaTrade(this));
        goalSelector.addGoal(4, (tradeAI = new NpcAIPlayerOwnedTrader(this)));
        goalSelector.addGoal(5, new NpcAIPlayerOwnedGetFood(this));
        goalSelector.addGoal(6, new NpcAIScheduleGoal(this));
        goalSelector.addGoal(6, new NpcAIMarketStall(this));
        goalSelector.addGoal(7, new NpcAIMoveHome(this, 50F, 3F, 30F, 3F));

        //post-100 -- used by delayed shared tasks (look at random stuff, wander)
        goalSelector.addGoal(101, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        goalSelector.addGoal(102, new NpcAIWander(this));
        goalSelector.addGoal(103, new LookAtPlayerGoal(this, Mob.class, 8.0F));
    }

    @Override
    public boolean isValidOrdersStack(ItemStack stack) {
        return NpcItemIds.is(stack, NpcItemIds.TRADE_ORDER) || NpcItemIds.is(stack, NpcItemIds.SEA_ROUTE);
    }

    @Override
    public void onOrdersInventoryChanged() {
        seaRouteKnown = false;
        tradeList = null;
        if (NpcItemIds.is(ordersStack, NpcItemIds.TRADE_ORDER)) {
            tradeList = TradeOrder.getTradeOrder(ordersStack).getTradeList();
        }
        if (tradeAI != null) {
            tradeAI.onOrdersUpdated();
        }
    }

    @Override
    public String getNpcSubType() {
        return "";
    }

    @Override
    public String getNpcType() {
        return "trader";
    }

    @Override
    protected boolean processInteract(Player player, InteractionHand hand) {
        boolean baton = NpcItemIds.isCommandBaton(player.getItemInHand(hand));
        if (baton) {
            return false;
        }
        if (player.level().isClientSide) {
            return true;
        }
        if (isOwner(player))//owner
        {
            return tryCommand(player);
        } else//non-owner
        {
            if (player instanceof ServerPlayer serverPlayer && MarketApi.stallOf(this).isPresent()) {
                // at its market stall the trader sells what the stall offers (rework phase 5)
                NpcBridge.openGui(serverPlayer, this, NpcBridge.Gui.MARKET_SHOP);
                return true;
            }
            if (trader != null && !trader.isAlive()) {
                closeTrade();
            }
            if (trader == null) {
                startTrade(player);
                openAltGui(player);
            }
        }
        return true;
    }

    // ---- sea trade (rework phase 11): a sea route chart in the orders slot makes the trader sail a cargo ship from dock to dock ----

    private final SeaTradeState seaState = new SeaTradeState();
    @Nullable
    private SeaRoute seaRoute;
    private boolean seaRouteKnown = false;

    /** What this trader remembers of its sea route: the ship, the stop, the phase. */
    public SeaTradeState getSeaState() {
        return seaState;
    }

    /** The sea route chart in the orders slot (a copy, kept until the slot changes), or null if the trader has no chart. */
    @Nullable
    public SeaRoute getSeaRoute() {
        if (!seaRouteKnown) {
            seaRoute = SeaRoute.get(ordersStack);
            seaRouteKnown = true;
        }
        return seaRoute;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            SeaTrade.tick(this);
        }
    }

    /** The wagon this trader hauls along its trade route (rework phase 5), saved. */
    @Nullable
    private UUID wagonId;

    @Nullable
    public UUID getWagonId() {
        return wagonId;
    }

    public void setWagonId(@Nullable UUID id) {
        wagonId = id;
    }

    @Nullable
    public BlockPos getMarketStallPos() {
        return marketStallPos;
    }

    public void setMarketStallPos(@Nullable BlockPos pos) {
        marketStallPos = pos;
    }

    public void startTrade(Player player) {
        trader = player;
    }

    public void closeTrade() {
        trader = null;
    }

    @Override
    public void openAltGui(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            NpcBridge.openGui(serverPlayer, this, NpcBridge.Gui.PLAYER_OWNED_TRADE);
        }
    }

    @Override
    public boolean hasAltGui() {
        return true;
    }

    /** At night in a dimension with a sky, or in the rain. */
    @Override
    public boolean shouldBeAtHome() {
        return (level().dimensionType().hasSkyLight() && !level().isDay()) || level().isRainingAt(blockPosition()) || pausedByStorm();
    }

    @Override
    public boolean isHostileTowards(Entity e) {
        return false;
    }

    @Nullable
    public POTradeList getTradeList() {
        return tradeList;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (tradeAI != null) {
            tag.put("tradeAI", tradeAI.writeToNBT(new CompoundTag()));
        }
        if (wagonId != null) {
            tag.putUUID("wagon", wagonId);
        }
        tag.put("sea", seaState.save());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tradeAI != null) {
            tradeAI.readFromNBT(tag.getCompound("tradeAI"));
        }
        wagonId = tag.hasUUID("wagon") ? tag.getUUID("wagon") : null;
        seaRouteKnown = false;
        if (tag.contains("sea")) {
            seaState.load(tag.getCompound("sea"));
        }
    }

}
