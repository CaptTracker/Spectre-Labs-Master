/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/owned/NpcAIPlayerOwnedTrader.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfare.item.BackpackItem;
import io.github.capttracker.ancientwarfare.util.WorldTools;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.entity.NpcTrader;
import io.github.capttracker.ancientwarfarenpc.trade.TradeWagon;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarenpc.orders.TradeOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;

/**
 * The trader's state machine: it walks the waypoints of its {@link TradeOrder} with a delay at each, optionally stops at the upkeep point to eat, deposits
 * and withdraws goods at the restock points and shelters at the nearer of the two last waypoints when it should be home (night, rain). Saves only the
 * waypoint index.
 *
 * <p>Rework phase 5: with a hitched wagon near it and a route of two or more points, an {@link NpcTrader} takes the wagon along ({@link TradeWagon}): the
 * horse follows the trader, the trader waits for a wagon that falls behind, and at each stop inside a settlement of its owner the deposit and withdraw
 * lists load and unload the wagon at the linked storages.
 *
 * <p>Uses the route and restock data of the trade order through the getters of the 1.12.2 classes ({@code getRoute().size()}, {@code get(i).getPosition()},
 * {@code getDelay()}, {@code shouldUpkeep()}; {@code getRestockData().getDepositPoint()} and so on).
 */
public class NpcAIPlayerOwnedTrader extends NpcAI<NpcPlayerOwned> {
    /*
     * state flags, to track what state the AI is currently in
     */
    private boolean shelter;
    private boolean upkeep;
    private boolean restock;
    private boolean deposit;
    private boolean waiting;
    private boolean atShelter;
    private boolean atUpkeep;
    private boolean atDeposit;
    private boolean atWithdraw;
    private boolean atWaypoint;

    private int delayCounter;
    private int wagonWait;
    private int currentWaypointIndex;
    @Nullable
    private BlockPos currentWaypoint;
    @Nullable
    private BlockPos shelterPoint;
    @Nullable
    private TradeOrder orders;
    @Nullable
    private VehicleBase wagon;

    public NpcAIPlayerOwnedTrader(NpcPlayerOwned npc) {
        super(npc);
        setMutexBits(MOVE + ATTACK);
    }

    public void onOrdersUpdated() {
        orders = TradeOrder.getTradeOrder(npc.ordersStack);
        currentWaypoint = null;
        shelterPoint = null;
        upkeep = false;
        restock = false;
        deposit = false;
        waiting = false;
        atDeposit = false;
        atShelter = false;
        atUpkeep = false;
        atWaypoint = false;
        atWithdraw = false;
        currentWaypointIndex = 0;
        delayCounter = 0;
        if (orders != null && orders.getRoute().size() > 0) {
            currentWaypoint = orders.getRoute().get(currentWaypointIndex).getPosition();
        }
    }

    @Override
    public boolean canUse() {
        return super.canUse() && orders != null && currentWaypoint != null;
    }

    /** The wagon of the route, taken when there is one near and the route has at least two points; null if the trader goes without. */
    @Nullable
    private VehicleBase updateWagon() {
        if (!(npc instanceof NpcTrader trader) || orders == null) {
            return null;
        }
        if (orders.getRoute().size() < 2) {
            if (trader.getWagonId() != null) {
                TradeWagon.release(trader);
            }
            return null;
        }
        VehicleBase wagon = TradeWagon.wagonOf(trader);
        if (wagon == null) {
            if (trader.getWagonId() != null && ++wagonWait > TradeWagon.PATIENCE_TICKS) {
                trader.setWagonId(null); // gone or never loaded again: the trader goes on without it
                wagonWait = 0;
            } else if (trader.getWagonId() == null && npc.tickCount % 40 == 0) {
                wagon = TradeWagon.bind(trader, orders);
            }
        } else {
            wagonWait = 0;
        }
        return wagon != null && TradeWagon.follow(trader, wagon) ? wagon : null;
    }

