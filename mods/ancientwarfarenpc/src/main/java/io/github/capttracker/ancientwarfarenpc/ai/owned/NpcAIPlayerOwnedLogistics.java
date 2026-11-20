/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 2): auto-hauling couriers.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfare.api.IWorkSite;
import io.github.capttracker.ancientwarfare.api.IWorksiteLogistics;
import io.github.capttracker.ancientwarfare.api.WorksiteRegistry;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcCourier;
import io.github.capttracker.ancientwarfarenpc.orders.RoutingOrder;
import io.github.capttracker.ancientwarfarenpc.settlement.JobBoard;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementApi;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The logistics loop of a courier without a routing order, inside its settlement. One stop at a time, nearest first:
 * <ol>
 *   <li>the backpack holds items: deliver them to a worksite that is short of them, else to the nearest storage that takes them (food that is
 *       balanced between storages goes to the storage that is low);</li>
 *   <li>the backpack is empty: empty the output of a worksite whose products fit a storage; else take what a worksite lacks (seeds, saplings, bone
 *       meal, animal feed, tools) out of a storage; else move spare food to a storage that is low.</li>
 * </ol>
 * Worksites are those of the {@link WorksiteRegistry} that implement {@link IWorksiteLogistics} and that the job board of the settlement
 * would let a worker use. The goal sits below the schedule and does not run for a courier with a routing order (those orders override it).
 */
public class NpcAIPlayerOwnedLogistics extends NpcAI<NpcCourier> {
    private static final int PLAN_INTERVAL = 100;
    private static final int RETRY_DELAY = 200;
    private static final int MAX_STOP_TICKS = 1200;

    private enum Kind { TAKE, GIVE }

    /**
     * One stop.
     *
     * @param site     the stop is a worksite (its outputs or needs) and not a storage
     * @param filter   storage stops: what to take
     * @param maxItems storage stops: how many to take
     * @param keepFood storage stops: food ticks the storage must keep, or -1
     */
    private record Task(BlockPos pos, Kind kind, boolean site, Predicate<ItemStack> filter, int maxItems, long keepFood) {}

    private long nextPlan;
    @Nullable
    private Task task;
    @Nullable
    private BlockPos foodDestination;
    private boolean finished;
    private int stopTicks;
    private int waitTicks;

    public NpcAIPlayerOwnedLogistics(NpcCourier npc) {
        super(npc);
        setMutexBits(ATTACK + MOVE);
    }

    private boolean enabled() {
        if (!SettlementConfig.autoHaul || npc.backpackInventory == null || !npc.isOnDuty() || npc.shouldBeAtHome()) {
            return false;
        }
        RoutingOrder order = RoutingOrder.getRoutingOrder(npc.ordersStack);
        return order == null || order.isEmpty();
    }

