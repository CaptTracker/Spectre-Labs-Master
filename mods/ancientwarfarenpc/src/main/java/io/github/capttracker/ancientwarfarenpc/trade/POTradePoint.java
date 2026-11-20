/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/trade/POTradePoint.
 */
package io.github.capttracker.ancientwarfarenpc.trade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.capttracker.ancientwarfarenpc.orders.OrderCodecs;
import net.minecraft.core.BlockPos;

import java.util.Objects;

/** One stop of a trader's route: where it stands, how long it stays (ticks) and whether it refills its upkeep there. */
public final class POTradePoint {
    /** The tags of 1.12.2: {@code pos}, {@code delay}, {@code upkeep}. */
    public static final Codec<POTradePoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            OrderCodecs.BLOCK_POS.optionalFieldOf("pos", BlockPos.ZERO).forGetter(POTradePoint::getPosition),
            Codec.INT.optionalFieldOf("delay", 0).forGetter(POTradePoint::getDelay),
            Codec.BOOL.optionalFieldOf("upkeep", false).forGetter(POTradePoint::shouldUpkeep)
    ).apply(instance, POTradePoint::new));

    private final BlockPos position;
    private int delay;
    private boolean shouldUpkeep;//if the npc should refill upkeep at this stop

    POTradePoint(BlockPos position, int delay, boolean shouldUpkeep) {
        this.position = position;
        this.delay = delay;
        this.shouldUpkeep = shouldUpkeep;
    }

    public POTradePoint copy() {
        return new POTradePoint(position, delay, shouldUpkeep);
    }

    public BlockPos getPosition() {
        return position;
    }

    public int getDelay() {
        return delay;
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }

    public void setShouldUpkeep(boolean val) {
        this.shouldUpkeep = val;
    }

    public boolean shouldUpkeep() {
        return shouldUpkeep;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof POTradePoint other && delay == other.delay && shouldUpkeep == other.shouldUpkeep && position.equals(other.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, delay, shouldUpkeep);
    }
}
