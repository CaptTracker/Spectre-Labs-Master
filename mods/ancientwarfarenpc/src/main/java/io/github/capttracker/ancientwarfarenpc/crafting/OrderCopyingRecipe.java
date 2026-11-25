/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/crafting/AWNpcCrafting.OrderCopyingRecipe.
 */
package io.github.capttracker.ancientwarfarenpc.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcs;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Copies an order slip: exactly two stacks of the order item and nothing else in the grid give two of the first one (with its orders), so
 * the second, empty slip is overwritten. Registered as the recipe type {@code ancientwarfarenpc:order_copy}; the four recipes
 * ({@code upkeep_order_copy}, {@code routing_order_copy}, {@code combat_order_copy}, {@code work_order_copy}) are data files that name the item.
 * There is no copy recipe for the trade order, as in the original.
 *
 * <p>1.12.2 returned a copy of the second stack carrying the first one's tag, count 2. All data of an order is in item data components now, and
 * both stacks are the same item, so the result is simply the first stack at count 2 (an unset first slip gives a blank result, as before).
 */
public class OrderCopyingRecipe extends CustomRecipe {
    private final Item item;

    public OrderCopyingRecipe(CraftingBookCategory category, Item item) {
        super(category);
        this.item = item;
    }

    public Item getItem() {
        return item;
    }

    /** The two slips in the grid, in slot order, or null if the grid does not hold exactly two of the order item. */
    private ItemStack[] findOrders(CraftingInput input) {
        ItemStack order1 = ItemStack.EMPTY;
        ItemStack order2 = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (!stack.is(item)) {
                return null;
            }
            if (order1.isEmpty()) {
                order1 = stack;
            } else if (order2.isEmpty()) {
                order2 = stack;
            } else {
                return null;
            }
        }
        return order1.isEmpty() || order2.isEmpty() ? null : new ItemStack[]{order1, order2};
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return findOrders(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack[] orders = findOrders(input);
        return orders == null ? ItemStack.EMPTY : orders[0].copyWithCount(2);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return AwNpcs.ORDER_COPY_SERIALIZER.get();
    }

    public static class Serializer implements RecipeSerializer<OrderCopyingRecipe> {
        private static final MapCodec<OrderCopyingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                CraftingBookCategory.CODEC.optionalFieldOf("category", CraftingBookCategory.MISC).forGetter(CustomRecipe::category),
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(OrderCopyingRecipe::getItem)
        ).apply(instance, OrderCopyingRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, OrderCopyingRecipe> STREAM_CODEC = StreamCodec.composite(
                CraftingBookCategory.STREAM_CODEC, CustomRecipe::category,
                ByteBufCodecs.registry(Registries.ITEM), OrderCopyingRecipe::getItem,
                OrderCopyingRecipe::new);

        @Override
        public MapCodec<OrderCopyingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, OrderCopyingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
