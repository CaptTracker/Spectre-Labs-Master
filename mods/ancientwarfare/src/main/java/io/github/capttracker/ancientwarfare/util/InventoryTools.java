/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/util/InventoryTools.
 */
package io.github.capttracker.ancientwarfare.util;

import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfare.inventory.ItemHashEntry;
import io.github.capttracker.ancientwarfare.inventory.ItemQuantityMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

import javax.annotation.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * The workhorse for {@link IItemHandler}s: insert, remove, transfer, count, clone, compact and merge
 * stacks, plus drop and loot helpers.
 *
 * <p>Differences from 1.12.2: stack equality is data-component based (there is no damage value or
 * NBT tag any more), wildcard ore-dictionary matching is gone (use tags in the caller),
 * {@code IInventory} became {@link Container}, and the capability helper takes a level and position.
 */
public final class InventoryTools {
    private static final Logger LOGGER = LogUtils.getLogger();

    private InventoryTools() {}

    // ---- stack equality ----

    /** A copy of the stack with the damage component reset, or the stack itself if it needs no change. */
    private static ItemStack withoutDamage(ItemStack stack) {
        if (stack.isDamageableItem() && stack.getDamageValue() != 0) {
            ItemStack copy = stack.copyWithCount(1);
            copy.setDamageValue(0);
            return copy;
        }
        return stack;
    }

    /** Same item and same data components, ignoring the damage value and the count. */
    public static boolean sameItemIgnoringDamage(ItemStack stack1, ItemStack stack2) {
        if (stack1 == stack2) {
            return true;
        }
        if (stack1.getItem() != stack2.getItem()) {
            return false;
        }
        return ItemStack.isSameItemSameComponents(withoutDamage(stack1), withoutDamage(stack2));
    }

    /**
     * Validates that stacks are the same item and components (damage ignored), ignores quantity.
     * Replaces 1.12.2's ore-dictionary and tag based {@code doItemStacksMatchRelaxed}.
     */
    public static boolean doItemStacksMatchRelaxed(ItemStack stack1, ItemStack stack2) {
        return sameItemIgnoringDamage(stack1, stack2);
    }

    public static boolean doItemStacksMatch(ItemStack stackA, ItemStack stackB) {
        return doItemStacksMatch(stackA, stackB, false, false);
    }

    /**
     * @param ignoreDamage ignore the damage value of damageable items
     * @param ignoreNBT    ignore every data component other than the damage value (was the NBT tag)
     */
    public static boolean doItemStacksMatch(ItemStack stackA, ItemStack stackB, boolean ignoreDamage, boolean ignoreNBT) {
        if (stackA.isEmpty() && stackB.isEmpty()) {
            return true;
        } else if (stackA.getItem() != stackB.getItem()) {
            return false;
        } else if (!ignoreDamage && stackA.isDamageableItem() && stackA.getDamageValue() != stackB.getDamageValue()) {
            return false;
        } else if (ignoreNBT) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(withoutDamage(stackA), withoutDamage(stackB));
    }

    // ---- insertion ----

    public static boolean canInventoryHold(IItemHandler handler, ItemStack stack) {
        return insertItem(handler, stack, true).isEmpty();
    }

    public static boolean canInventoryHold(IItemHandler handler, List<ItemStack> stacks) {
        return insertItems(handler, stacks, true).isEmpty();
    }

    /** @return the stacks that did not fit */
    public static List<ItemStack> insertItems(IItemHandler handler, List<ItemStack> stacks, boolean simulate) {
        NonNullList<ItemStack> remainingItems = NonNullList.create();
        if (simulate) {
            handler = cloneItemHandler(handler);
        }
        for (ItemStack stack : stacks) {
            ItemStack remainingItem = insertItem(handler, stack, false);
            if (!remainingItem.isEmpty()) {
                remainingItems.add(remainingItem);
            }
        }
        return remainingItems;
    }

