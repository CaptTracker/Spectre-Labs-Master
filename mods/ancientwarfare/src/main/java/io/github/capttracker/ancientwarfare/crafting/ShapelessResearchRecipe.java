/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/crafting/ShapelessResearchRecipe.
 */
package io.github.capttracker.ancientwarfare.crafting;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.capttracker.ancientwarfare.init.AwCoreCrafting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.common.util.RecipeMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Research recipe where the ingredients can sit anywhere in the grid. */
public class ShapelessResearchRecipe extends ResearchRecipe {
    private static final int MAX_INGREDIENTS = 9;

    private final List<SizedIngredient> ingredients;

    public ShapelessResearchRecipe(ResourceLocation research, List<SizedIngredient> ingredients, ItemStack result) {
        super(research, result);
        this.ingredients = List.copyOf(ingredients);
    }

    @Override
    public List<SizedIngredient> slots() {
        return ingredients;
    }

    @Override
    public int width() {
        return 3;
    }

    @Override
    public int height() {
        return 3;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= ingredients.size();
    }

    /**
     * Every non-empty grid stack must be paired with its own ingredient and every ingredient with a stack.
     * The original took the first ingredient that accepted a stack, so two ingredients that accept the same item
     * could never both be satisfied; a proper pairing (NeoForge's RecipeMatcher) fixes that.
     */
    @Override
    public boolean matches(CraftingInput input, Level level) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack stack : input.items()) {
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        if (stacks.size() != ingredients.size()) {
            return false;
        }
        List<Predicate<ItemStack>> tests = new ArrayList<>();
        for (SizedIngredient ingredient : ingredients) {
            tests.add(ingredient::test);
        }
        return RecipeMatcher.findMatches(stacks, tests) != null;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return AwCoreCrafting.SHAPELESS_RESEARCH_SERIALIZER.get();
    }

    public static class Serializer implements RecipeSerializer<ShapelessResearchRecipe> {
        public static final MapCodec<ShapelessResearchRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ResearchRecipe.RESEARCH_ID_CODEC.fieldOf("research").forGetter(ResearchRecipe::research),
                SizedIngredient.FLAT_CODEC.listOf().validate(list -> {
                    if (list.isEmpty()) {
                        return DataResult.error(() -> "No ingredients for shapeless research recipe");
                    }
                    return list.size() > MAX_INGREDIENTS
                            ? DataResult.error(() -> "Too many ingredients for shapeless research recipe, " + MAX_INGREDIENTS + " is the maximum")
                            : DataResult.success(list);
                }).fieldOf("ingredients").forGetter(ShapelessResearchRecipe::slots),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(ResearchRecipe::result)
        ).apply(instance, ShapelessResearchRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, ShapelessResearchRecipe> STREAM_CODEC = StreamCodec.of(
                Serializer::toNetwork, Serializer::fromNetwork);

        @Override
        public MapCodec<ShapelessResearchRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ShapelessResearchRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static ShapelessResearchRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
            ResourceLocation research = buf.readResourceLocation();
            int size = buf.readVarInt();
            List<SizedIngredient> ingredients = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                ingredients.add(SizedIngredient.STREAM_CODEC.decode(buf));
            }
            return new ShapelessResearchRecipe(research, ingredients, ItemStack.STREAM_CODEC.decode(buf));
        }

        private static void toNetwork(RegistryFriendlyByteBuf buf, ShapelessResearchRecipe recipe) {
            buf.writeResourceLocation(recipe.research());
            buf.writeVarInt(recipe.ingredients.size());
            for (SizedIngredient ingredient : recipe.ingredients) {
                SizedIngredient.STREAM_CODEC.encode(buf, ingredient);
            }
            ItemStack.STREAM_CODEC.encode(buf, recipe.result());
        }
    }
}
