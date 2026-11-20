/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/orders/RoutingOrder.
 */
package io.github.capttracker.ancientwarfarenpc.orders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfare.util.OrderingList;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcItemComponents;
import io.github.capttracker.ancientwarfarenpc.item.order.ItemRoutingOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The routing order slip: the route of a courier. Every {@link RoutePoint} is an inventory (a position and the side to access it from), a
 * {@link RouteType} (what to move) and up to 12 filter stacks; the stack size of a filter is the quantity the route type works with.
 * Stored in the {@code ancientwarfarenpc:routing_order} data component with the layout of the 1.12.2 tag {@code orders}:
 * {@code entryList}, each entry {@code type, position, blockSide, ignoreDamage, ignoreTag, filterList, filterCounts}. The filter stacks of the
 * list have stack size 1 and their slot; the real quantities are in {@code filterCounts}, as in 1.12.2 (a data component stack holds at most 99).
 */
public class RoutingOrder extends OrderingList<RoutingOrder.RoutePoint> {
    /** The number of filter slots of a route point. */
    public static final int FILTER_SLOTS = 12;

    public static final Codec<RoutingOrder> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RoutePoint.CODEC.listOf().optionalFieldOf("entryList", List.of()).forGetter(order -> order.points)
    ).apply(instance, RoutingOrder::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, RoutingOrder> STREAM_CODEC = OrderCodecs.stream(CODEC);

    public RoutingOrder() {
    }

    private RoutingOrder(List<RoutePoint> entries) {
        for (RoutePoint entry : entries) {
            add(entry.copy());
        }
    }

    public void addRoutePoint(Direction side, BlockPos pos) {
        add(new RoutePoint(side, pos));
    }

    private boolean check(int index) {
        return index >= 0 && index < size();
    }

    public void changeRouteType(int index, boolean isRmb) {
        if (check(index)) {
            get(index).changeRouteType(isRmb);
        }
    }

    public void changeBlockSide(int index) {
        if (check(index)) {
            get(index).changeBlockSide();
        }
    }

    public void toggleIgnoreDamage(int index) {
        if (check(index)) {
            get(index).toggleIgnoreDamage();
        }
    }

    public void toggleIgnoreTag(int index) {
        if (check(index)) {
            get(index).toggleIgnoreTag();
        }
    }

    public List<RoutePoint> getEntries() {
        return points;
    }

    public RoutingOrder copy() {
        return new RoutingOrder(points);
    }

    /** The order of the stack (a new one if the stack has no data), or null if the stack is not a routing order. */
    @Nullable
    public static RoutingOrder getRoutingOrder(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof ItemRoutingOrder) {
            RoutingOrder order = stack.get(AwNpcItemComponents.ROUTING_ORDER.get());
            return order == null ? new RoutingOrder() : order.copy();
        }
        return null;
    }

    /** Stores a copy of the order in the stack (if it is a routing order). */
    public void write(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof ItemRoutingOrder) {
            stack.set(AwNpcItemComponents.ROUTING_ORDER.get(), copy());
        }
    }

    /**
     * Does the routing action for the courier at the route point. Position and distance are not checked here, the AI does that before calling.
     *
     * @param npc    the courier's inventory (its backpack)
     * @param target the inventory at the route point
     * @return the number of stacks processed, which the AI turns into the time the courier works at the point
     */
    public int handleRouteAction(RoutePoint p, IItemHandler npc, IItemHandler target) {
        return switch (p.routeType) {
            case FILL_COURIER_TO -> p.fillTo(target, npc);
            case FILL_TARGET_TO -> p.fillTo(npc, target);
            case DEPOSIT_ALL_EXCEPT -> p.depositAllItemsExcept(npc, target);
            case DEPOSIT_ALL_OF -> p.depositAllItems(npc, target);
            case WITHDRAW_ALL_EXCEPT -> p.depositAllItemsExcept(target, npc);
            case WITHDRAW_ALL_OF -> p.depositAllItems(target, npc);
            case DEPOSIT_RATIO -> p.depositRatio(npc, target);
            case WITHDRAW_RATIO -> p.depositRatio(target, npc);
            case DEPOSIT_EXACT -> p.depositExact(npc, target);
            case WITHDRAW_EXACT -> p.depositExact(target, npc);
            case FILL_MINIMUM -> p.fillAtLeast(npc, target);
            case TAKE_MINIMUM -> p.fillAtLeast(target, npc);
        };
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RoutingOrder other && points.equals(other.points);
    }

    @Override
    public int hashCode() {
        return points.hashCode();
    }

    /** One stop of the route. */
    public static class RoutePoint {
        /** The filters on disk: stacks of size 1 with their slot. */
        private static final Codec<List<OrderCodecs.SlotStack>> FILTER_LIST = OrderCodecs.SLOT_STACKS;

        public static final Codec<RoutePoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                OrderCodecs.ordinal(RouteType.values(), RouteType.FILL_TARGET_TO).optionalFieldOf("type", RouteType.FILL_TARGET_TO)
                        .forGetter(RoutePoint::getRouteType),
                OrderCodecs.BLOCK_POS.optionalFieldOf("position", BlockPos.ZERO).forGetter(RoutePoint::getTarget),
                OrderCodecs.DIRECTION.optionalFieldOf("blockSide", Direction.DOWN).forGetter(RoutePoint::getBlockSide),
                Codec.BOOL.optionalFieldOf("ignoreDamage", false).forGetter(RoutePoint::getIgnoreDamage),
                Codec.BOOL.optionalFieldOf("ignoreTag", false).forGetter(RoutePoint::getIgnoreTag),
                FILTER_LIST.optionalFieldOf("filterList", List.of()).forGetter(RoutePoint::filterSlots),
                Codec.INT.listOf().optionalFieldOf("filterCounts", List.of()).forGetter(RoutePoint::filterCounts)
        ).apply(instance, RoutePoint::new));

        private boolean ignoreDamage;
        private boolean ignoreTag;
        private RouteType routeType = RouteType.FILL_TARGET_TO;
        private BlockPos target = BlockPos.ZERO;
        private Direction blockSide = Direction.DOWN;
        private final List<ItemStack> filters = emptyFilters();

        private RoutePoint(RouteType type, BlockPos target, Direction side, boolean ignoreDamage, boolean ignoreTag,
                           List<OrderCodecs.SlotStack> filterList, List<Integer> filterCounts) {
            this.routeType = type;
            this.target = target;
            this.blockSide = side;
            this.ignoreDamage = ignoreDamage;
            this.ignoreTag = ignoreTag;
            for (OrderCodecs.SlotStack slotStack : filterList) {
                int slot = slotStack.slot();
                if (slot < 0) {
                    continue;
                }
                while (slot >= filters.size()) {
                    filters.add(ItemStack.EMPTY);
                }
                ItemStack filterStack = slotStack.stack().copy();
                // 1.12.2 indexed the counts array unchecked; a missing count keeps the stack's own size
                if (slot < filterCounts.size() && filterCounts.get(slot) > 0) {
                    filterStack.setCount(filterCounts.get(slot));
                }
                filters.set(slot, filterStack);
            }
        }

        private RoutePoint(Direction side, BlockPos target) {
            this.target = target;
            this.blockSide = side;
        }

        private static List<ItemStack> emptyFilters() {
            List<ItemStack> ret = new ArrayList<>(FILTER_SLOTS);
            for (int i = 0; i < FILTER_SLOTS; i++) {
                ret.add(ItemStack.EMPTY);
            }
            return ret;
        }

        public RoutePoint copy() {
            RoutePoint ret = new RoutePoint(blockSide, target);
            ret.routeType = routeType;
            ret.ignoreDamage = ignoreDamage;
            ret.ignoreTag = ignoreTag;
            ret.filters.clear();
            for (ItemStack filter : filters) {
                ret.filters.add(filter.copy());
            }
            return ret;
        }

        private void changeBlockSide() {
            blockSide = Direction.values()[(blockSide.ordinal() + 1) % Direction.values().length];
        }

        private void changeRouteType(boolean isRmb) {
            routeType = isRmb ? routeType.previous() : routeType.next();
        }

        public void setFilter(int index, ItemStack stack) {
            filters.set(index, stack);
        }

        public Direction getBlockSide() {
            return blockSide;
        }

        public RouteType getRouteType() {
            return routeType;
        }

        public BlockPos getTarget() {
            return target;
        }

        public ItemStack getFilterInSlot(int slot) {
            return filters.get(slot);
        }

        public int getFilterSize() {
            return filters.size();
        }

        public boolean getIgnoreDamage() {
            return ignoreDamage;
        }

        public boolean getIgnoreTag() {
            return ignoreTag;
        }

        private void toggleIgnoreDamage() {
            ignoreDamage = !ignoreDamage;
        }

        private void toggleIgnoreTag() {
            ignoreTag = !ignoreTag;
        }

        /** The non-empty filters with size 1 and their slot (the quantities are saved apart in {@link #filterCounts}). */
        private List<OrderCodecs.SlotStack> filterSlots() {
            List<OrderCodecs.SlotStack> ret = new ArrayList<>();
            for (int i = 0; i < filters.size(); i++) {
                if (!filters.get(i).isEmpty()) {
                    ItemStack filterCopy = filters.get(i).copy();
                    filterCopy.setCount(1);
                    ret.add(new OrderCodecs.SlotStack(i, filterCopy));
                }
            }
            return ret;
        }

        private List<Integer> filterCounts() {
            List<Integer> ret = new ArrayList<>(filters.size());
            for (ItemStack filter : filters) {
                ret.add(filter.isEmpty() ? 0 : filter.getCount());
            }
            return ret;
        }

        private int depositAllItems(IItemHandler from, IItemHandler to) {
            float movedStacks = 0;
            for (ItemStack filter : filters) {
                if (filter.isEmpty()) {
                    continue;
                }
                int movedSize = InventoryTools.transferItems(from, to, filter, Integer.MAX_VALUE, ignoreDamage, ignoreTag);
                movedStacks += (float) movedSize / filter.getMaxStackSize();
            }
            return (int) Math.ceil(movedStacks);
        }

        private int depositAllItemsExcept(IItemHandler from, IItemHandler to) {
            float movedStacks = 0;
            for (ItemStack stack : InventoryTools.getIterator(from)) {
                if (stack.isEmpty() || matchesFilter(stack)) {
                    continue;
                }
                ItemStack filter = stack.copy();
                ItemStack remaining = InventoryTools.insertItem(to, filter, true);
                int toMove = stack.getCount() - remaining.getCount();
                if (!InventoryTools.removeItems(from, filter, toMove, true).isEmpty()) {
                    ItemStack removedStack = InventoryTools.removeItems(from, filter, toMove);
                    InventoryTools.insertItem(to, removedStack);
                    movedStacks += ((float) removedStack.getCount()) / ((float) filter.getMaxStackSize());
                }
            }
            return (int) Math.ceil(movedStacks);
        }

        private boolean matchesFilter(ItemStack stack) {
            return filters.stream().anyMatch(s -> !s.isEmpty() && InventoryTools.doItemStacksMatch(stack, s, ignoreDamage, ignoreTag));
        }

        private int fillTo(IItemHandler from, IItemHandler to) {
            float moved = 0;
            for (ItemStack filter : filters) {
                if (filter.isEmpty()) {
                    continue;
                }
                int foundCount = InventoryTools.getCountOf(to, filter);
                int toMove = filter.getCount();
                if (foundCount > toMove) {
                    continue;
                }
                toMove -= foundCount;
                int m1 = InventoryTools.transferItems(from, to, filter, toMove, ignoreDamage, ignoreTag);
                moved += (float) m1 / filter.getMaxStackSize();
            }
            return (int) Math.ceil(moved);
        }

        private int depositRatio(IItemHandler from, IItemHandler to) {
            float movedTotal = 0;
            for (ItemStack filter : filters) {
                if (filter.isEmpty()) {
                    continue;
                }
                int foundCount = InventoryTools.getCountOf(from, filter);
                int toMove = (int) (foundCount * (1f / (float) filter.getCount()));
                int moved = InventoryTools.transferItems(from, to, filter, toMove, ignoreDamage, ignoreTag);
                movedTotal += (float) moved / filter.getMaxStackSize();
            }
            return (int) Math.ceil(movedTotal);
        }

        private int depositExact(IItemHandler from, IItemHandler to) {
            float movedTotal = 0;
            for (ItemStack filter : filters) {
                if (filter.isEmpty()) {
                    continue;
                }
                int foundCount = InventoryTools.getCountOf(from, filter);
                int toMove = filter.getCount();
                if (foundCount < toMove) {
                    continue;
                }
                if (!InventoryTools.canInventoryHold(to, filter)) {
                    continue;
                }
                int moved = InventoryTools.transferItems(from, to, filter, toMove, ignoreDamage, ignoreTag);
                movedTotal += (float) moved / filter.getMaxStackSize();
            }
            return (int) Math.ceil(movedTotal);
        }

        private int fillAtLeast(IItemHandler from, IItemHandler to) {
            float movedTotal = 0;
            for (ItemStack filter : filters) {
                if (filter.isEmpty()) {
                    continue;
                }
                int foundCount = InventoryTools.getCountOf(from, filter);
                int existingCount = InventoryTools.getCountOf(to, filter);
                int toMove = filter.getCount() - existingCount; // we only want to move items up to the specified filter size
                if (toMove < 1 || foundCount < toMove) {
                    // the target already has more than the filter specifies
                    // or the source doesn't have enough to fulfill the minimum requirement
                    continue;
                }
                ItemStack filterAdjusted = filter.copy();
                filterAdjusted.setCount(toMove);
                if (!InventoryTools.canInventoryHold(to, filterAdjusted)) {
                    continue;
                }
                int moved = InventoryTools.transferItems(from, to, filterAdjusted, foundCount, ignoreDamage, ignoreTag);
                movedTotal += (float) moved / filter.getMaxStackSize();
            }
            return (int) Math.ceil(movedTotal);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof RoutePoint other && ignoreDamage == other.ignoreDamage && ignoreTag == other.ignoreTag && routeType == other.routeType
                    && target.equals(other.target) && blockSide == other.blockSide && OrderCodecs.stacksEqual(filters, other.filters);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ignoreDamage, ignoreTag, routeType, target, blockSide, OrderCodecs.stacksHash(filters));
        }
    }

    public enum RouteType {
        /** Fill the target up to the specified quantity from the courier's inventory. */
        FILL_TARGET_TO("route.fill.upto"),
        /** Fill the courier up to the specified quantity from the target's inventory. */
        FILL_COURIER_TO("route.take.upto"),
        /** Deposit any of the specified items from the courier into the target inventory (no quantity limit). */
        DEPOSIT_ALL_OF("route.deposit.match"),
        /** Withdraw any of the specified items from the target inventory into the courier inventory (no quantity limit). */
        WITHDRAW_ALL_OF("route.withdraw.match"),
        /** Deposit all items of the courier's inventory, except those matching a filter. */
        DEPOSIT_ALL_EXCEPT("route.deposit.no_match"),
        /** Withdraw all items of the target inventory, except those matching a filter. */
        WITHDRAW_ALL_EXCEPT("route.withdraw.no_match"),
        /** Deposit the specified ratio of the items (the ratio is 1 / filter stack size). */
        DEPOSIT_RATIO("route.deposit.ratio"),
        /** Withdraw the specified ratio of the items (the ratio is 1 / filter stack size). */
        WITHDRAW_RATIO("route.withdraw.ratio"),
        /** Deposit the exact number of items (or none at all if that is not possible). */
        DEPOSIT_EXACT("route.deposit.exact"),
        /** Withdraw the exact number of items (or none at all if that is not possible). */
        WITHDRAW_EXACT("route.withdraw.exact"),
        /** Deposit a minimum of items. */
        FILL_MINIMUM("route.fill.minimum"),
        /** Withdraw a minimum of items. */
        TAKE_MINIMUM("route.take.minimum");

        private final String key;

        RouteType(String key) {
            this.key = key;
        }

        /** The lang key, as in 1.12.2 ({@code route.fill.upto} and so on, with a {@code .tooltip} variant for each). */
        public String getTranslationKey() {
            return key;
        }

        public static RouteType next(@Nullable RouteType type) {
            return type == null ? RouteType.FILL_TARGET_TO : type.next();
        }

        public static RouteType previous(@Nullable RouteType type) {
            return type == null ? RouteType.FILL_TARGET_TO : type.previous();
        }

        public RouteType next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public RouteType previous() {
            return values()[(ordinal() + values().length - 1) % values().length];
        }
    }
}
