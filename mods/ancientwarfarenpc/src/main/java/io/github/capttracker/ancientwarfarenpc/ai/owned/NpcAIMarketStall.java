/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): a trader staffs a market stall during the work hours.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfare.api.IMarketStall;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.entity.NpcTrader;
import io.github.capttracker.ancientwarfarenpc.npccommand.NpcCommand.Command;
import io.github.capttracker.ancientwarfarenpc.orders.TradeOrder;
import io.github.capttracker.ancientwarfarenpc.settlement.Activity;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.trade.MarketApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * The trader of a settlement staffs a market stall in the work band: it picks the nearest stall that no other trader holds (checked every 2 seconds),
 * walks to the block in front of it and stays there, reporting in to the stall every half second so it counts as open. It leaves at the end of the work band,
 * when the alarm sounds, at night or in the rain (the trader's own {@code shouldBeAtHome}), when it gets a command, or when it has a trade order with a route
 * (that trader walks its route as before). Players buy from it by right-clicking it (see {@code MarketShopMenu}).
 */
public class NpcAIMarketStall extends NpcAI<NpcTrader> {
    private static final int SEARCH_INTERVAL = 40;
    private static final int REPORT_INTERVAL = 10;
    private static final double REPORT_RANGE_SQ = 9.0D;

    private long nextSearch = 0;
    @Nullable
    private BlockPos stallPos;
    @Nullable
    private BlockPos standPos;
    private int reportDelay = 0;

    public NpcAIMarketStall(NpcTrader npc) {
        super(npc);
        setMutexBits(MOVE);
    }

    private boolean mayStaff() {
        if (npc.isAlarmed || npc.getTarget() != null || npc.isPassenger() || npc.shouldBeAtHome() || npc.getActivity() != Activity.WORK
                || npc.getCurrentCommand() != Command.NONE || !(npc.level() instanceof ServerLevel)) {
            return false;
        }
        TradeOrder order = TradeOrder.getTradeOrder(npc.ordersStack);
        return order == null || order.getRoute().size() == 0;
    }

    @Override
    public boolean canUse() {
        if (!super.canUse() || npc.tickCount < nextSearch || !mayStaff()) {
            return false;
        }
        nextSearch = npc.tickCount + SEARCH_INTERVAL;
        ServerLevel level = (ServerLevel) npc.level();
        Optional<Settlement> settlement = npc.getSettlement().filter(s -> s.getDimension().equals(level.dimension()));
        if (settlement.isEmpty()) {
            return false;
        }
        IMarketStall best = null;
        double bestDistance = Double.MAX_VALUE;
        for (IMarketStall stall : MarketApi.stallsOf(level, settlement.get())) {
            if (stall.isOpen() && stall.getTrader().filter(id -> !id.equals(npc.getUUID())).isPresent()) {
                continue; // another trader has it
            }
            double distance = npc.distanceToSqr(stall.getStallPos().getX() + 0.5D, stall.getStallPos().getY(), stall.getStallPos().getZ() + 0.5D);
            if (distance < bestDistance) {
                best = stall;
                bestDistance = distance;
            }
        }
        if (best == null) {
            return false;
        }
        stallPos = best.getStallPos();
        standPos = best.getStandPos();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return super.canUse() && stallPos != null && mayStaff() && stallAt() != null;
    }

    @Nullable
    private IMarketStall stallAt() {
        if (stallPos != null && npc.level().isLoaded(stallPos) && npc.level().getBlockEntity(stallPos) instanceof IMarketStall stall) {
            return stall;
        }
        return null;
    }

    @Override
    public void start() {
        reportDelay = 0;
        npc.setMarketStallPos(stallPos);
        npc.addAITask(TASK_WORK);
    }

    @Override
    public void tick() {
        IMarketStall stall = stallAt();
        BlockPos stand = standPos;
        BlockPos at = stallPos;
        if (stall == null || stand == null || at == null) {
            return;
        }
        double distance = npc.distanceToSqr(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D);
        if (distance > 1.5D) {
            npc.addAITask(TASK_MOVE);
            moveToPosition(stand.getX(), stand.getY(), stand.getZ(), distance);
        } else {
            npc.getNavigation().stop();
            npc.removeAITask(TASK_MOVE);
        }
        if (distance <= REPORT_RANGE_SQ) {
            npc.getLookControl().setLookAt(at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D);
            if (reportDelay-- <= 0) {
                reportDelay = REPORT_INTERVAL;
                if (!stall.staff(npc.getUUID(), npc.level().getGameTime())) {
                    stallPos = null; // another trader got there first: the goal ends, the next search takes another stall
                }
            }
        }
    }

    @Override
    public void stop() {
        IMarketStall stall = stallAt();
        if (stall != null) {
            stall.release(npc.getUUID());
        }
        stallPos = null;
        standPos = null;
        npc.setMarketStallPos(null);
        npc.getNavigation().stop();
        npc.removeAITask(TASK_WORK + TASK_MOVE);
        nextSearch = npc.tickCount + SEARCH_INTERVAL;
    }
}
