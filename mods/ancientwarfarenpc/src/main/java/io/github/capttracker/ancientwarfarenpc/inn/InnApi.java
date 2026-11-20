/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): inns, meals and bed rent.
 */
package io.github.capttracker.ancientwarfarenpc.inn;

import io.github.capttracker.ancientwarfare.api.IInn;
import io.github.capttracker.ancientwarfare.api.IMarketStall;
import io.github.capttracker.ancientwarfare.api.IMarketStall.TradeResult;
import io.github.capttracker.ancientwarfare.api.IWorkSite;
import io.github.capttracker.ancientwarfare.api.WorksiteRegistry;
import io.github.capttracker.ancientwarfare.owner.Owner;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcInnkeeper;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.item.ItemCoin;
import io.github.capttracker.ancientwarfarenpc.settlement.JobBoard;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementApi;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementData;
import io.github.capttracker.ancientwarfarenpc.trade.MarketApi;
import io.github.capttracker.spectrefactions.api.ActorId;
import io.github.capttracker.spectrefactions.api.FactionsApi;
import io.github.capttracker.spectrefactions.api.Relation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The server side of the inns (rework phase 5): finding the inns of a settlement, the meals the innkeeper serves to the members that gather at the inn
 * ({@link #serveGuest}), the meals players buy ({@link #buyMeal}, priced like the market with the relation multiplier) and the rent of the beds to visiting
 * caravan NPCs ({@link #rentToCaravan}).
 */
public final class InnApi {
    private InnApi() {}

    // ---------------------------------------------------------------- finding

    /** The inns inside the land of the settlement that its owner may use (loaded ones), nearest to the hall first. */
    public static List<IInn> innsOf(ServerLevel level, Settlement settlement) {
        List<IInn> result = new ArrayList<>();
        for (BlockEntity be : WorksiteRegistry.inLevel(level, settlement::contains)) {
            if (be instanceof IInn inn && be instanceof IWorkSite site && JobBoard.mayUse(settlement, site)) {
                result.add(inn);
            }
        }
        BlockPos hall = settlement.getHallPos();
        result.sort(Comparator.comparingDouble(inn -> inn.getInnPos().distSqr(hall)));
        return result;
    }

    /** The inn an innkeeper stands at (the one that lists it as its keeper), if any. */
    public static Optional<IInn> innOf(NpcInnkeeper keeper) {
        BlockPos pos = keeper.getInnPos();
        if (pos == null || !(keeper.level() instanceof ServerLevel level) || !level.isLoaded(pos)) {
            return Optional.empty();
        }
        if (level.getBlockEntity(pos) instanceof IInn inn && inn.getKeeper().filter(id -> id.equals(keeper.getUUID())).isPresent()) {
            return Optional.of(inn);
        }
        return Optional.empty();
    }

    /** The actor that sells at an inn: the owner of the block (the player or their faction), else the owner of the settlement around it. */
    public static Optional<ActorId> sellerOf(ServerLevel level, IInn inn) {
        if (inn instanceof IWorkSite site) {
            Owner owner = site.getOwner();
            if (owner != null && owner != Owner.EMPTY) {
                return Optional.of(FactionsApi.actorOf(owner.getUUID()));
            }
        }
        for (Settlement settlement : SettlementData.get(level).all()) {
            if (settlement.getDimension().equals(level.dimension()) && settlement.contains(inn.getInnPos())) {
                return Optional.of(settlement.getOwnerActor());
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- coins

    private static ItemCoin.CoinMetal metal() {
        ItemCoin.CoinMetal metal = ItemCoin.CoinMetal.byName(SettlementConfig.innMealCoin);
        return metal == null ? ItemCoin.CoinMetal.COPPER : metal;
    }

    /** The price of one meal for the buyer, as a coin stack (empty if the seller refuses to serve the buyer). */
    public static ItemStack quote(ServerLevel level, Player buyer, IInn inn) {
        Relation relation = sellerOf(level, inn).map(actor -> MarketApi.relationOf(buyer, actor)).orElse(Relation.NEUTRAL);
        if (MarketApi.refuses(relation)) {
            return ItemStack.EMPTY;
        }
        return ItemCoin.getCoinStack(metal(), IMarketStall.adjustPrice(SettlementConfig.innMealPrice, MarketApi.priceMultiplier(relation)));
    }

    private static int countOf(IItemHandler handler, ItemStack coin) {
        int have = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (ItemStack.isSameItemSameComponents(stack, coin)) {
                have += stack.getCount();
            }
        }
        return have;
    }

    /** Takes exactly {@code amount} of the coin from the handler, or nothing. */
    private static boolean takeCoins(IItemHandler handler, ItemStack coin, int amount) {
        if (countOf(handler, coin) < amount) {
            return false;
        }
        int left = amount;
        for (int slot = 0; slot < handler.getSlots() && left > 0; slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (ItemStack.isSameItemSameComponents(stack, coin)) {
                left -= handler.extractItem(slot, Math.min(left, stack.getCount()), false).getCount();
            }
        }
        return left == 0;
    }

    // ---------------------------------------------------------------- the player buys a meal

    /**
     * A player buys the meal in a slot of the pantry from the innkeeper. Validated here: the keeper is alive, at the open inn and within reach, the seller is
     * not hostile; the whole deal (coins into the pantry, the meal to the buyer) happens or none of it. Reputation as for any trade.
     */
    public static TradeResult buyMeal(Player buyer, NpcInnkeeper keeper, IInn inn, int pantrySlot) {
        if (!(buyer.level() instanceof ServerLevel level) || !keeper.isAlive() || buyer.distanceToSqr(keeper) > 64.0D) {
            return TradeResult.CLOSED;
        }
        if (!inn.isOpen() || inn.getKeeper().filter(id -> id.equals(keeper.getUUID())).isEmpty()) {
            return TradeResult.CLOSED;
        }
        Optional<ActorId> seller = sellerOf(level, inn);
        Relation relation = seller.map(actor -> MarketApi.relationOf(buyer, actor)).orElse(Relation.NEUTRAL);
        if (MarketApi.refuses(relation)) {
            return TradeResult.REFUSED;
        }
        IItemHandler pantry = inn.getPantry();
        if (pantrySlot < 0 || pantrySlot >= pantry.getSlots() || !IInn.isMeal(pantry.getStackInSlot(pantrySlot))) {
            return TradeResult.NO_SUCH_ENTRY;
        }
        ItemStack price = ItemCoin.getCoinStack(metal(), IMarketStall.adjustPrice(SettlementConfig.innMealPrice, MarketApi.priceMultiplier(relation)));
        if (!ItemHandlerHelper.insertItem(pantry, price.copy(), true).isEmpty()) {
            return TradeResult.STALL_FULL;
        }
        IItemHandler inventory = new PlayerMainInvWrapper(buyer.getInventory());
        if (!takeCoins(inventory, price, price.getCount())) {
            return TradeResult.NO_PAYMENT;
        }
        ItemStack meal = pantry.extractItem(pantrySlot, 1, false);
        if (meal.isEmpty()) {
            ItemHandlerHelper.insertItem(inventory, price.copy(), false);
            return TradeResult.NO_STOCK;
        }
        if (!ItemHandlerHelper.insertItem(inventory, meal.copy(), true).isEmpty()) {
            ItemHandlerHelper.insertItem(pantry, meal, false);
            ItemHandlerHelper.insertItem(inventory, price.copy(), false);
            return TradeResult.NO_ROOM;
        }
        ItemHandlerHelper.insertItem(inventory, meal, false);
        ItemStack left = ItemHandlerHelper.insertItem(pantry, price.copy(), false);
        if (!left.isEmpty()) {
            buyer.drop(left, false); // cannot happen after the simulation; the coins are not lost anyway
        }
        seller.ifPresent(actor -> MarketApi.awardTradeReputation(level, buyer, actor));
        return TradeResult.OK;
    }

    // ---------------------------------------------------------------- serving the members

    /** Takes one meal from the pantry, else from a storage of the settlement; empty if there is none. */
    public static ItemStack takeMeal(ServerLevel level, Settlement settlement, IInn inn) {
        IItemHandler pantry = inn.getPantry();
        ItemStack meal = takeOne(pantry);
        if (!meal.isEmpty()) {
            return meal;
        }
        for (SettlementApi.StorageHandle storage : SettlementApi.allStorages(level, settlement)) {
            meal = takeOne(storage.handler());
            if (!meal.isEmpty()) {
                return meal;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack takeOne(IItemHandler handler) {
        // the meal with the most food in it first, so that the guests are filled up
        int best = -1;
        int bestValue = -1;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (IInn.isMeal(stack)) {
                int value = mealFood(stack);
                if (value > bestValue) {
                    bestValue = value;
                    best = slot;
                }
            }
        }
        return best < 0 ? ItemStack.EMPTY : handler.extractItem(best, 1, false);
    }

    /** The ticks of feeding a meal gives: the food value, or the drink value for a drink. */
    public static int mealFood(ItemStack stack) {
        int value = AWNPCStatics.getFoodValue(stack);
        return value > 0 ? value : SettlementConfig.innDrinkFood;
    }

    /**
     * The innkeeper serves a meal to a member of the settlement: the food meter of the guest rises by the value of the meal (up to its maximum), the
     * settlement notes that the inn served. Returns false if the inn had no meal or the guest is not a member that wants it.
     */
    public static boolean serveGuest(ServerLevel level, Settlement settlement, IInn inn, NpcPlayerOwned guest) {
        if (!guest.isAlive() || !settlement.getId().equals(guest.getSettlementId())) {
            return false;
        }
        ItemStack meal = takeMeal(level, settlement, inn);
        if (meal.isEmpty()) {
            return false;
        }
        guest.setFoodRemaining(Math.min(guest.getMaxFood(), guest.getFoodRemaining() + mealFood(meal)));
        level.playSound(null, guest.getX(), guest.getY(), guest.getZ(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.6F, 1.0F);
        settlement.markMealServed(level.getGameTime());
        return true;
    }

    // ---------------------------------------------------------------- bed rent for visiting caravans

    /**
     * A caravan NPC rents a bed of the open inn of the settlement until the game time {@code until}, paying coins into the pantry (nothing is charged
     * for a bed it already holds, and a bed is only rented if the pantry has room for the coins). Returns the bed, or empty if the inn is closed, has no
     * free bed or cannot take the rent.
     */
    public static Optional<BlockPos> rentToCaravan(ServerLevel level, Settlement settlement, UUID guest, long until) {
        long now = level.getGameTime();
        for (IInn inn : innsOf(level, settlement)) {
            Optional<BlockPos> held = inn.getRentedBed(guest, now);
            if (held.isPresent()) {
                return held;
            }
            if (!inn.isOpen() || inn.freeBeds(now) <= 0) {
                continue;
            }
            int rent = SettlementConfig.innBedRent;
            ItemStack coins = ItemCoin.getCoinStack(metal(), Math.max(1, rent));
            if (rent > 0 && !ItemHandlerHelper.insertItem(inn.getPantry(), coins.copy(), true).isEmpty()) {
                continue;
            }
            Optional<BlockPos> bed = inn.rentBed(guest, now, until);
            if (bed.isPresent() && rent > 0) {
                ItemHandlerHelper.insertItem(inn.getPantry(), coins, false);
            }
            if (bed.isPresent()) {
                return bed;
            }
        }
        return Optional.empty();
    }
}