    /** True if the wagon is too far behind for the trader to walk on. A wagon that does not catch up in time is let go. */
    private boolean waitForWagon(@Nullable VehicleBase wagon) {
        if (wagon == null || !(npc instanceof NpcTrader trader)) {
            return false;
        }
        if (npc.distanceToSqr(wagon) <= TradeWagon.WAIT_RANGE * TradeWagon.WAIT_RANGE) {
            wagonWait = 0;
            return false;
        }
        npc.getNavigation().stop();
        if (++wagonWait > TradeWagon.PATIENCE_TICKS) {
            TradeWagon.release(trader);
            wagonWait = 0;
            return false;
        }
        return true;
    }

    @Override
    public void tick() {
        if (orders == null || currentWaypoint == null) {
            return;
        }
        wagon = updateWagon();
        if (npc.shouldBeAtHome() || shelter) {
            updateShelter();
        } else if (upkeep) {
            updateUpkeep();
        } else if (restock) {
            updateRestock();
        } else {
            updatePatrol();
        }
    }

    private void updateShelter() {
        TradeOrder order = orders;
        BlockPos waypoint = currentWaypoint;
        if (order == null || waypoint == null) {
            return;
        }
        npc.addAITask(TASK_GO_HOME);
        shelter = true;
        if (atShelter) {
            if (!npc.shouldBeAtHome()) {
                shelter = false;
                atShelter = false;
                shelterPoint = null;
                upkeep = false;
                atUpkeep = false;
                deposit = false;
                restock = false;
                atDeposit = false;
                atWithdraw = false;
                waiting = false;
                delayCounter = 0;
                npc.removeAITask(TASK_GO_HOME);
            }//end shelter code, return to previously current route point - if was interrupted in the middle of upkeep, will restart upkeep
        } else if (shelterPoint == null) {
            int index = currentWaypointIndex - 1;
            if (index < 0) {
                index = order.getRoute().size() - 1;
            }
            BlockPos wp2 = order.getRoute().get(index).getPosition();
            double d1 = distanceSq(waypoint);
            double d2 = distanceSq(wp2);
            shelterPoint = d1 < d2 ? waypoint : wp2;
        } else {
            double d = distanceSq(shelterPoint);
            if (d > MIN_RANGE) {
                npc.addAITask(TASK_MOVE);
                moveToPosition(shelterPoint, d);
            } else {
                npc.removeAITask(TASK_MOVE);
                atShelter = true;
            }
        }
    }

    private void updateUpkeep() {
        npc.addAITask(TASK_UPKEEP);
        if (atUpkeep) {
            if (tryWithdrawUpkeep()) {
                atUpkeep = false;
                upkeep = false;
                restock = true;
                deposit = true;
                npc.removeAITask(TASK_UPKEEP);
                npc.removeAITask(TASK_IDLE_HUNGRY);
            }
        } else if (npc.getUpkeepPoint().isPresent()) {
            npc.getUpkeepPoint().ifPresent(upkeepPoint -> {
                double d = distanceSq(upkeepPoint);
                if (d > MIN_RANGE) {
                    npc.addAITask(TASK_MOVE);
                    moveToPosition(upkeepPoint, d);
                } else {
                    npc.removeAITask(TASK_MOVE);
                    atUpkeep = true;
                }
            });
        } else//no upkeep point, display no upkeep task/state icon
        {
            npc.addAITask(TASK_IDLE_HUNGRY);
            npc.removeAITask(TASK_UPKEEP);
        }
    }

    private boolean tryWithdrawUpkeep() {
        return npc.getUpkeepPoint()
                .map(upkeepPoint -> WorldTools.getItemHandlerFromTile(npc.level(), upkeepPoint, npc.getUpkeepBlockSide()).map(h -> npc.withdrawFood(h)).orElse(false))
                .orElse(false);
    }

    private void updateRestock() {
        if (deposit) {
            updateDeposit();
        } else {
            updateWithdraw();
        }
    }

    private void updateDeposit() {
        if (orders == null) {
            return;
        }
        BlockPos depositPoint = orders.getRestockData().getDepositPoint();
        if (atDeposit) {
            doDeposit();
            deposit = false;
            atDeposit = false;
        } else if (depositPoint != null) {
            double d = distanceSq(depositPoint);
            if (d > MIN_RANGE) {
                npc.addAITask(TASK_MOVE);
                moveToPosition(depositPoint, d);
            } else {
                npc.removeAITask(TASK_MOVE);
                atDeposit = true;
            }
        } else//no deposit point
        {
            deposit = false;//kick into withdraw mode
        }
    }

