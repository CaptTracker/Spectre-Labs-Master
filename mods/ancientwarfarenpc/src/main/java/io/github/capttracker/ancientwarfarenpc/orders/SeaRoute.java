/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 11): the sea route chart, the route of docks a trader sails a cargo ship along.
 */
package io.github.capttracker.ancientwarfarenpc.orders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcItemComponents;
import io.github.capttracker.ancientwarfarenpc.item.order.ItemSeaRoute;
import io.github.capttracker.ancientwarfarenpc.trade.POTradeDepositEntry;
import io.github.capttracker.ancientwarfarenpc.trade.POTradeWithdrawEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The sea route chart: the docks (stops) a trader sails a cargo ship to, in order and round again, and for each stop what is unloaded there and what is loaded.
 * Stored in the {@code ancientwarfarenpc:sea_route} data component as {@code stops}, each with {@code dock} (the position of the dock block), {@code wait} (ticks
 * the ship stays moored), {@code unload} (items the ship puts into the dock's storage, the entries of a trade order's deposit list) and {@code load} (items it takes
 * out of the dock's storage, the entries of the withdraw list). A route of at least two stops is sailed.
 */
public class SeaRoute {
    /** The most stops a chart holds, and the most entries in each list of a stop. */
    public static final int MAX_STOPS = 16;
    public static final int MAX_ENTRIES = 16;
    /** How long a ship stays moored at a new stop: ten seconds, and the most it may be set to (an hour). */
    public static final int DEFAULT_WAIT = 200;
    public static final int MAX_WAIT = 72000;

    /** One stop of the route. */
    public static final class Stop {
        public static final Codec<Stop> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                OrderCodecs.BLOCK_POS.optionalFieldOf("dock", BlockPos.ZERO).forGetter(Stop::getDock),
                Codec.INT.optionalFieldOf("wait", DEFAULT_WAIT).forGetter(Stop::getWaitTicks),
                OrderCodecs.lenientList(POTradeDepositEntry.CODEC).optionalFieldOf("unload", List.of()).forGetter(Stop::getUnload),
                OrderCodecs.lenientList(POTradeWithdrawEntry.CODEC).optionalFieldOf("load", List.of()).forGetter(Stop::getLoad)
        ).apply(instance, Stop::new));

        private final BlockPos dock;
        private int waitTicks;
        private final List<POTradeDepositEntry> unload = new ArrayList<>();
        private final List<POTradeWithdrawEntry> load = new ArrayList<>();

        private Stop(BlockPos dock, int waitTicks, List<POTradeDepositEntry> unload, List<POTradeWithdrawEntry> load) {
            this.dock = dock;
            this.waitTicks = Math.max(0, Math.min(MAX_WAIT, waitTicks));
            for (POTradeDepositEntry entry : unload) {
                this.unload.add(entry.copy());
            }
            for (POTradeWithdrawEntry entry : load) {
                this.load.add(entry.copy());
            }
        }

        public Stop(BlockPos dock) {
            this(dock, DEFAULT_WAIT, List.of(), List.of());
        }

        public Stop copy() {
            return new Stop(dock, waitTicks, unload, load);
        }

        /** The position of the dock block. */
        public BlockPos getDock() {
            return dock;
        }

        /** Ticks the ship stays moored after it has unloaded and loaded. */
        public int getWaitTicks() {
            return waitTicks;
        }

        public void setWaitTicks(int ticks) {
            waitTicks = Math.max(0, Math.min(MAX_WAIT, ticks));
        }

        /** What the ship puts into the dock's storage here (the quantity of an entry is as in the trade order's deposit list). */
        public List<POTradeDepositEntry> getUnload() {
            return unload;
        }

        /** What the ship takes out of the dock's storage here (as in the trade order's withdraw list). */
        public List<POTradeWithdrawEntry> getLoad() {
            return load;
        }

        public void addUnloadEntry() {
            unload.add(new POTradeDepositEntry());
        }

        public void addLoadEntry() {
            load.add(new POTradeWithdrawEntry());
        }

        public boolean hasRules() {
            return unload.stream().anyMatch(entry -> !entry.getFilter().isEmpty()) || load.stream().anyMatch(entry -> !entry.getFilter().isEmpty());
        }

        /** Unloads and then loads: the ship's {@code cargo} against one {@code storage} of the dock (its inventory, or a storage of the settlement). */
        public void transfer(IItemHandler cargo, IItemHandler storage) {
            for (POTradeDepositEntry entry : unload) {
                entry.process(cargo, storage);
            }
            for (POTradeWithdrawEntry entry : load) {
                entry.process(cargo, storage);
            }
        }

        public void unload(IItemHandler cargo, IItemHandler storage) {
            for (POTradeDepositEntry entry : unload) {
                entry.process(cargo, storage);
            }
        }

        public void load(IItemHandler cargo, IItemHandler storage) {
            for (POTradeWithdrawEntry entry : load) {
                entry.process(cargo, storage);
            }
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Stop other && dock.equals(other.dock) && waitTicks == other.waitTicks && unload.equals(other.unload) && load.equals(other.load);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dock, waitTicks, unload, load);
        }
    }

    public static final Codec<SeaRoute> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            OrderCodecs.lenientList(Stop.CODEC).optionalFieldOf("stops", List.of()).forGetter(SeaRoute::getStops)
    ).apply(instance, SeaRoute::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, SeaRoute> STREAM_CODEC = OrderCodecs.stream(CODEC);

    private final List<Stop> stops = new ArrayList<>();

    public SeaRoute() {
    }

    private SeaRoute(List<Stop> stops) {
        for (Stop stop : stops) {
            if (this.stops.size() < MAX_STOPS) {
                this.stops.add(stop.copy());
            }
        }
    }

    public SeaRoute copy() {
        return new SeaRoute(stops);
    }

    /** The route of the stack (a new one if the stack has no data), or null if the stack is not a sea route chart. */
    @Nullable
    public static SeaRoute get(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof ItemSeaRoute) {
            SeaRoute route = stack.get(AwNpcItemComponents.SEA_ROUTE.get());
            return route == null ? new SeaRoute() : route.copy();
        }
        return null;
    }

    /** Stores a copy of the route in the stack (if it is a sea route chart). */
    public void write(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof ItemSeaRoute) {
            stack.set(AwNpcItemComponents.SEA_ROUTE.get(), copy());
        }
    }

    public List<Stop> getStops() {
        return stops;
    }

    public int size() {
        return stops.size();
    }

    public Stop get(int index) {
        return stops.get(index);
    }

    public boolean isFull() {
        return stops.size() >= MAX_STOPS;
    }

    /** Adds a stop at the end; false if the chart is full. */
    public boolean addStop(BlockPos dock) {
        if (isFull()) {
            return false;
        }
        stops.add(new Stop(dock));
        return true;
    }

    public void deleteStop(int index) {
        if (index >= 0 && index < stops.size()) {
            stops.remove(index);
        }
    }

    public void moveStopUp(int index) {
        if (index <= 0 || index >= stops.size()) {
            return;
        }
        stops.add(index - 1, stops.remove(index));
    }

    public void moveStopDown(int index) {
        if (index < 0 || index >= stops.size() - 1) {
            return;
        }
        stops.add(index + 1, stops.remove(index));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SeaRoute other && stops.equals(other.stops);
    }

    @Override
    public int hashCode() {
        return stops.hashCode();
    }
}
