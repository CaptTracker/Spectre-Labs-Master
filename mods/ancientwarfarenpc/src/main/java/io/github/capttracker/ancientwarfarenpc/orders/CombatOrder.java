/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/orders/CombatOrder.
 */
package io.github.capttracker.ancientwarfarenpc.orders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.capttracker.ancientwarfare.util.OrderingList;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcItemComponents;
import io.github.capttracker.ancientwarfarenpc.item.order.ItemCombatOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * The combat order slip: the patrol points of a soldier, all in one dimension. Stored in the {@code ancientwarfarenpc:combat_order}
 * data component ({@code dim}, {@code pointList}; the list is left out when it is empty).
 */
public class CombatOrder extends OrderingList<BlockPos> {
    public static final Codec<CombatOrder> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Level.RESOURCE_KEY_CODEC.optionalFieldOf("dim", Level.OVERWORLD).forGetter(CombatOrder::getPatrolDimension),
            OrderCodecs.BLOCK_POS.listOf().optionalFieldOf("pointList", List.of()).forGetter(order -> order.points)
    ).apply(instance, CombatOrder::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatOrder> STREAM_CODEC = OrderCodecs.stream(CODEC);

    private ResourceKey<Level> patrolDimension = Level.OVERWORLD;

    public CombatOrder() {
    }

    private CombatOrder(ResourceKey<Level> dimension, List<BlockPos> points) {
        this.patrolDimension = dimension;
        for (BlockPos point : points) {
            add(point);
        }
    }

    /** Adds a patrol point; a point in another dimension than the existing ones clears the list first. */
    public void addPatrolPoint(Level world, BlockPos point) {
        if (!world.dimension().equals(patrolDimension)) {
            clear();
            patrolDimension = world.dimension();
        }
        add(point);
    }

    public ResourceKey<Level> getPatrolDimension() {
        return patrolDimension;
    }

    public CombatOrder copy() {
        return new CombatOrder(patrolDimension, points);
    }

    /** The order of the stack (a new one if the stack has no data), or null if the stack is not a combat order. */
    @Nullable
    public static CombatOrder getCombatOrder(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof ItemCombatOrder) {
            CombatOrder order = stack.get(AwNpcItemComponents.COMBAT_ORDER.get());
            return order == null ? new CombatOrder() : order.copy();
        }
        return null;
    }

    /** Stores a copy of the order in the stack (if it is a combat order). */
    public void write(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof ItemCombatOrder) {
            stack.set(AwNpcItemComponents.COMBAT_ORDER.get(), copy());
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CombatOrder other && patrolDimension.equals(other.patrolDimension) && points.equals(other.points);
    }

    @Override
    public int hashCode() {
        return Objects.hash(patrolDimension, points);
    }
}
