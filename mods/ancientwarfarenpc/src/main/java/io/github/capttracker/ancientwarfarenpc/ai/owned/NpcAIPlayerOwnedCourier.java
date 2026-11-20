/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/owned/NpcAIPlayerOwnedCourier.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfare.owner.IOwnable;
import io.github.capttracker.ancientwarfare.util.WorldTools;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcCourier;
import io.github.capttracker.ancientwarfarenpc.orders.RoutingOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Courier: walks the route points of the {@link RoutingOrder} in its orders slot, moves items between its backpack and the item handler of the block at
 * each point ({@code RoutingOrder.handleRouteAction}) and waits {@code (work ticks - 30 - level) * items moved} ticks there. Only blocks whose owner the
 * NPC may command are used. Saves the route index and the two counters.
 *
 * <p>The item handler of a block is the 1.21 block capability ({@code WorldTools.getItemHandlerFromTile}), not a tile entity capability.
 */
public class NpcAIPlayerOwnedCourier extends NpcAI<NpcCourier> {
    private boolean init;
    private int routeIndex;
    private int ticksToWork;
    private int ticksAtSite;
    @Nullable
    private RoutingOrder order;

    public NpcAIPlayerOwnedCourier(NpcCourier npc) {
        super(npc);
        setMutexBits(ATTACK + MOVE);
    }

    @Override
    public boolean canUse() {
        if (!super.canUse()) {
            return false;
        }
        if (!init) {
            init = true;
            order = RoutingOrder.getRoutingOrder(npc.ordersStack);
            if (order == null || routeIndex >= order.size()) {
                routeIndex = 0;
            }
        }
        return !npc.shouldBeAtHome() && npc.backpackInventory != null && order != null && !order.isEmpty();
    }

    @Override
    public void start() {
        npc.addAITask(TASK_WORK);
    }

    @Override
    public void tick() {
        if (order == null || order.isEmpty()) {
            return;
        }
        if (routeIndex >= order.size()) {
            routeIndex = 0;
        }
        BlockPos pos = order.get(routeIndex).getTarget();
        double dist = distanceSq(pos);
        if (dist > AWNPCStatics.npcActionRange * AWNPCStatics.npcActionRange) {
            npc.addAITask(TASK_MOVE);
            ticksAtSite = 0;
            ticksToWork = 0;
            moveToPosition(pos, dist);
        } else {
            moveRetryDelay = 0;
            npc.getNavigation().stop();
            npc.removeAITask(TASK_MOVE);
            workAtSite();
        }
    }

    @Override
    public void stop() {
        ticksToWork = 0;
        ticksAtSite = 0;
        moveRetryDelay = 0;
        npc.getNavigation().stop();
        npc.removeAITask(TASK_WORK + TASK_MOVE);
    }

    private void workAtSite() {
        if (ticksToWork == 0) {
            startWork();
        } else {
            ticksAtSite++;
            if (npc.tickCount % 10 == 0) {
                npc.swing(InteractionHand.MAIN_HAND);
            }
            if (ticksAtSite > ticksToWork) {
                setMoveToNextSite();
            }
        }
    }

    private void startWork() {
        IItemHandler target = getTargetHandler();
        if (target != null && order != null && npc.backpackInventory != null) {
            ticksAtSite = 0;
            int moved = order.handleRouteAction(order.get(routeIndex), npc.backpackInventory, target);
            if (moved > 0) {
                ticksToWork = (AWNPCStatics.npcWorkTicks - 30 - npc.getLevelingStats().getLevel()) * moved;
                if (ticksToWork <= 0) {
                    ticksToWork = 0;
                }
                ticksToWork = (int) Math.ceil(ticksToWork / npc.getWorkFactor()); // hunger slows the work down
                npc.addExperience(moved * AWNPCStatics.npcXpFromMoveItem);
                return;
            }
        }
        setMoveToNextSite();
    }

    @Nullable
    private IItemHandler getTargetHandler() {
        if (order == null) {
            return null;
        }
        RoutingOrder.RoutePoint point = order.get(routeIndex);
        Optional<BlockEntity> tile = WorldTools.getTile(npc.level(), point.getTarget());
        if (tile.isEmpty()) {
            return null;
        }
        return getTargetHandler(point.getBlockSide(), tile.get());
    }

    @Nullable
    private IItemHandler getTargetHandler(@Nullable Direction side, BlockEntity te) {
        if (te instanceof IOwnable ownableTE && !npc.hasCommandPermissions(ownableTE.getOwner())) {
            return null;
        }
        return WorldTools.getItemHandlerFromTile(npc.level(), te.getBlockPos(), side).orElse(null);
    }

    private void setMoveToNextSite() {
        ticksAtSite = 0;
        ticksToWork = 0;
        moveRetryDelay = 0;
        routeIndex++;
        if (order == null || routeIndex >= order.size()) {
            routeIndex = 0;
        }
    }

    public void onOrdersChanged() {
        order = RoutingOrder.getRoutingOrder(npc.ordersStack);
        routeIndex = 0;
        ticksAtSite = 0;
        ticksToWork = 0;
        moveRetryDelay = 0;
    }

    public void readFromNBT(CompoundTag tag) {
        routeIndex = tag.getInt("routeIndex");
        ticksAtSite = tag.getInt("ticksAtSite");
        ticksToWork = tag.getInt("ticksToWork");
    }

    public CompoundTag writeToNBT(CompoundTag tag) {
        tag.putInt("routeIndex", routeIndex);
        tag.putInt("ticksAtSite", ticksAtSite);
        tag.putInt("ticksToWork", ticksToWork);
        return tag;
    }
}