    private void updateWithdraw() {
        if (orders == null) {
            return;
        }
        BlockPos withdrawPoint = orders.getRestockData().getWithdrawPoint();
        if (atWithdraw) {
            doWithdraw();
            setNextWaypoint();
            restock = false;
            atWithdraw = false;
        } else if (withdrawPoint != null) {
            double d = distanceSq(withdrawPoint);
            if (d > MIN_RANGE) {
                npc.addAITask(TASK_MOVE);
                moveToPosition(withdrawPoint, d);
            } else {
                npc.removeAITask(TASK_MOVE);
                atWithdraw = true;
            }
        } else//no withdraw point
        {
            restock = false;
            setNextWaypoint();
        }
    }

    private void updatePatrol() {
        TradeOrder order = orders;
        BlockPos waypoint = currentWaypoint;
        if (order == null || waypoint == null) {
            return;
        }
        if (atWaypoint) {
            if (waiting) {
                delayCounter++;
                if (delayCounter >= order.getRoute().get(currentWaypointIndex).getDelay()) {
                    if (wagon != null && npc instanceof NpcTrader trader) {
                        // the wagon has had the whole wait to catch up: load and unload before moving on
                        TradeWagon.loadAndUnload(trader, wagon, waypoint, order);
                    }
                    delayCounter = 0;
                    waiting = false;
                    atWaypoint = false;
                    if (order.getRoute().get(currentWaypointIndex).shouldUpkeep()) {
                        upkeep = true;
                    } else {
                        setNextWaypoint();
                    }
                }
            } else {
                waiting = true;
                delayCounter = 0;
            }
        } else {
            npc.addAITask(TASK_MOVE);
            double d = distanceSq(waypoint);
            if (d < MIN_RANGE) {
                atWaypoint = true;
                waiting = false;
                npc.removeAITask(TASK_MOVE);
            } else if (!waitForWagon(wagon)) {
                moveToPosition(waypoint, d);
            }
        }
    }

    private void setNextWaypoint() {
        if (orders == null) {
            return;
        }
        currentWaypointIndex++;
        if (currentWaypointIndex >= orders.getRoute().size()) {
            currentWaypointIndex = 0;
        }
        currentWaypoint = orders.getRoute().get(currentWaypointIndex).getPosition();
    }

    private void doDeposit() {
        TradeOrder order = orders;
        ItemStack backpack = npc.getMainHandItem();
        if (order != null && !backpack.isEmpty() && backpack.getItem() instanceof BackpackItem) {
            IItemHandler inv = backpack.getCapability(Capabilities.ItemHandler.ITEM);
            BlockPos pos = order.getRestockData().getDepositPoint();
            if (inv != null && pos != null) {
                WorldTools.getItemHandlerFromTile(npc.level(), pos, order.getRestockData().getDepositSide())
                        .ifPresent(handler -> order.getRestockData().doDeposit(inv, handler));
            }
        }
    }

    private void doWithdraw() {
        TradeOrder order = orders;
        ItemStack backpack = npc.getMainHandItem();
        if (order != null && !backpack.isEmpty() && backpack.getItem() instanceof BackpackItem) {
            IItemHandler inv = backpack.getCapability(Capabilities.ItemHandler.ITEM);
            BlockPos pos = order.getRestockData().getWithdrawPoint();
            if (inv != null && pos != null) {
                WorldTools.getItemHandlerFromTile(npc.level(), pos, order.getRestockData().getWithdrawSide())
                        .ifPresent(handler -> order.getRestockData().doWithdraw(inv, handler));
            }
        }
    }

    public void readFromNBT(CompoundTag tag) {
        orders = TradeOrder.getTradeOrder(npc.ordersStack);
        currentWaypointIndex = tag.getInt("waypoint");
        currentWaypoint = (orders == null || orders.getRoute().size() == 0 || currentWaypointIndex >= orders.getRoute().size())
                ? null : orders.getRoute().get(currentWaypointIndex).getPosition();
    }

    public CompoundTag writeToNBT(CompoundTag tag) {
        tag.putInt("waypoint", currentWaypointIndex);
        return tag;
    }
}
