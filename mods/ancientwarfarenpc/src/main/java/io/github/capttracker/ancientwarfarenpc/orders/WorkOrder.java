/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/orders/WorkOrder.
 */
package io.github.capttracker.ancientwarfarenpc.orders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.capttracker.ancientwarfare.api.IWorkSite;
import io.github.capttracker.ancientwarfare.api.IWorker;
import io.github.capttracker.ancientwarfare.util.OrderingList;
import io.github.capttracker.ancientwarfare.util.WorldTools;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcItemComponents;
import io.github.capttracker.ancientwarfarenpc.item.order.ItemWorkOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * The work order slip: up to {@value #MAX_SIZE} worksite positions, how the worker picks the next one and whether it works at night only.
 * Stored in the {@code ancientwarfarenpc:work_order} data component of the item with the layout of the 1.12.2 tag {@code orders}
 * ({@code entryList}, {@code priorityType}, {@code nightShift}); an entry's dimension is a dimension id now ({@code dim}), not an int.
 *
 * <p>The component value is never changed in place: {@link #getWorkOrder} returns a copy and {@link #write} stores one.
 */
public class WorkOrder extends OrderingList<WorkOrder.WorkEntry> {
    public static final int MAX_SIZE = 8;

    public static final Codec<WorkOrder> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            WorkEntry.CODEC.listOf().optionalFieldOf("entryList", List.of()).forGetter(order -> order.points),
            OrderCodecs.ordinal(WorkPriorityType.values(), WorkPriorityType.ROUTE).optionalFieldOf("priorityType", WorkPriorityType.ROUTE)
                    .forGetter(WorkOrder::getPriorityType),
            Codec.BOOL.optionalFieldOf("nightShift", false).forGetter(WorkOrder::isNightShift)
    ).apply(instance, WorkOrder::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkOrder> STREAM_CODEC = OrderCodecs.stream(CODEC);

    private WorkPriorityType priorityType = WorkPriorityType.ROUTE;
    private boolean nightShift;

    public WorkOrder() {
    }

    private WorkOrder(List<WorkEntry> entries, WorkPriorityType priorityType, boolean nightShift) {
        for (WorkEntry entry : entries) {
            add(entry.copy());
        }
        this.priorityType = priorityType;
        this.nightShift = nightShift;
    }

    public boolean isNightShift() {
        return nightShift;
    }

    public void toggleShift() {
        nightShift = !nightShift;
    }

    public WorkPriorityType getPriorityType() {
        return priorityType;
    }

    public List<WorkEntry> getEntries() {
        return points;
    }

    /** @return true if successfully added (a position is given and there is room) */
    public boolean addWorkPosition(Level world, @Nullable BlockPos position) {
        if (position != null && size() < MAX_SIZE) {
            add(new WorkEntry(position, world.dimension(), 0));
            return true;
        }
        return false;
    }

    public void togglePriority() {
        WorkPriorityType[] type = WorkPriorityType.values();
        priorityType = type[(priorityType.ordinal() + 1) % type.length];
    }

    public WorkOrder copy() {
        return new WorkOrder(points, priorityType, nightShift);
    }

    /** The order of the stack (a new one if the stack has no data), or null if the stack is not a work order. */
    @Nullable
    public static WorkOrder getWorkOrder(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof ItemWorkOrder) {
            WorkOrder order = stack.get(AwNpcItemComponents.WORK_ORDER.get());
            return order == null ? new WorkOrder() : order.copy();
        }
        return null;
    }

    /** Stores a copy of the order in the stack (if it is a work order). */
    public void write(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof ItemWorkOrder) {
            stack.set(AwNpcItemComponents.WORK_ORDER.get(), copy());
        }
    }

    @Override
    public String toString() {
        return "Work Orders size: " + size() + " of type: " + priorityType;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof WorkOrder other && priorityType == other.priorityType && nightShift == other.nightShift && points.equals(other.points);
    }

    @Override
    public int hashCode() {
        return Objects.hash(points, priorityType, nightShift);
    }

    /** One worksite: where it is, in which dimension, and for how long a {@link WorkPriorityType#TIMED} order stays there. */
    public static final class WorkEntry {
        public static final Codec<WorkEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                OrderCodecs.BLOCK_POS.optionalFieldOf("pos", BlockPos.ZERO).forGetter(WorkEntry::getPosition),
                Level.RESOURCE_KEY_CODEC.optionalFieldOf("dim", Level.OVERWORLD).forGetter(WorkEntry::getDimension),
                Codec.INT.optionalFieldOf("length", 0).forGetter(WorkEntry::getWorkLength)
        ).apply(instance, WorkEntry::new));

        private BlockPos position;
        private final ResourceKey<Level> dimension;
        private int workLength;

        public WorkEntry(BlockPos position, ResourceKey<Level> dimension, int workLength) {
            this.position = position;
            this.dimension = dimension;
            this.workLength = workLength;
        }

        public WorkEntry copy() {
            return new WorkEntry(position, dimension, workLength);
        }

        public Block getBlock(Level world) {
            return world.getBlockState(getPosition()).getBlock();
        }

        public BlockPos getPosition() {
            return position;
        }

        public void setPosition(BlockPos position) {
            this.position = position;
        }

        public ResourceKey<Level> getDimension() {
            return dimension;
        }

        public int getWorkLength() {
            return workLength;
        }

        public void setWorkLength(int workLength) {
            this.workLength = workLength;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof WorkEntry other && workLength == other.workLength && position.equals(other.position) && dimension.equals(other.dimension);
        }

        @Override
        public int hashCode() {
            return Objects.hash(position, dimension, workLength);
        }
    }

    /** How the worker chooses the next worksite. */
    public enum WorkPriorityType {
        /** The first entry whose block is a worksite that this worker can work at and that has work (entry 0 if none). */
        SITE_NEED {
            @Override
            public int getNextWorkIndex(int current, List<WorkEntry> orders, NpcBase npc) {
                for (int i = 0; i < orders.size(); i++) {
                    if (WorldTools.getTile(npc.level(), orders.get(i).getPosition(), IWorkSite.class)
                            .filter(site -> npc instanceof IWorker worker && worker.canWorkAt(site.getWorkType()) && site.hasWork()).isPresent()) {
                        return i;
                    }
                }
                return 0;
            }
        },
        /** Cycle through the entries in order. */
        ROUTE,
        /** Cycle through the entries, moving on after each entry's work length. */
        TIMED;

        public int getNextWorkIndex(int current, List<WorkEntry> orders, NpcBase npcBase) {
            if (current + 1 >= orders.size()) {
                return 0;
            }
            return current + 1;
        }

        public boolean isTimed() {
            return this == TIMED;
        }
    }
}