    @Override
    public boolean canUse() {
        if (!super.canUse() || !(npc.level() instanceof ServerLevel level) || !enabled()) {
            return false;
        }
        long now = level.getGameTime();
        if (now < nextPlan) {
            return false;
        }
        Optional<Settlement> settlement = npc.getSettlement();
        if (settlement.isEmpty() || !settlement.get().getDimension().equals(level.dimension())) {
            nextPlan = now + RETRY_DELAY;
            return false;
        }
        task = plan(level, settlement.get());
        if (task == null) {
            nextPlan = now + RETRY_DELAY;
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !finished && npc.isAlive() && super.canUse() && enabled();
    }

    @Override
    public void start() {
        finished = false;
        stopTicks = 0;
        waitTicks = 0;
        npc.addAITask(TASK_WORK);
    }

    @Override
    public void tick() {
        if (!(npc.level() instanceof ServerLevel level)) {
            return;
        }
        Optional<Settlement> settlement = npc.getSettlement();
        if (settlement.isEmpty()) {
            finish(level, RETRY_DELAY);
            return;
        }
        if (task == null) {
            task = plan(level, settlement.get());
            stopTicks = 0;
            if (task == null) {
                finish(level, RETRY_DELAY);
                return;
            }
        }
        if (waitTicks > 0) {
            waitTicks--;
            if (npc.tickCount % 10 == 0) {
                npc.swing(InteractionHand.MAIN_HAND);
            }
            if (waitTicks == 0) {
                task = null; // next tick plans the next stop
            }
            return;
        }
        if (++stopTicks > MAX_STOP_TICKS) {
            finish(level, PLAN_INTERVAL);
            return;
        }
        double dist = distanceSq(task.pos());
        if (dist > AWNPCStatics.npcActionRange * AWNPCStatics.npcActionRange) {
            npc.addAITask(TASK_MOVE);
            moveToPosition(task.pos(), dist);
            return;
        }
        moveRetryDelay = 0;
        npc.getNavigation().stop();
        npc.removeAITask(TASK_MOVE);
        int moved = perform(level, settlement.get(), task);
        if (moved <= 0) {
            finish(level, PLAN_INTERVAL);
            return;
        }
        npc.addExperience(moved * AWNPCStatics.npcXpFromMoveItem);
        waitTicks = (int) Math.ceil(Math.max(10, Math.min(80, moved * 4)) / npc.getWorkFactor());
    }

    private void finish(ServerLevel level, int delay) {
        finished = true;
        task = null;
        nextPlan = level.getGameTime() + delay;
    }

    @Override
    public void stop() {
        task = null;
        finished = false;
        moveRetryDelay = 0;
        npc.getNavigation().stop();
        npc.removeAITask(TASK_WORK + TASK_MOVE);
    }

    // ---------------------------------------------------------------- planning

    private double distTo(BlockPos pos) {
        return npc.distanceToSqr(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
    }

    /** The loaded worksites of the land that have a logistics view and that the settlement may use, nearest first. */
    private List<BlockEntity> sites(ServerLevel level, Settlement settlement) {
        List<BlockEntity> result = new ArrayList<>();
        for (BlockEntity be : WorksiteRegistry.inLevel(level, settlement::contains)) {
            if (be instanceof IWorksiteLogistics && be instanceof IWorkSite site && JobBoard.mayUse(settlement, site)) {
                result.add(be);
            }
        }
        result.sort(Comparator.comparingDouble(be -> distTo(be.getBlockPos())));
        return result;
    }

    @Nullable
    private Task plan(ServerLevel level, Settlement settlement) {
        IItemHandler pack = npc.backpackInventory;
        if (pack == null) {
            return null;
        }
        List<SettlementApi.StorageHandle> storages = new ArrayList<>(SettlementApi.allStorages(level, settlement));
        storages.sort(Comparator.comparingDouble(s -> distTo(s.pos())));
        if (storages.isEmpty()) {
            return null;
        }
        List<BlockEntity> sites = sites(level, settlement);
        return isEmpty(pack) ? planPickup(pack, storages, sites) : planDelivery(pack, storages, sites);
    }

    private static boolean isEmpty(IItemHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean accepts(IItemHandler handler, ItemStack stack) {
        return ItemHandlerHelper.insertItem(handler, stack.copy(), true).getCount() < stack.getCount();
    }

    private static boolean hasItem(IItemHandler handler, Predicate<ItemStack> test) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && test.test(stack)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private Task planDelivery(IItemHandler pack, List<SettlementApi.StorageHandle> storages, List<BlockEntity> sites) {
        for (BlockEntity be : sites) {
            for (IWorksiteLogistics.Need need : ((IWorksiteLogistics) be).getLogisticsNeeds()) {
                if (need.deficit() > 0 && hasItem(pack, stack -> need.accepts().test(stack) && accepts(need.inventory(), stack))) {
                    return new Task(be.getBlockPos(), Kind.GIVE, true, stack -> true, 0, -1);
                }
            }
        }
        BlockPos destination = foodDestination;
        if (destination != null) {
            for (SettlementApi.StorageHandle storage : storages) {
                if (storage.pos().equals(destination) && hasItem(pack, stack -> AWNPCStatics.getFoodValue(stack) > 0 && accepts(storage.handler(), stack))) {
                    return new Task(destination, Kind.GIVE, false, stack -> true, 0, -1);
                }
            }
            foodDestination = null;
        }
        for (SettlementApi.StorageHandle storage : storages) {
            if (hasItem(pack, stack -> accepts(storage.handler(), stack))) {
                return new Task(storage.pos(), Kind.GIVE, false, stack -> true, 0, -1);
            }
        }
        return null; // nowhere to put it: hold on to it and look again later
    }

    @Nullable
    private Task planPickup(IItemHandler pack, List<SettlementApi.StorageHandle> storages, List<BlockEntity> sites) {
        // 1. products to a storage
        for (BlockEntity be : sites) {
            for (IWorksiteLogistics.Output output : ((IWorksiteLogistics) be).getLogisticsOutputs()) {
                IItemHandler inventory = output.inventory();
                for (int slot = 0; slot < inventory.getSlots(); slot++) {
                    ItemStack stack = inventory.getStackInSlot(slot);
                    if (!stack.isEmpty() && output.takes().test(stack) && accepts(pack, stack)
                            && storages.stream().anyMatch(s -> accepts(s.handler(), stack))) {
                        return new Task(be.getBlockPos(), Kind.TAKE, true, s -> true, 0, -1);
                    }
                }
            }
        }
        // 2. what a worksite lacks, from a storage
        for (BlockEntity be : sites) {
            for (IWorksiteLogistics.Need need : ((IWorksiteLogistics) be).getLogisticsNeeds()) {
                int deficit = need.deficit();
                if (deficit <= 0) {
                    continue;
                }
                Predicate<ItemStack> wanted = stack -> need.accepts().test(stack) && accepts(need.inventory(), stack) && accepts(pack, stack);
                for (SettlementApi.StorageHandle storage : storages) {
                    if (hasItem(storage.handler(), wanted)) {
                        return new Task(storage.pos(), Kind.TAKE, false, wanted, deficit, -1);
                    }
                }
            }
        }
        // 3. food from a storage with spare to a storage that is low
        long low = (long) SettlementConfig.foodLowNpcDays * SettlementConfig.npcDayFoodTicks;
        long spare = (long) Math.max(SettlementConfig.foodSpareNpcDays, SettlementConfig.foodLowNpcDays + 1) * SettlementConfig.npcDayFoodTicks;
        SettlementApi.StorageHandle needy = null;
        long needyFood = low;
        SettlementApi.StorageHandle donor = null;
        long donorFood = spare;
        for (SettlementApi.StorageHandle storage : storages) {
            long food = SettlementApi.foodIn(storage.handler());
            if (food < needyFood) {
                needy = storage;
                needyFood = food;
            }
            if (food > donorFood) {
                donor = storage;
                donorFood = food;
            }
        }
        if (needy != null && donor != null && !needy.pos().equals(donor.pos())) {
            IItemHandler destination = needy.handler();
            Predicate<ItemStack> foodFilter = stack -> AWNPCStatics.getFoodValue(stack) > 0 && accepts(destination, stack) && accepts(pack, stack);
            if (hasItem(donor.handler(), foodFilter)) {
                foodDestination = needy.pos();
                return new Task(donor.pos(), Kind.TAKE, false, foodFilter, 64, spare);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- acting

    /** Does the stop and returns how many items were moved. */
    private int perform(ServerLevel level, Settlement settlement, Task stop) {
        IItemHandler pack = npc.backpackInventory;
        if (pack == null) {
            return 0;
        }
        if (stop.site()) {
            BlockEntity be = level.getBlockEntity(stop.pos());
            if (!(be instanceof IWorksiteLogistics logistics)) {
                return 0;
            }
            int moved = 0;
            if (stop.kind() == Kind.TAKE) {
                for (IWorksiteLogistics.Output output : logistics.getLogisticsOutputs()) {
                    moved += take(output.inventory(), pack, output.takes(), Integer.MAX_VALUE, -1);
                }
            } else {
                for (IWorksiteLogistics.Need need : logistics.getLogisticsNeeds()) {
                    moved += give(pack, need.inventory(), need.accepts(), need.deficit());
                }
            }
            return moved;
        }
        IItemHandler storage = SettlementApi.handlerFor(level, settlement, stop.pos());
        if (storage == null) {
            return 0;
        }
        if (stop.kind() == Kind.TAKE) {
            return take(storage, pack, stop.filter(), stop.maxItems(), stop.keepFood());
        }
        int moved = give(pack, storage, stack -> true, Integer.MAX_VALUE);
        if (stop.pos().equals(foodDestination)) {
            foodDestination = null;
        }
        return moved;
    }

    /** Moves matching items from the source into the backpack, as far as they fit; with a food limit the source keeps that many food ticks. */
    private static int take(IItemHandler source, IItemHandler pack, Predicate<ItemStack> filter, int maxItems, long keepFood) {
        int moved = 0;
        long food = keepFood >= 0 ? SettlementApi.foodIn(source) : 0;
        for (int slot = 0; slot < source.getSlots() && moved < maxItems; slot++) {
            ItemStack stack = source.getStackInSlot(slot);
            if (stack.isEmpty() || !filter.test(stack)) {
                continue;
            }
            int want = Math.min(stack.getCount(), maxItems - moved);
            int value = keepFood >= 0 ? AWNPCStatics.getFoodValue(stack) : 0;
            if (keepFood >= 0) {
                if (value <= 0) {
                    continue;
                }
                want = (int) Math.min(want, (food - keepFood) / value);
            }
            if (want <= 0) {
                continue;
            }
            ItemStack extracted = source.extractItem(slot, want, true);
            if (extracted.isEmpty()) {
                continue;
            }
            ItemStack rest = ItemHandlerHelper.insertItem(pack, extracted.copy(), true);
            int fits = extracted.getCount() - rest.getCount();
            if (fits <= 0) {
                continue;
            }
            ItemStack real = source.extractItem(slot, fits, false);
            ItemStack left = ItemHandlerHelper.insertItem(pack, real, false);
            if (!left.isEmpty()) {
                ItemHandlerHelper.insertItem(source, left, false); // cannot happen after the simulation; puts it back
            }
            int done = real.getCount() - left.getCount();
            moved += done;
            food -= (long) value * done;
        }
        return moved;
    }

    /** Moves matching items from the backpack into the target, at most {@code limit}. */
    private static int give(IItemHandler pack, IItemHandler target, Predicate<ItemStack> filter, int limit) {
        int moved = 0;
        for (int slot = 0; slot < pack.getSlots() && moved < limit; slot++) {
            ItemStack stack = pack.getStackInSlot(slot);
            if (stack.isEmpty() || !filter.test(stack)) {
                continue;
            }
            int want = Math.min(stack.getCount(), limit - moved);
            ItemStack extracted = pack.extractItem(slot, want, true);
            if (extracted.isEmpty()) {
                continue;
            }
            ItemStack rest = ItemHandlerHelper.insertItem(target, extracted.copy(), true);
            int fits = extracted.getCount() - rest.getCount();
            if (fits <= 0) {
                continue;
            }
            ItemStack real = pack.extractItem(slot, fits, false);
            ItemStack left = ItemHandlerHelper.insertItem(target, real, false);
            if (!left.isEmpty()) {
                ItemHandlerHelper.insertItem(pack, left, false);
            }
            moved += real.getCount() - left.getCount();
        }
        return moved;
    }
}
