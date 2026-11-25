/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: the codec pieces shared by the order slips (npc/orders) and the trade data (npc/trade). 1.12.2 wrote these by hand into
 * NBTTagCompounds; the codecs keep the same keys and value kinds (positions as longs, sides as bytes, enums as ordinals, item stacks with a slot).
 */
package io.github.capttracker.ancientwarfarenpc.orders;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Codecs and small helpers for the order and trade data models. */
public final class OrderCodecs {
    /** A position as one long (1.12.2: {@code BlockPos.toLong}; the 1.21.1 packing differs, saves are not compatible anyway). */
    public static final Codec<BlockPos> BLOCK_POS = Codec.LONG.xmap(BlockPos::of, BlockPos::asLong);

    /** A side as its ordinal in a byte (1.12.2: {@code EnumFacing.ordinal}; the order DOWN, UP, NORTH, SOUTH, WEST, EAST is the same). */
    public static final Codec<Direction> DIRECTION = Codec.BYTE.xmap(b -> Direction.values()[Math.floorMod((int) b, Direction.values().length)],
            d -> (byte) d.ordinal());

    private OrderCodecs() {}

    /**
     * A list codec that skips the elements it cannot decode instead of failing the whole list. 1.12.2 dropped the item stacks of mods that are
     * not installed (and the trades that used them) while reading; a strict list would lose the whole order slip for one unknown item.
     */
    public static <A> Codec<List<A>> lenientList(Codec<A> elementCodec) {
        Decoder<List<A>> decoder = new Decoder<>() {
            @Override
            public <T> DataResult<Pair<List<A>, T>> decode(DynamicOps<T> ops, T input) {
                return ops.getList(input).map(elements -> {
                    List<A> decoded = new ArrayList<>();
                    elements.accept(element -> elementCodec.parse(ops, element).result().ifPresent(decoded::add));
                    return Pair.of(decoded, ops.empty());
                });
            }
        };
        return Codec.of(elementCodec.listOf(), decoder);
    }

    /** An enum as its ordinal in an int; an ordinal out of range reads as the fallback (1.12.2 threw an array index exception). */
    public static <E extends Enum<E>> Codec<E> ordinal(E[] values, E fallback) {
        return Codec.INT.xmap(i -> i >= 0 && i < values.length ? values[i] : fallback, Enum::ordinal);
    }

    /** The network form of a codec (an NBT tag, written with the registry access of the connection so that stacks with components work). */
    public static <T> StreamCodec<RegistryFriendlyByteBuf, T> stream(Codec<T> codec) {
        return ByteBufCodecs.fromCodecWithRegistries(codec);
    }

    /** An item stack and the slot it sits in ({@code {slot: int, item: stack}}, 1.12.2: the stack tag with an added {@code slot}). */
    public record SlotStack(int slot, ItemStack stack) {
        public static final Codec<SlotStack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("slot", 0).forGetter(SlotStack::slot),
                ItemStack.CODEC.fieldOf("item").forGetter(SlotStack::stack)
        ).apply(instance, SlotStack::new));
    }

    /** The list of stacks with their slots as it is stored: unreadable stacks are skipped. */
    public static final Codec<List<SlotStack>> SLOT_STACKS = lenientList(SlotStack.CODEC);

    /** The non-empty stacks of the list with their slot (the sparse list 1.12.2 saved). */
    public static List<SlotStack> toSlots(List<ItemStack> stacks) {
        List<SlotStack> ret = new ArrayList<>();
        for (int i = 0; i < stacks.size(); i++) {
            if (!stacks.get(i).isEmpty()) {
                ret.add(new SlotStack(i, stacks.get(i).copy()));
            }
        }
        return ret;
    }

    /** Puts the stacks into their slots of the target list (slots out of its range are ignored). */
    public static void fromSlots(List<SlotStack> slots, NonNullList<ItemStack> target) {
        for (SlotStack slotStack : slots) {
            if (slotStack.slot() >= 0 && slotStack.slot() < target.size()) {
                target.set(slotStack.slot(), slotStack.stack().copy());
            }
        }
    }

    /** The stack or empty for an empty one (an empty stack cannot be encoded). */
    public static Optional<ItemStack> optional(ItemStack stack) {
        return stack.isEmpty() ? Optional.empty() : Optional.of(stack.copy());
    }

    public static ItemStack orEmpty(Optional<ItemStack> stack) {
        return stack.map(ItemStack::copy).orElse(ItemStack.EMPTY);
    }

    // ---- equality of stack lists (item stacks have no equals) ----

    public static boolean stacksEqual(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.matches(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    public static int stacksHash(List<ItemStack> stacks) {
        int hash = 1;
        for (ItemStack stack : stacks) {
            hash = 31 * hash + (stack.isEmpty() ? 0 : ItemStack.hashItemAndComponents(stack) * 31 + stack.getCount());
        }
        return hash;
    }

    public static NonNullList<ItemStack> copyOf(List<ItemStack> stacks) {
        NonNullList<ItemStack> ret = NonNullList.withSize(stacks.size(), ItemStack.EMPTY);
        for (int i = 0; i < stacks.size(); i++) {
            ret.set(i, stacks.get(i).copy());
        }
        return ret;
    }
}
