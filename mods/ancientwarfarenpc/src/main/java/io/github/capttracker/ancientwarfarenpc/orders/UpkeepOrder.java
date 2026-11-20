/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/orders/UpkeepOrder.
 */
package io.github.capttracker.ancientwarfarenpc.orders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfare.util.WorldTools;
import io.github.capttracker.ancientwarfarenpc.block.BlockTownHall;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcItemComponents;
import io.github.capttracker.ancientwarfarenpc.item.order.ItemUpkeepOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Objects;
import java.util.Optional;

/**
 * The upkeep order slip: one inventory the NPC fetches its food from. Stored in the {@code ancientwarfarenpc:upkeep_order} data component.
 * As in 1.12.2 the data is only written when a point is set ({@code upkeepPosition}, {@code dim}, {@code side}, {@code upkeepAmount}).
 */
public class UpkeepOrder {
    public static final int DEFAULT_UPKEEP_AMOUNT = 6000;

    public static final Codec<UpkeepOrder> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            OrderCodecs.BLOCK_POS.optionalFieldOf("upkeepPosition").forGetter(UpkeepOrder::getUpkeepPosition),
            Level.RESOURCE_KEY_CODEC.optionalFieldOf("dim", Level.OVERWORLD).forGetter(UpkeepOrder::getUpkeepDimension),
            OrderCodecs.DIRECTION.optionalFieldOf("side", Direction.DOWN).forGetter(UpkeepOrder::getUpkeepBlockSide),
            Codec.INT.optionalFieldOf("upkeepAmount", DEFAULT_UPKEEP_AMOUNT).forGetter(UpkeepOrder::getUpkeepAmount)
    ).apply(instance, UpkeepOrder::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, UpkeepOrder> STREAM_CODEC = OrderCodecs.stream(CODEC);

    private BlockPos upkeepPosition;
    private ResourceKey<Level> upkeepDimension = Level.OVERWORLD;
    private Direction blockSide = Direction.DOWN;
    private int upkeepAmount = DEFAULT_UPKEEP_AMOUNT;

    public UpkeepOrder() {
    }

    private UpkeepOrder(Optional<BlockPos> position, ResourceKey<Level> dimension, Direction side, int amount) {
        // like 1.12.2, nothing else is read if there is no position
        if (position.isPresent()) {
            upkeepPosition = position.get();
            upkeepDimension = dimension;
            blockSide = side;
            upkeepAmount = amount;
        }
    }

    public void changeBlockSide() {
        blockSide = Direction.values()[(blockSide.ordinal() + 1) % Direction.values().length];
    }

    public void removeUpkeepPoint() {
        upkeepPosition = null;
        blockSide = Direction.DOWN;
        upkeepDimension = Level.OVERWORLD;
        upkeepAmount = DEFAULT_UPKEEP_AMOUNT;
    }

    public void setUpkeepAmount(int amt) {
        this.upkeepAmount = amt;
    }

    public Direction getUpkeepBlockSide() {
        return blockSide;
    }

    public ResourceKey<Level> getUpkeepDimension() {
        return upkeepDimension;
    }

    public Optional<BlockPos> getUpkeepPosition() {
        return Optional.ofNullable(upkeepPosition);
    }

    public final int getUpkeepAmount() {
        return upkeepAmount;
    }

    /** Sets the upkeep point if the block is an inventory (and, with {@code allow_upkeep_any_inventory} off, a town hall). */
    public boolean addUpkeepPosition(Level world, BlockPos pos) {
        if (WorldTools.getTile(world, pos, BlockEntity.class).map(InventoryTools::isInventory).orElse(false)) {
            if (!AWNPCStatics.npcAllowUpkeepAnyInventory && !(world.getBlockState(pos).getBlock() instanceof BlockTownHall)) {
                return false;
            }
            upkeepPosition = pos;
            upkeepDimension = world.dimension();
            blockSide = Direction.DOWN;
            upkeepAmount = DEFAULT_UPKEEP_AMOUNT;
            return true;
        }
        return false;
    }

    public UpkeepOrder copy() {
        UpkeepOrder ret = new UpkeepOrder();
        ret.upkeepPosition = upkeepPosition;
        ret.upkeepDimension = upkeepDimension;
        ret.blockSide = blockSide;
        ret.upkeepAmount = upkeepAmount;
        return ret;
    }

    /** The order of the stack (a new one if the stack has no data), empty if the stack is not an upkeep order. */
    public static Optional<UpkeepOrder> getUpkeepOrder(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof ItemUpkeepOrder) {
            UpkeepOrder order = stack.get(AwNpcItemComponents.UPKEEP_ORDER.get());
            return Optional.of(order == null ? new UpkeepOrder() : order.copy());
        }
        return Optional.empty();
    }

    /** Stores a copy of the order in the stack (if it is an upkeep order). */
    public void write(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof ItemUpkeepOrder) {
            stack.set(AwNpcItemComponents.UPKEEP_ORDER.get(), copy());
        }
    }

    @Override
    public String toString() {
        return "Upkeep Orders[" + upkeepPosition + "]";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof UpkeepOrder other && upkeepAmount == other.upkeepAmount && blockSide == other.blockSide
                && Objects.equals(upkeepPosition, other.upkeepPosition) && upkeepDimension.equals(other.upkeepDimension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(upkeepPosition, upkeepDimension, blockSide, upkeepAmount);
    }
}
