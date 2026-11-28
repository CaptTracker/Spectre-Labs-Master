/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (third round, mod integrations): an innkeeper cooks in a kitchen of the settlement (Farmer's Delight).
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfare.api.IInn;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.compat.KitchenCompat;
import io.github.capttracker.ancientwarfarenpc.compat.ModCompat;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcInnkeeper;
import io.github.capttracker.ancientwarfarenpc.npccommand.NpcCommand.Command;
import io.github.capttracker.ancientwarfarenpc.settlement.Activity;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementApi;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementNeed;
import io.github.capttracker.ancientwarfarenpc.settlement.building.KitchenSites;
import io.github.capttracker.ancientwarfarenpc.skill.Skill;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The cook (an innkeeper in the work hours): it cooks in a kitchen of the settlement ({@link KitchenSites}, no extra block) and keeps the settlement in meals.
 *
 * <p>Every 5 seconds it looks whether the settlement wants meals: fewer meals in the storages and the pantries of the inns than {@code cook_meal_stock_per_member}
 * for each member, or a food need on the list of needs of the hall. If so it picks a cooking recipe whose ingredients the storages hold (the one that gives the
 * most food, less the more of that meal there already is, so the meals vary; with a food need the one with the most food), takes the ingredients and the
 * container (a bowl, a bottle) from the storage, walks to a free heated pot of a kitchen and puts them in. The pot cooks by itself; the cook waits at it, takes the
 * meal and puts it into a storage that has room. If the settlement does not want meals and a kitchen has a cutting board, it may cut a food (a pie into slices)
 * on it with a knife from the storage. Each finished dish gives crafting experience. The cook leaves at the end of the work hours, in an alarm, when hungry, or
 * when it gets a command, and puts what it carries back into a storage.
 *
 * <p>The recipes and the stations come from {@link KitchenCompat}, so this goal names no class of the other mod.
 */
public class NpcAICook extends NpcAI<NpcInnkeeper> {
    private static final int SEARCH_INTERVAL = 100;
    private static final double REACH_SQ = 6.25D;
    private static final int STUCK_TICKS = 900;
    private static final int WAIT_TICKS = 1800;

    private enum Phase { FETCH, STATION, WAIT, STORE }

    /** One thing to take from a storage: which one, the slot, and what (a single item). */
    private record Take(int storage, int slot, ItemStack stack, boolean container) {}

    /** The work of one round: a dish in a pot, a cut on a board, or just the meal that is waiting in a pot. */
    private static final class Job {
        BlockPos station;
        KitchenCompat.Kind kind;
        List<Take> takes = new ArrayList<>();
        ItemStack container = ItemStack.EMPTY;
        KitchenCompat.Dish dish;
        KitchenCompat.Cut cut;
        boolean collectOnly;
    }

    private long nextSearch = 0;
    @Nullable
    private Job job;
    private Phase phase = Phase.FETCH;
    private final List<ItemStack> carried = new ArrayList<>();
    /** The container (a bowl, a bottle) of the dish, carried apart from the ingredients. */
    private ItemStack carriedContainer = ItemStack.EMPTY;
    private boolean completed;
    private List<SettlementApi.StorageHandle> storages = List.of();
    private int phaseTicks;
    private int waitTicks;

    public NpcAICook(NpcInnkeeper npc) {
        super(npc);
        setMutexBits(MOVE);
    }

    private boolean mayCook() {
        return !npc.isAlarmed && npc.getTarget() == null && !npc.isPassenger() && npc.getActivity() == Activity.WORK && npc.getCurrentCommand() == Command.NONE
                && npc.getFoodFraction() > 0.3D && npc.level() instanceof ServerLevel && KitchenSites.enabled();
    }

    @Override
    public boolean canUse() {
        if (!super.canUse() || npc.tickCount < nextSearch || !mayCook()) {
            return false;
        }
        nextSearch = npc.tickCount + SEARCH_INTERVAL;
        ServerLevel level = (ServerLevel) npc.level();
        Settlement settlement = npc.getSettlement().filter(s -> s.getDimension().equals(level.dimension())).orElse(null);
        if (settlement == null || KitchenSites.of(settlement).isEmpty()) {
            return false;
        }
        storages = SettlementApi.allStorages(level, settlement);
        job = plan(level, settlement);
        return job != null;
    }

    @Override
    public boolean canContinueToUse() {
        return super.canUse() && job != null && mayCook();
    }

    @Override
    public void start() {
        carried.clear();
        carriedContainer = ItemStack.EMPTY;
        completed = false;
        phaseTicks = 0;
        waitTicks = 0;
        Job started = job;
        phase = started != null && (started.collectOnly || started.takes.isEmpty()) ? Phase.STATION : Phase.FETCH;
        npc.addAITask(TASK_WORK);
    }

    // ---------------------------------------------------------------- planning

    /** The meals (items that are the result of a cooking recipe) in the storages and the pantries of the inns, by item. */
    private Map<Item, Integer> mealStock(ServerLevel level, Settlement settlement, Set<Item> mealItems) {
        Map<Item, Integer> stock = new HashMap<>();
        List<IItemHandler> handlers = new ArrayList<>();
        for (SettlementApi.StorageHandle storage : storages) {
            handlers.add(storage.handler());
        }
        for (IInn inn : io.github.capttracker.ancientwarfarenpc.inn.InnApi.innsOf(level, settlement)) {
            handlers.add(inn.getPantry());
        }
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && mealItems.contains(stack.getItem())) {
                    stock.merge(stack.getItem(), stack.getCount(), Integer::sum);
                }
            }
        }
        return stock;
    }

    private boolean foodNeeded(Settlement settlement) {
        for (SettlementNeed need : SettlementApi.getNeeds(settlement)) {
            if (need.category == SettlementNeed.Category.FOOD) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private Job plan(ServerLevel level, Settlement settlement) {
        KitchenCompat kitchen = ModCompat.KITCHEN.get();
        List<BlockPos> pots = new ArrayList<>();
        List<BlockPos> boards = new ArrayList<>();
        for (KitchenSites.Kitchen room : KitchenSites.of(settlement)) {
            pots.addAll(room.pots());
            boards.addAll(room.boards());
        }
        // a meal waiting in a pot is collected first
        BlockPos freePot = null;
        for (BlockPos pos : pots) {
            KitchenCompat.Pot pot = kitchen.pot(level, pos);
            if (pot == null) {
                continue;
            }
            if (!pot.output().isEmpty()) {
                Job collect = new Job();
                collect.station = pos;
                collect.kind = KitchenCompat.Kind.POT;
                collect.collectOnly = true;
                return collect;
            }
            if (freePot == null && pot.heated() && !pot.busy()) {
                freePot = pos;
            }
        }
        List<KitchenCompat.Dish> dishes = new ArrayList<>();
        Set<Item> mealItems = new HashSet<>();
        for (KitchenCompat.Dish dish : kitchen.dishes(level)) {
            if (AWNPCStatics.getFoodValue(dish.result()) > 0) {
                dishes.add(dish);
                mealItems.add(dish.result().getItem());
            }
        }
        Map<Item, Integer> stock = mealStock(level, settlement, mealItems);
        int have = stock.values().stream().mapToInt(Integer::intValue).sum();
        int want = (int) Math.ceil(settlement.getMembers().size() * SettlementConfig.fdMealStockPerMember);
        boolean hungry = foodNeeded(settlement);
        boolean wantMeals = have < want || hungry;
        if (wantMeals && freePot != null) {
            Job dishJob = planDish(kitchen, dishes, stock, hungry, freePot);
            if (dishJob != null) {
                return dishJob;
            }
        }
        if (!wantMeals && SettlementConfig.fdCookCutting && !boards.isEmpty()) {
            return planCut(level, kitchen, boards, want);
        }
        return null;
    }

    /** Remembers which items of the storages are taken for a plan, so that one stack is not used twice. */
    private final class Allocation {
        private final Map<Long, Integer> used = new HashMap<>();

        @Nullable
        Take find(Ingredient ingredient) {
            for (int s = 0; s < storages.size(); s++) {
                IItemHandler handler = storages.get(s).handler();
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    long key = ((long) s << 32) | slot;
                    if (!stack.isEmpty() && ingredient.test(stack) && stack.getCount() - used.getOrDefault(key, 0) > 0) {
                        used.merge(key, 1, Integer::sum);
                        return new Take(s, slot, stack.copyWithCount(1), false);
                    }
                }
            }
            return null;
        }

        @Nullable
        Take findItem(ItemStack wanted) {
            return find(Ingredient.of(wanted.getItem()));
        }
    }

    @Nullable
    private Job planDish(KitchenCompat kitchen, List<KitchenCompat.Dish> dishes, Map<Item, Integer> stock, boolean hungry, BlockPos pot) {
        Job best = null;
        double bestScore = 0.0D;
        for (KitchenCompat.Dish dish : dishes) {
            Allocation allocation = new Allocation();
            List<Take> takes = new ArrayList<>();
            boolean complete = true;
            for (Ingredient ingredient : dish.ingredients()) {
                Take take = allocation.find(ingredient);
                if (take == null) {
                    complete = false;
                    break;
                }
                takes.add(take);
            }
            if (!complete) {
                continue;
            }
            if (!dish.container().isEmpty()) {
                Take container = allocation.findItem(dish.container());
                if (container == null) {
                    continue;
                }
                takes.add(new Take(container.storage(), container.slot(), container.stack(), true));
            }
            double value = (double) AWNPCStatics.getFoodValue(dish.result()) * dish.result().getCount();
            double score = hungry ? value : value / (1.0D + stock.getOrDefault(dish.result().getItem(), 0));
            if (score > bestScore) {
                bestScore = score;
                Job planned = new Job();
                planned.station = pot;
                planned.kind = KitchenCompat.Kind.POT;
                planned.dish = dish;
                planned.takes = takes;
                planned.container = dish.container().copy();
                best = planned;
            }
        }
        return best;
    }

    @Nullable
    private Job planCut(ServerLevel level, KitchenCompat kitchen, List<BlockPos> boards, int want) {
        BlockPos board = null;
        for (BlockPos pos : boards) {
            if (kitchen.boardFree(level, pos)) {
                board = pos;
                break;
            }
        }
        if (board == null) {
            return null;
        }
        for (KitchenCompat.Cut cut : kitchen.cuts(level)) {
            int inValue = 0;
            int outValue = 0;
            for (ItemStack result : cut.results()) {
                outValue += AWNPCStatics.getFoodValue(result) * result.getCount();
            }
            Allocation allocation = new Allocation();
            Take input = allocation.find(cut.input());
            if (input == null) {
                continue;
            }
            inValue = AWNPCStatics.getFoodValue(input.stack());
            if (inValue <= 0 || outValue < inValue) {
                continue; // only a food that stays as much food after the cut (a pie into its slices)
            }
            Take tool = allocation.find(cut.tool());
            if (tool == null) {
                continue;
            }
            Job planned = new Job();
            planned.station = board;
            planned.kind = KitchenCompat.Kind.BOARD;
            planned.cut = cut;
            planned.takes = new ArrayList<>(List.of(input, tool));
            return planned;
        }
        return null;
    }

    // ---------------------------------------------------------------- running

    private boolean walkTo(BlockPos pos) {
        double distance = npc.distanceToSqr(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        if (distance > REACH_SQ) {
            npc.addAITask(TASK_MOVE);
            moveToPosition(pos.getX(), pos.getY(), pos.getZ(), distance);
            return false;
        }
        npc.getNavigation().stop();
        npc.removeAITask(TASK_MOVE);
        npc.getLookControl().setLookAt(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        return true;
    }

    private void giveUp() {
        job = null;
    }

    @Override
    public void tick() {
        Job current = job;
        if (current == null) {
            return;
        }
        ServerLevel level = (ServerLevel) npc.level();
        if (++phaseTicks > (phase == Phase.WAIT ? WAIT_TICKS : STUCK_TICKS)) {
            giveUp(); // stuck on the way, or the pot never finished
            return;
        }
        switch (phase) {
            case FETCH -> fetch(level, current);
            case STATION -> atStation(level, current);
            case WAIT -> waitForPot(level, current);
            case STORE -> store(level);
        }
    }

    private void goTo(Phase next) {
        phase = next;
        phaseTicks = 0;
    }

    /** Takes the planned items from the storages, one storage at a time, nearest first. */
    private void fetch(ServerLevel level, Job current) {
        npc.setDoing("work.cook_fetch", current.station, Component.empty());
        if (current.takes.isEmpty()) {
            goTo(Phase.STATION);
            return;
        }
        Take first = current.takes.get(0);
        int index = first.storage();
        if (index >= storages.size()) {
            giveUp();
            return;
        }
        BlockPos at = storages.get(index).pos();
        if (!walkTo(at)) {
            return;
        }
        IItemHandler handler = storages.get(index).handler();
        List<Take> rest = new ArrayList<>();
        for (Take take : current.takes) {
            if (take.storage() != index) {
                rest.add(take);
                continue;
            }
            ItemStack inSlot = handler.getStackInSlot(take.slot());
            if (!ItemStack.isSameItemSameComponents(inSlot, take.stack())) {
                giveUp(); // somebody else took it
                return;
            }
            ItemStack taken = handler.extractItem(take.slot(), 1, false);
            if (taken.isEmpty()) {
                giveUp();
                return;
            }
            if (take.container()) {
                carriedContainer = taken;
            } else {
                carried.add(taken);
            }
        }
        current.takes = rest;
        npc.swing(InteractionHand.MAIN_HAND);
        phaseTicks = 0;
        if (rest.isEmpty()) {
            goTo(Phase.STATION);
        }
    }

    private void atStation(ServerLevel level, Job current) {
        npc.setDoing("work.cook", current.station, Component.empty());
        if (!walkTo(current.station)) {
            return;
        }
        KitchenCompat kitchen = ModCompat.KITCHEN.get();
        if (current.collectOnly) {
            collect(level, current);
            return;
        }
        if (current.kind == KitchenCompat.Kind.POT) {
            if (kitchen.fillPot(level, current.station, carried, carriedContainer)) {
                carried.clear();
                carriedContainer = ItemStack.EMPTY;
                npc.swing(InteractionHand.MAIN_HAND);
                waitTicks = 0;
                goTo(Phase.WAIT);
            } else {
                giveUp();
            }
            return;
        }
        // the board: carried is the item to cut, then the tool
        if (carried.size() >= 2) {
            ItemStack input = carried.get(0);
            ItemStack tool = carried.get(1);
            List<ItemStack> results = kitchen.cut(level, current.station, input, tool);
            if (results == null) {
                giveUp();
                return;
            }
            input.shrink(1);
            carried.removeIf(ItemStack::isEmpty);
            carried.addAll(results);
            npc.swing(InteractionHand.MAIN_HAND);
            npc.gainSkillXp(Skill.CRAFTING, SettlementConfig.skillXpWork);
            goTo(Phase.STORE);
        } else {
            giveUp();
        }
    }

    private void waitForPot(ServerLevel level, Job current) {
        npc.setDoing("work.cook", current.station, Component.empty());
        if (!walkTo(current.station)) {
            return;
        }
        if (waitTicks++ % 20 != 0) {
            return;
        }
        KitchenCompat.Pot pot = ModCompat.KITCHEN.get().pot(level, current.station);
        if (pot == null) {
            giveUp();
            return;
        }
        if (!pot.output().isEmpty()) {
            collect(level, current);
        } else if (!pot.busy() && waitTicks > 60) {
            giveUp(); // nothing is cooking (the heat went out, or no recipe fits what was put in)
        }
    }

    private void collect(ServerLevel level, Job current) {
        ItemStack meal = ModCompat.KITCHEN.get().takePotOutput(level, current.station);
        if (meal.isEmpty()) {
            giveUp();
            return;
        }
        carried.add(meal);
        if (!current.collectOnly) {
            npc.gainSkillXp(Skill.CRAFTING, SettlementConfig.skillXpWork * 2);
        }
        level.playSound(null, npc.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.4F, 1.0F);
        goTo(Phase.STORE);
    }

    /** Puts what is carried into the nearest storage that has room (a meal into the pantry of an inn, if it is closer). */
    private void store(ServerLevel level) {
        flushContainer();
        carried.removeIf(ItemStack::isEmpty);
        if (carried.isEmpty()) {
            finish();
            return;
        }
        npc.setDoing("work.cook_fetch", npc.blockPosition(), Component.empty());
        SettlementApi.StorageHandle target = null;
        double best = Double.MAX_VALUE;
        for (SettlementApi.StorageHandle storage : storages) {
            boolean room = false;
            for (ItemStack stack : carried) {
                if (ItemHandlerHelper.insertItem(storage.handler(), stack.copy(), true).getCount() < stack.getCount()) {
                    room = true;
                    break;
                }
            }
            double distance = npc.distanceToSqr(storage.pos().getX() + 0.5D, storage.pos().getY(), storage.pos().getZ() + 0.5D);
            if (room && distance < best) {
                best = distance;
                target = storage;
            }
        }
        if (target == null) {
            dropCarried(); // no storage with room: the meal is put down at the feet of the cook, as any worker does
            finish();
            return;
        }
        if (!walkTo(target.pos())) {
            return;
        }
        for (int i = 0; i < carried.size(); i++) {
            carried.set(i, ItemHandlerHelper.insertItem(target.handler(), carried.get(i), false));
        }
        carried.removeIf(ItemStack::isEmpty);
        npc.swing(InteractionHand.MAIN_HAND);
        if (carried.isEmpty()) {
            finish();
        }
    }

    /** The container joins the rest of what is carried (when the round ends before it is used). */
    private void flushContainer() {
        if (!carriedContainer.isEmpty()) {
            carried.add(carriedContainer);
            carriedContainer = ItemStack.EMPTY;
        }
    }

    private void finish() {
        completed = true;
        job = null;
        nextSearch = npc.tickCount + 20; // look at once for the next round
    }

    private void dropCarried() {
        for (ItemStack stack : carried) {
            if (!stack.isEmpty()) {
                npc.spawnAtLocation(stack);
            }
        }
        carried.clear();
    }

    @Override
    public void stop() {
        flushContainer();
        if (!carried.isEmpty() && npc.level() instanceof ServerLevel level) {
            // whatever is still carried goes back into a storage, else it is put down
            Settlement settlement = npc.getSettlement().orElse(null);
            if (settlement != null) {
                for (SettlementApi.StorageHandle storage : SettlementApi.allStorages(level, settlement)) {
                    for (int i = 0; i < carried.size(); i++) {
                        carried.set(i, ItemHandlerHelper.insertItem(storage.handler(), carried.get(i), false));
                    }
                    carried.removeIf(ItemStack::isEmpty);
                    if (carried.isEmpty()) {
                        break;
                    }
                }
            }
            dropCarried();
        }
        job = null;
        npc.getNavigation().stop();
        npc.removeAITask(TASK_WORK + TASK_MOVE);
        nextSearch = npc.tickCount + (completed ? 20 : SEARCH_INTERVAL);
    }
}