    /** A detached copy of the handler's contents that still respects what the original would accept per slot. */
    public static IItemHandlerModifiable cloneItemHandler(IItemHandler handler) {
        ItemStackHandler copy = new ItemStackHandler(handler.getSlots()) {
            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return canInsert(slot, stack) ? super.insertItem(slot, stack.copy(), simulate) : stack;
            }

            private boolean canInsert(int slot, ItemStack stack) {
                ItemStack remainingStack = handler.insertItem(slot, stack, true);
                return remainingStack.isEmpty() || remainingStack.getCount() != stack.getCount();
            }
        };

        for (int slot = 0; slot < handler.getSlots(); slot++) {
            copy.setStackInSlot(slot, handler.getStackInSlot(slot).copy());
        }
        return copy;
    }

    public static ItemStack insertItem(IItemHandler handler, ItemStack stack) {
        return insertItem(handler, stack, false);
    }

    /** @return the part of the stack that did not fit */
    public static ItemStack insertItem(IItemHandler handler, ItemStack stack, boolean simulate) {
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            remaining = handler.insertItem(slot, remaining, simulate);
            if (remaining.isEmpty()) {
                break;
            }
        }
        return remaining;
    }

    /**
     * Puts the stack on the player's cursor (and tells the client), after optionally merging it into the
     * player's inventory first (shift click).
     */
    public static void updateCursorItem(ServerPlayer player, ItemStack stack, boolean shiftClick) {
        if (!stack.isEmpty()) {
            if (shiftClick) {
                stack = mergeItemStack(new PlayerMainInvWrapper(player.getInventory()), stack);
            }
            if (!stack.isEmpty()) {
                player.containerMenu.setCarried(stack);
                player.containerMenu.broadcastChanges();
            }
        }
    }

    /** Merges the stack into the handler, topping up existing stacks first. Consumes what it places. */
    public static ItemStack mergeItemStack(IItemHandler handler, ItemStack stack) {
        ItemStack ret = stack;
        if (stack.isStackable()) {
            for (int i = 0; i < handler.getSlots(); i++) {
                if (stack.isEmpty()) {
                    break;
                }

                ItemStack slotStack = handler.getStackInSlot(i);

                if (doItemStacksMatch(slotStack, stack)) {
                    int maxSize = Math.min(handler.getSlotLimit(i), stack.getMaxStackSize());

                    int change = Math.min(maxSize - slotStack.getCount(), stack.getCount());

                    handler.insertItem(i, stack, false);
                    stack.shrink(change);
                }
            }
        }

        if (!stack.isEmpty()) {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack slotStack = handler.getStackInSlot(i);

                if (slotStack.isEmpty() && handler.insertItem(i, stack, true).isEmpty()) {
                    int slotLimit = handler.getSlotLimit(i);
                    if (stack.getCount() > slotLimit) {
                        handler.insertItem(i, stack.split(slotLimit), false);
                    } else {
                        handler.insertItem(i, stack.split(stack.getCount()), false);
                        ret = ItemStack.EMPTY;
                        break;
                    }
                }
            }
        }

        return ret;
    }

    // ---- removal and transfer ----

    /**
     * Attempts to remove filter * quantity from the handler. Will only remove and return up to
     * {@code filter.getMaxStackSize()} items, regardless of how many are requested.
     *
     * @return the removed items, or {@link ItemStack#EMPTY} if none were removed
     */
    public static ItemStack removeItems(IItemHandler handler, ItemStack filter, int quantity) {
        return removeItems(handler, filter, quantity, false);
    }

    public static ItemStack removeItems(IItemHandler handler, ItemStack filter, int quantity, boolean simulate) {
        if (quantity <= 0) {
            return ItemStack.EMPTY;
        }
        if (quantity > filter.getMaxStackSize()) {
            quantity = filter.getMaxStackSize();
        }
        int returnCount = 0;
        ItemStack slotStack;
        for (int index = 0; index < handler.getSlots(); index++) {
            slotStack = handler.getStackInSlot(index);
            if (slotStack.isEmpty() || !doItemStacksMatchRelaxed(filter, slotStack)) {
                continue;
            }

            int toMove = Math.min(quantity - returnCount, slotStack.getCount());

            ItemStack extractedStack = handler.extractItem(index, toMove, simulate);

            returnCount += extractedStack.getCount();
            if (quantity - returnCount <= 0) {
                break;
            }
        }
        ItemStack returnStack = ItemStack.EMPTY;
        if (returnCount > 0) {
            returnStack = filter.copy();
            returnStack.setCount(returnCount);
        }
        return returnStack;
    }

    /** Moves up to {@code quantity} items matching the filter stack from one handler into another. */
    public static int transferItems(IItemHandler from, IItemHandler to, ItemStack filter, int quantity) {
        return transferItems(from, to, filter, quantity, false, false);
    }

    public static int transferItems(IItemHandler from, IItemHandler to, ItemStack filterStack, int quantity, boolean ignoreDamage, boolean ignoreNBT) {
        return transferItems(from, to, stack -> doItemStacksMatch(filterStack, stack, ignoreDamage, ignoreNBT), quantity);
    }

    public static int transferItems(IItemHandler from, IItemHandler to, Function<ItemStack, Boolean> filter, int quantity) {
        int moved = 0;
        int toMove = quantity;
        for (int slot = 0; slot < from.getSlots() && toMove > 0; slot++) {
            ItemStack stack = from.getStackInSlot(slot);
            if (stack.isEmpty() || !filter.apply(stack)) {
                continue;
            }

            int stackSizeToMove = Math.min(stack.getMaxStackSize(), Math.min(stack.getCount(), toMove));

            ItemStack stackToMove = stack.copy();
            stackToMove.setCount(stackSizeToMove);

            if (from.extractItem(slot, stackSizeToMove, true).getCount() != stackSizeToMove) {
                continue;
            }

            ItemStack remaining = insertItem(to, stackToMove, false);

            int stackSizeMoved = stackSizeToMove - remaining.getCount();

            if (stackSizeMoved > 0) {
                from.extractItem(slot, stackSizeMoved, false);
            }

            moved += stackSizeMoved;
            toMove = quantity - moved;
        }
        return moved;
    }

    public static int findItemSlot(IItemHandler handler, Predicate<ItemStack> filter) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (filter.test(stack)) {
                return slot;
            }
        }
        return -1;
    }

    // ---- counting ----

    public static int getCountOf(IItemHandler handler, ItemStack filterStack) {
        return getCountOf(handler, stack -> !stack.isEmpty() && doItemStacksMatchRelaxed(filterStack, stack));
    }

    public static int getCountOf(IItemHandler handler, Predicate<ItemStack> filter) {
        if (handler.getSlots() <= 0) {
            return 0;
        }
        int count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (filter.test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static boolean hasCountOrMore(IItemHandler handler, ItemStack filterStack) {
        return hasCountOrMore(handler, s -> doItemStacksMatch(s, filterStack), filterStack.getCount());
    }

    private static boolean hasCountOrMore(IItemHandler handler, Predicate<ItemStack> filter, int minimumCount) {
        if (handler.getSlots() <= 0) {
            return false;
        }
        int count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (filter.test(stack)) {
                count += stack.getCount();
                if (count >= minimumCount) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---- dropping ----

    /** Drops the stack into the world at the position. Server only; consumes the stack. */
    public static void dropItemInWorld(Level world, ItemStack item, BlockPos pos) {
        dropItemInWorld(world, item, pos.getX(), pos.getY(), pos.getZ());
    }

    public static void dropItemInWorld(Level world, ItemStack item, double x, double y, double z) {
        if (world.isClientSide) {
            return;
        }
        Containers.dropItemStack(world, x, y, z, item);
    }

    public static void dropItemsInWorld(Level world, Container inventory, BlockPos pos) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            dropItemInWorld(world, inventory.getItem(slot), pos);
        }
    }

    public static void dropItemsInWorld(Level world, IItemHandler handler, BlockPos pos) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            dropItemInWorld(world, handler.getStackInSlot(slot), pos);
        }
    }

    public static void dropItemsInWorld(Level world, IItemHandler handler, double x, double y, double z) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            dropItemInWorld(world, handler.getStackInSlot(slot), x, y, z);
        }
    }

    public static void dropItemsInWorld(Level world, NonNullList<ItemStack> stacks, BlockPos pos) {
        for (ItemStack stack : stacks) {
            dropItemInWorld(world, stack, pos);
        }
    }

    public static void insertOrDropItem(IItemHandler handler, ItemStack stack, Level world, BlockPos pos) {
        ItemStack remaining = insertItem(handler, stack, false);
        if (!remaining.isEmpty()) {
            // 1.12.2 dropped the whole original stack here, duplicating whatever had just been inserted.
            dropItemInWorld(world, remaining, pos);
        }
    }

    public static void insertOrDropItems(IItemHandler handler, List<ItemStack> stacks, Level world, BlockPos pos) {
        for (ItemStack stack : stacks) {
            insertOrDropItem(handler, stack, world, pos);
        }
    }

    // ---- Container NBT (was IInventory) ----

    /**
     * Writes the container into the tag under {@code itemList}. Readable with
     * {@link #readInventoryFromNBT(Container, CompoundTag, HolderLookup.Provider)}.
     */
    public static CompoundTag writeInventoryToNBT(Container inventory, CompoundTag tag, HolderLookup.Provider registries) {
        ListTag itemList = new ListTag();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item.isEmpty()) {
                continue;
            }
            CompoundTag itemTag = new CompoundTag();
            item.save(registries, itemTag);
            itemTag.putShort("slot", (short) i);
            itemList.add(itemTag);
        }
        tag.put("itemList", itemList);
        return tag;
    }

    /** Reads what {@link #writeInventoryToNBT} wrote. Slots outside the container are ignored. */
    public static void readInventoryFromNBT(Container inventory, CompoundTag tag, HolderLookup.Provider registries) {
        ListTag itemList = tag.getList("itemList", Tag.TAG_COMPOUND);
        for (int i = 0; i < itemList.size(); i++) {
            CompoundTag itemTag = itemList.getCompound(i);
            int slot = itemTag.getShort("slot");
            if (slot < 0 || slot >= inventory.getContainerSize()) {
                continue;
            }
            inventory.setItem(slot, ItemStack.parseOptional(registries, itemTag));
        }
    }

    // ---- stack lists ----

    /** Compacts the stack list through an {@link ItemQuantityMap}: one entry per item, split at max stack size. */
    public static NonNullList<ItemStack> compactStackList(NonNullList<ItemStack> in) {
        ItemQuantityMap map = new ItemQuantityMap();
        for (ItemStack stack : in) {
            map.addCount(stack, stack.getCount());
        }
        return map.getItems();
    }

    /** Merges {@code stacksToMerge} into {@code stacks}, adding new entries where nothing stacks. */
    public static void mergeItemStacks(NonNullList<ItemStack> stacks, NonNullList<ItemStack> stacksToMerge) {
        for (ItemStack stackToMerge : stacksToMerge) {
            if (stackToMerge.isEmpty()) {
                continue;
            }

            for (ItemStack stack : stacks) {
                if (stack.getCount() < stack.getMaxStackSize() && ItemStack.isSameItemSameComponents(stackToMerge, stack)) {
                    int count = Math.min(stack.getMaxStackSize() - stack.getCount(), stackToMerge.getCount());
                    stack.grow(count);
                    stackToMerge.shrink(count);
                    if (stackToMerge.isEmpty()) {
                        break;
                    }
                }
            }
            if (!stackToMerge.isEmpty()) {
                stacks.add(stackToMerge);
            }
        }
    }

    public static NonNullList<ItemStack> getItems(IItemHandler handler) {
        NonNullList<ItemStack> ret = NonNullList.create();

        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack slotStack = handler.getStackInSlot(slot);
            if (!slotStack.isEmpty()) {
                ret.add(slotStack);
            }
        }
        return ret;
    }

    public static NonNullList<ItemStack> removeItems(NonNullList<ItemStack> stacks, NonNullList<ItemStack> stacksToRemove) {
        return stacks.stream().filter(s -> stacksToRemove.stream().noneMatch(r -> doItemStacksMatch(s, r)))
                .collect(Collectors.toCollection(NonNullList::create));
    }

    public static NonNullList<ItemStack> removeItems(IItemHandler handler, NonNullList<ItemStack> stacks) {
        return removeItems(handler, stacks, false);
    }

    public static NonNullList<ItemStack> removeItems(IItemHandler handler, NonNullList<ItemStack> stacks, boolean simulate) {
        NonNullList<ItemStack> extractedItems = NonNullList.create();
        if (simulate) {
            handler = cloneItemHandler(handler);
        }
        for (ItemStack stack : stacks) {
            ItemStack extracted = removeItem(handler, stack, false);
            if (!extracted.isEmpty()) {
                extractedItems.add(extracted);
            }
        }
        return extractedItems;
    }

    private static ItemStack removeItem(IItemHandler handler, ItemStack stack, boolean simulate) {
        ItemStack extracted = ItemStack.EMPTY;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack slotStack = handler.getStackInSlot(slot);
            if (doItemStacksMatchRelaxed(stack, slotStack)) {
                extracted = handler.extractItem(slot, stack.getCount(), simulate);
                if (extracted.getCount() == stack.getCount()) {
                    break;
                }
            }
        }
        return extracted;
    }

    public static ItemStack removeItem(NonNullList<ItemStack> stacks, Predicate<ItemStack> filter, int quantity) {
        return removeItem(stacks, filter, quantity, false);
    }

    public static ItemStack removeItem(NonNullList<ItemStack> stacks, Predicate<ItemStack> filter, int quantity, boolean simulate) {
        Iterator<ItemStack> it = simulate ? copyStacks(stacks).iterator() : stacks.iterator();

        ItemStack stackToReturn = ItemStack.EMPTY;
        int removed = 0;
        while (it.hasNext()) {
            ItemStack stack = it.next();

            if (filter.test(stack)) {
                if (stack.getMaxStackSize() < quantity) {
                    throw new UnsupportedOperationException("Not supported for quantity greater than max stack size");
                }

                int toRemove = Math.min(quantity - removed, stack.getCount());
                removed += toRemove;

                if (stackToReturn.isEmpty()) {
                    stackToReturn = stack.copy();
                }
                stack.shrink(toRemove);
                stackToReturn.setCount(removed);

                if (stack.isEmpty()) {
                    it.remove();
                }

                if (quantity - removed <= 0) {
                    return stackToReturn;
                }
            }
        }
        return stackToReturn;
    }

    public static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).collect(Collectors.toCollection(NonNullList::create));
    }

    public static NonNullList<ItemStack> toNonNullList(List<ItemStack> stacks) {
        NonNullList<ItemStack> ret = NonNullList.create();
        for (ItemStack stack : stacks) {
            ret.add(java.util.Objects.requireNonNull(stack));
        }
        return ret;
    }

    public static void emptyInventory(IItemHandler itemHandler) {
        for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
            itemHandler.extractItem(slot, itemHandler.getStackInSlot(slot).getCount(), false);
        }
    }

    // ---- capabilities ----

    /** True if the block entity exposes an item handler on any side. */
    public static boolean isInventory(BlockEntity blockEntity) {
        Level level = blockEntity.getLevel();
        return level != null && level.getCapability(Capabilities.ItemHandler.BLOCK, blockEntity.getBlockPos(),
                blockEntity.getBlockState(), blockEntity, null) != null;
    }

    /** Replaces the 1.12.2 capability-provider lookup: the item handler of the block at the position, if any. */
    public static Optional<IItemHandler> getItemHandlerFrom(Level level, BlockPos pos, @Nullable Direction side) {
        return Optional.ofNullable(level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side));
    }

    public static Stream<ItemStack> stream(IItemHandler handler) {
        return StreamSupport.stream(getIterator(handler).spliterator(), false);
    }

    public static Iterable<ItemStack> getIterator(IItemHandler handler) {
        return () -> new Iterator<ItemStack>() {
            private int currentSlot = 0;

            @Override
            public boolean hasNext() {
                return currentSlot < handler.getSlots();
            }

            @Override
            public ItemStack next() {
                if (currentSlot < 0 || currentSlot >= handler.getSlots()) {
                    throw new NoSuchElementException();
                }

                return handler.getStackInSlot(currentSlot++);
            }
        };
    }

    // ---- loot ----

    /** Fills empty slots of the handler from the simple dungeon chest table, as the original's default did. */
    public static void generateLootFor(ServerLevel level, Vec3 origin, IItemHandler inventory, RandomSource rng, int rolls) {
        generateLootFor(level, origin, null, inventory, rng, BuiltInLootTables.SIMPLE_DUNGEON.location(), rolls);
    }

    /**
     * Rolls the loot table {@code rolls} times, merges the result and scatters it over the empty slots of
     * the handler. The origin parameter is new: 1.21 chest loot tables need a position.
     */
    public static void generateLootFor(ServerLevel level, Vec3 origin, @Nullable Player player, IItemHandler inventory,
                                       RandomSource rng, ResourceLocation lootTableName, int rolls) {
        NonNullList<ItemStack> loot = NonNullList.create();
        for (int i = 0; i < rolls; i++) {
            mergeItemStacks(loot, getLootStacks(level, origin, player, rng, lootTableName));
        }

        List<Integer> randomSlots = getEmptySlotsRandomized(inventory, rng);
        shuffleItems(loot, randomSlots.size(), rng);

        for (ItemStack itemstack : loot) {
            if (randomSlots.isEmpty()) {
                LOGGER.warn("Tried to over-fill a container");
                return;
            }

            if (!itemstack.isEmpty()) {
                inventory.insertItem(randomSlots.remove(randomSlots.size() - 1), itemstack, false);
            }
        }
    }

    public static NonNullList<ItemStack> getLootStacks(ServerLevel level, Vec3 origin, @Nullable Player player, RandomSource rng, ResourceLocation lootTableName) {
        LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, lootTableName));
        LootParams.Builder builder = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, origin);
        if (player != null) {
            builder.withLuck(player.getLuck()).withParameter(LootContextParams.THIS_ENTITY, player);
        }
        return toNonNullList(lootTable.getRandomItems(builder.create(LootContextParamSets.CHEST), rng));
    }

    public static List<Integer> getEmptySlotsRandomized(IItemHandler inventory, RandomSource rand) {
        List<Integer> list = new ArrayList<>();

        for (int i = 0; i < inventory.getSlots(); ++i) {
            if (inventory.getStackInSlot(i).isEmpty()) {
                list.add(i);
            }
        }

        // Collections.shuffle needs a java.util.Random; RandomSource has none, so shuffle by hand.
        for (int i = list.size() - 1; i > 0; i--) {
            Collections.swap(list, i, rand.nextInt(i + 1));
        }
        return list;
    }

    private static void shuffleItems(NonNullList<ItemStack> stacks, int numberOfSlots, RandomSource rand) {
        numberOfSlots = numberOfSlots - stacks.size();

        int minSizeToSplit = 3;

        List<ItemStack> splittableStacks = stacks.stream().filter(s -> s.getCount() >= minSizeToSplit).collect(Collectors.toList());

        while (numberOfSlots > 0 && !splittableStacks.isEmpty()) {
            int slot = rand.nextInt(splittableStacks.size());

            ItemStack stack = splittableStacks.get(slot);

            int splitCount = Mth.nextInt(rand, 1, stack.getCount() / 2);
            ItemStack splitStack = stack.split(splitCount);

            if (stack.getCount() < minSizeToSplit) {
                splittableStacks.remove(slot);
            }

            if (splitStack.getCount() >= minSizeToSplit) {
                splittableStacks.add(splitStack);
            }

            stacks.add(splitStack);

            numberOfSlots--;
        }

        for (int i = stacks.size() - 1; i > 0; i--) {
            Collections.swap(stacks, i, rand.nextInt(i + 1));
        }
    }

    // ---- sorting ----

    /** Item-stack hash entry comparator, configurable by sort type and order (for warehouse style GUIs). */
    public static final class ComparatorItemHashEntry implements Comparator<ItemHashEntry> {

        public enum SortType {
            QUANTITY("sort_type_quantity") {
                @Override
                public int compare(ItemQuantityMap itemMap, ItemHashEntry o1, ItemHashEntry o2) {
                    int r = itemMap.getCount(o1) - itemMap.getCount(o2);
                    if (r == 0) {
                        return super.compare(itemMap, o1, o2);
                    }
                    return r;
                }
            }, NAME("sort_type_name") {
                @Override
                public int compare(ItemQuantityMap itemMap, ItemHashEntry o1, ItemHashEntry o2) {
                    int r = o1.getItemStack().getHoverName().getString().compareTo(o2.getItemStack().getHoverName().getString());
                    if (r == 0) { // same name: compare item id, damage and components
                        return super.compare(itemMap, o1, o2);
                    }
                    return r;
                }
            }, DAMAGE("sort_type_damage");

            public final String unlocalizedName;

            SortType(String unlocalizedName) {
                this.unlocalizedName = unlocalizedName;
            }

            public SortType next() {
                if (this == QUANTITY) {
                    return NAME;
                } else if (this == NAME) {
                    return DAMAGE;
                } else {
                    return QUANTITY;
                }
            }

            @Override
            public String toString() {
                return unlocalizedName;
            }

            public int compare(ItemQuantityMap itemMap, ItemHashEntry o1, ItemHashEntry o2) {
                ItemStack stackA = o1.getItemStack();
                ItemStack stackB = o2.getItemStack();
                int itemComparison = BuiltInRegistries.ITEM.getKey(stackA.getItem()).compareTo(BuiltInRegistries.ITEM.getKey(stackB.getItem()));
                if (itemComparison != 0) {
                    return itemComparison;
                }

                if (stackA.getDamageValue() != stackB.getDamageValue()) {
                    return Integer.compare(stackA.getDamageValue(), stackB.getDamageValue());
                }

                // Other components stand in for the NBT tag: stacks with extra data sort after plain ones.
                boolean dataA = !withoutDamage(stackA).getComponentsPatch().isEmpty();
                boolean dataB = !withoutDamage(stackB).getComponentsPatch().isEmpty();
                if (dataA) {
                    if (dataB) {
                        return Integer.compare(ItemStack.hashItemAndComponents(stackA), ItemStack.hashItemAndComponents(stackB));
                    }
                    return 1;
                } else if (dataB) {
                    return -1;
                }

                return 0;
            }
        }

        public enum SortOrder {
            ASCENDING(-1), DESCENDING(1);

            SortOrder(int mult) {
                this.mult = mult;
            }

            final int mult;
        }

        private final ItemQuantityMap itemMap;
        private SortOrder sortOrder;
        private SortType sortType;

        public ComparatorItemHashEntry(ItemQuantityMap itemMap, SortType type, SortOrder order) {
            this.itemMap = itemMap;
            sortOrder = order;
            sortType = type;
        }

        public void setSortOrder(SortOrder order) {
            sortOrder = order;
        }

        public void setSortType(SortType type) {
            sortType = type;
        }

        @Override
        public int compare(ItemHashEntry o1, ItemHashEntry o2) {
            int result = sortType.compare(itemMap, o1, o2);
            if (result == 0) {
                return 0;
            }
            return (result > 0 ? 1 : -1) * sortOrder.mult;
        }
    }

    /** Re-exported so callers do not need both this class and NeoForge's helper for the common case. */
    public static ItemStack insertItemStacked(IItemHandler handler, ItemStack stack, boolean simulate) {
        return ItemHandlerHelper.insertItemStacked(handler, stack, simulate);
    }
}
