/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/trade/Trade.
 */
package io.github.capttracker.ancientwarfarenpc.trade;

import io.github.capttracker.ancientwarfare.api.IMarketStall;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfarenpc.item.ItemCoin;
import io.github.capttracker.ancientwarfarenpc.orders.OrderCodecs;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The base of both trade systems: up to {@link #size()} (9) input stacks the customer pays and 9 output stacks the customer receives.
 * {@link POTrade} (player owned traders) moves the items through the trader's stock, {@link FactionTrade} (faction traders) has no stock.
 */
public abstract class Trade {
    protected NonNullList<ItemStack> input = NonNullList.withSize(size(), ItemStack.EMPTY);
    protected NonNullList<ItemStack> output = NonNullList.withSize(size(), ItemStack.EMPTY);

    /** Rework phase 5: the factor for the coins this trade asks for, set around one purchase by the faction trade (1 = the listed price). */
    protected double priceMultiplier = 1.0D;

    /** A stack the customer pays: coins are scaled by the price multiplier, anything else is as listed. */
    protected ItemStack priced(ItemStack stack) {
        if (priceMultiplier == 1.0D || stack.isEmpty() || !(stack.getItem() instanceof ItemCoin)) {
            return stack;
        }
        return stack.copyWithCount(IMarketStall.adjustPrice(stack.getCount(), priceMultiplier));
    }

    public int size() {
        return 9;
    }

    public ItemStack getInputStack(int index) {
        return input.get(index);
    }

    public List<ItemStack> getInput() {
        return input;
    }

    public ItemStack getOutputStack(int index) {
        return output.get(index);
    }

    public List<ItemStack> getOutput() {
        return output;
    }

    public void setInputStack(int index, ItemStack stack) {
        input.set(index, stack);
    }

    public void setOutputStack(int index, ItemStack stack) {
        output.set(index, stack);
    }

    /**
     * If the customer has all the input items, does the trade ({@link #doTrade}).
     *
     * @param storage where the paid items go and the output comes from; null if the trade has no stock
     * @return true if the trade was done
     */
    public boolean performTrade(Player player, @Nullable IItemHandler storage) {
        NonNullList<ItemStack> list = NonNullList.create();
        for (ItemStack temp : input) {
            if (!temp.isEmpty()) {
                list.add(priced(temp).copy());
            }
        }
        list = InventoryTools.compactStackList(list);
        for (ItemStack stack : list) {
            if (!hasEnoughOf(player, stack)) {
                return false;
            }
        }
        doTrade(player, storage);
        return true;
    }

    private static IItemHandler getPlayerInventory(Player player) {
        return new PlayerMainInvWrapper(player.getInventory());
    }

    private boolean hasEnoughOf(Player player, ItemStack stack) {
        return InventoryTools.getCountOf(getPlayerInventory(player), stack) >= stack.getCount();
    }

    /**
     * Takes the input items from the player's inventory and puts them into the storage, takes the output items from the storage (or makes
     * them, without storage) and merges them into the player's inventory, dropping what does not fit at the player (on the server).
     */
    protected void doTrade(Player player, @Nullable IItemHandler storage) {
        IItemHandler playerInventory = getPlayerInventory(player);
        for (ItemStack listed : input) {
            if (listed.isEmpty()) {
                continue;
            }
            ItemStack inputStack = priced(listed);
            ItemStack result = InventoryTools.removeItems(playerInventory, inputStack, inputStack.getCount());
            if (!result.isEmpty() && storage != null) {
                InventoryTools.mergeItemStack(storage, result);
            }
        }
        for (ItemStack outputStack : output) {
            if (outputStack.isEmpty()) {
                continue;
            }
            if (storage != null) {
                outputStack = InventoryTools.removeItems(storage, outputStack, outputStack.getCount());
            } else {
                outputStack = outputStack.copy();
            }
            outputStack = InventoryTools.mergeItemStack(playerInventory, outputStack);
            if (!outputStack.isEmpty() && !player.level().isClientSide) {
                Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), outputStack);
            }
        }
    }

    /** The non-empty input stacks with their slots (for the codecs). */
    protected List<OrderCodecs.SlotStack> inputSlots() {
        return OrderCodecs.toSlots(input);
    }

    protected List<OrderCodecs.SlotStack> outputSlots() {
        return OrderCodecs.toSlots(output);
    }

    /** Replaces the stacks with the ones read by the codecs. */
    protected void setSlots(List<OrderCodecs.SlotStack> inputSlots, List<OrderCodecs.SlotStack> outputSlots) {
        input = NonNullList.withSize(size(), ItemStack.EMPTY);
        output = NonNullList.withSize(size(), ItemStack.EMPTY);
        OrderCodecs.fromSlots(inputSlots, input);
        OrderCodecs.fromSlots(outputSlots, output);
    }

    /** True if there is something to pay and something to get. */
    public boolean isValid() {
        return input.stream().anyMatch(s -> !s.isEmpty()) && output.stream().anyMatch(s -> !s.isEmpty());
    }

    protected boolean sameStacks(Trade other) {
        return OrderCodecs.stacksEqual(input, other.input) && OrderCodecs.stacksEqual(output, other.output);
    }

    protected int stacksHash() {
        return 31 * OrderCodecs.stacksHash(input) + OrderCodecs.stacksHash(output);
    }
}
