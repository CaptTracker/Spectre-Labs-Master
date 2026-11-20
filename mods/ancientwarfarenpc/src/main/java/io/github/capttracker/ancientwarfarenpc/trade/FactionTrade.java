/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/trade/FactionTrade.
 */
package io.github.capttracker.ancientwarfarenpc.trade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.capttracker.ancientwarfarenpc.orders.OrderCodecs;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * A trade of a faction trader: it has no stock. {@code maxAvailable} purchases can be made before the trade is sold out, and one purchase
 * is added back every {@code refillFrequency} ticks (0 = the trade is restocked instantly and never sells out, negative = never refilled).
 * The refill is done lazily by {@link #updateTrade} when the trade view is opened.
 */
public class FactionTrade extends Trade {
    /** The tags of 1.12.2: {@code refillFrequency, refillTime, maxAvailable, currentAvailable, inputItems, outputItems}. */
    public static final Codec<FactionTrade> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("refillFrequency", 0).forGetter(FactionTrade::getRefillFrequency),
            Codec.LONG.optionalFieldOf("refillTime", -1L).forGetter(FactionTrade::getRefillTime),
            Codec.INT.optionalFieldOf("maxAvailable", 0).forGetter(FactionTrade::getMaxAvailable),
            Codec.INT.optionalFieldOf("currentAvailable", 0).forGetter(FactionTrade::getCurrentAvailable),
            OrderCodecs.SLOT_STACKS.optionalFieldOf("inputItems", List.of()).forGetter(Trade::inputSlots),
            OrderCodecs.SLOT_STACKS.optionalFieldOf("outputItems", List.of()).forGetter(Trade::outputSlots)
    ).apply(instance, FactionTrade::fromData));

    private int refillFrequency;
    private long refillTime = -1;
    private int maxAvailable;
    private int currentAvailable;
    //TODO (from the original) add a minLevel stat -- used to determine if the trade should be available

    public FactionTrade() {
        refillFrequency = 20 * 60 * 5;//five minutes per item refilled
        maxAvailable = 1;
        currentAvailable = 1;
    }

    private static FactionTrade fromData(int refillFrequency, long refillTime, int maxAvailable, int currentAvailable,
                                         List<OrderCodecs.SlotStack> inputSlots, List<OrderCodecs.SlotStack> outputSlots) {
        FactionTrade trade = new FactionTrade();
        trade.refillFrequency = refillFrequency;
        trade.refillTime = refillTime;
        trade.maxAvailable = maxAvailable;
        trade.currentAvailable = currentAvailable;
        trade.setSlots(inputSlots, outputSlots);
        return trade;
    }

    public FactionTrade copy() {
        return fromData(refillFrequency, refillTime, maxAvailable, currentAvailable, inputSlots(), outputSlots());
    }

    /** True if any input or output slot holds an item. */
    public boolean hasItems() {
        for (int i = 0; i < size(); i++) {
            if (!getInputStack(i).isEmpty() || !getOutputStack(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public int getRefillFrequency() {
        return refillFrequency;
    }

    public int getMaxAvailable() {
        return maxAvailable;
    }

    public int getCurrentAvailable() {
        return currentAvailable;
    }

    public void setRefillFrequency(int refill) {
        refillFrequency = refill;
    }

    /** Sets the maximum and fills the trade up to it. */
    public void setMaxAvailable(int max) {
        maxAvailable = max;
        currentAvailable = max;
    }

    /** The game time of the next refill (-1 before the first update, 0 when the trade is full). */
    public long getRefillTime() {
        return refillTime;
    }

    /**
     * Refills the trade for the time that has passed. Called by the trade list on the server before the trade view is opened.
     *
     * @param totalWorldTime the game time
     */
    public void updateTrade(long totalWorldTime) {
        if (refillTime == -1) {
            refillTime = totalWorldTime + refillFrequency;
        }
        if (refillFrequency > 0 && refillTime > 0 && refillTime <= totalWorldTime) {//update per freq period
            long timeDiff = totalWorldTime - refillTime;
            while (currentAvailable < maxAvailable && timeDiff >= 0) {
                timeDiff -= refillFrequency;
                currentAvailable++;
            }
            refillTime = currentAvailable < maxAvailable ? totalWorldTime + refillFrequency : 0;
        } else if (refillFrequency == 0) {//full refill automatically if frequency==0
            currentAvailable = maxAvailable;
        }//don't refill if frequency<0
    }

    /** The trade as a customer with this price multiplier sees it: the coins it asks for are scaled (the stock and the refill are copied). */
    public FactionTrade pricedCopy(double multiplier) {
        FactionTrade copy = copy();
        copy.priceMultiplier = multiplier;
        for (int i = 0; i < copy.size(); i++) {
            copy.input.set(i, copy.priced(copy.input.get(i)).copy());
        }
        copy.priceMultiplier = 1.0D;
        return copy;
    }

    /** Does the trade with the coins it asks for scaled by the multiplier (rework phase 5). */
    public boolean performTrade(Player player, double multiplier) {
        priceMultiplier = multiplier;
        try {
            return performTrade(player, null);
        } finally {
            priceMultiplier = 1.0D;
        }
    }

    @Override
    public boolean performTrade(Player player, @Nullable IItemHandler storage) {
        return currentAvailable > 0 && super.performTrade(player, null);
    }

    @Override
    protected void doTrade(Player player, @Nullable IItemHandler storage) {
        if (refillFrequency != 0) {
            currentAvailable--;
            refillTime = player.level().getGameTime() + refillFrequency;
        }//0 denotes instant restock, no reason to decrease qty if it will just be instantly restocked when the view is opened next
        super.doTrade(player, storage);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FactionTrade other && refillFrequency == other.refillFrequency && refillTime == other.refillTime
                && maxAvailable == other.maxAvailable && currentAvailable == other.currentAvailable && sameStacks(other);
    }

    @Override
    public int hashCode() {
        return Objects.hash(refillFrequency, refillTime, maxAvailable, currentAvailable, stacksHash());
    }
}
