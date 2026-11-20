/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/crafting/ShapedResearchRecipe and ResearchRecipeFactory.parseShaped.
 */
package io.github.capttracker.ancientwarfare.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.capttracker.ancientwarfare.init.AwCoreCrafting;
import it.unimi.dsi.fastutil.chars.CharArraySet;
import it.unimi.dsi.fastutil.chars.CharSet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Research recipe with a fixed pattern. Like the original it is never mirrored, and the pattern may sit anywhere in
 * the grid as long as every other cell is empty.
 */
public class ShapedResearchRecipe extends ResearchRecipe {
    private static final int MAX_SIZE = 3;

    private final int width;
    private final int height;
    private final List<SizedIngredient> slots;
    private final int ingredientCount;

    public ShapedResearchRecipe(ResourceLocation research, Pattern pattern, ItemStack result) {
        this(research, pattern.width(), pattern.height(), pattern.slots(), result);
    }

    public ShapedResearchRecipe(ResourceLocation research, int width, int height, List<SizedIngredient> slots, ItemStack result) {
        super(research, result);
        this.width = width;
        this.height = height;
        this.slots = List.copyOf(slots);
        this.ingredientCount = (int) slots.stream().filter(s -> !ICraftingRecipe.isEmptySlot(s)).count();
    }

    @Override
    public List<SizedIngredient> slots() {
        return slots;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    public Pattern pattern() {
        return new Pattern(width, height, slots);
    }

    @Override
    public boolean canCraftInDimensions(int gridWidth, int gridHeight) {
        return gridWidth >= width && gridHeight >= height;
    }

    /** Works on a full 3x3 input and on one trimmed to the used area (what {@link CraftingInput#of} produces). */
    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() != ingredientCount) {
            return false;
        }
        for (int x = 0; x <= input.width() - width; x++) {
            for (int y = 0; y <= input.height() - height; y++) {
                if (checkMatch(input, x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Based on the 1.12.2 ShapedRecipes.checkMatch (without mirroring). */
    private boolean checkMatch(CraftingInput input, int startX, int startY) {
        for (int x = 0; x < input.width(); x++) {
            for (int y = 0; y < input.height(); y++) {
                int subX = x - startX;
                int subY = y - startY;
                SizedIngredient target = ICraftingRecipe.EMPTY_SLOT;
                if (subX >= 0 && subY >= 0 && subX < width && subY < height) {
                    target = slots.get(subX + subY * width);
                }
                ItemStack stack = input.getItem(x, y);
                boolean ok = ICraftingRecipe.isEmptySlot(target) ? stack.isEmpty() : target.test(stack);
                if (!ok) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return AwCoreCrafting.SHAPED_RESEARCH_SERIALIZER.get();
    }

    /** The pattern of the recipe: size plus the ingredient of every cell. */
    public record Pattern(int width, int height, List<SizedIngredient> slots) {
        /** The file form: single-character keys and rows of them. */
        private record Data(Map<Character, SizedIngredient> key, List<String> pattern) {
            private static final Codec<List<String>> PATTERN_CODEC = Codec.STRING.listOf().comapFlatMap(rows -> {
                if (rows.size() > MAX_SIZE) {
                    return DataResult.error(() -> "Invalid pattern: too many rows, " + MAX_SIZE + " is maximum");
                } else if (rows.isEmpty()) {
                    return DataResult.error(() -> "Invalid pattern: empty pattern not allowed");
                }
                int length = rows.getFirst().length();
                for (String row : rows) {
                    if (row.length() > MAX_SIZE) {
                        return DataResult.error(() -> "Invalid pattern: too many columns, " + MAX_SIZE + " is maximum");
                    }
                    if (row.length() != length) {
                        return DataResult.error(() -> "Invalid pattern: each row must be the same width");
                    }
                }
                return DataResult.success(rows);
            }, Function.identity());

            private static final Codec<Character> SYMBOL_CODEC = Codec.STRING.comapFlatMap(text -> {
                if (text.length() != 1) {
                    return DataResult.error(() -> "Invalid key entry: '" + text + "' is an invalid symbol (must be 1 character only).");
                }
                return " ".equals(text) ? DataResult.error(() -> "Invalid key entry: ' ' is a reserved symbol.") : DataResult.success(text.charAt(0));
            }, String::valueOf);

            static final MapCodec<Data> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    ExtraCodecs.strictUnboundedMap(SYMBOL_CODEC, SizedIngredient.FLAT_CODEC).fieldOf("key").forGetter(Data::key),
                    PATTERN_CODEC.fieldOf("pattern").forGetter(Data::pattern)
            ).apply(instance, Data::new));
        }

        public static final MapCodec<Pattern> MAP_CODEC = Data.MAP_CODEC.flatXmap(Pattern::unpack, Pattern::pack);

        private static DataResult<Pattern> unpack(Data data) {
            String[] rows = shrink(data.pattern());
            if (rows.length == 0) {
                return DataResult.error(() -> "Invalid pattern: empty pattern not allowed");
            }
            int width = rows[0].length();
            int height = rows.length;
            List<SizedIngredient> slots = new ArrayList<>(width * height);
            CharSet unused = new CharArraySet(data.key().keySet());
            for (String row : rows) {
                for (int i = 0; i < row.length(); i++) {
                    char symbol = row.charAt(i);
                    SizedIngredient ingredient = symbol == ' ' ? ICraftingRecipe.EMPTY_SLOT : data.key().get(symbol);
                    if (ingredient == null) {
                        return DataResult.error(() -> "Pattern references symbol '" + symbol + "' but it's not defined in the key");
                    }
                    unused.remove(symbol);
                    slots.add(ingredient);
                }
            }
            return unused.isEmpty()
                    ? DataResult.success(new Pattern(width, height, List.copyOf(slots)))
                    : DataResult.error(() -> "Key defines symbols that aren't used in pattern: " + unused);
        }

        /** Used when a recipe built in code or received from the server is written out again: letters are assigned per distinct ingredient. */
        private static DataResult<Data> pack(Pattern pattern) {
            Map<SizedIngredient, Character> symbols = new LinkedHashMap<>();
            List<String> rows = new ArrayList<>();
            for (int y = 0; y < pattern.height(); y++) {
                StringBuilder row = new StringBuilder();
                for (int x = 0; x < pattern.width(); x++) {
                    SizedIngredient slot = pattern.slots().get(x + y * pattern.width());
                    if (ICraftingRecipe.isEmptySlot(slot)) {
                        row.append(' ');
                    } else {
                        row.append(symbols.computeIfAbsent(slot, s -> (char) ('A' + symbols.size())));
                    }
                }
                rows.add(row.toString());
            }
            Map<Character, SizedIngredient> key = new LinkedHashMap<>();
            symbols.forEach((ingredient, symbol) -> key.put(symbol, ingredient));
            return DataResult.success(new Data(key, rows));
        }

        /** Removes empty outer rows and columns, like vanilla shaped recipes do. */
        private static String[] shrink(List<String> pattern) {
            int minColumn = Integer.MAX_VALUE;
            int maxColumn = -1;
            int firstRow = -1;
            int lastRow = -1;
            for (int i = 0; i < pattern.size(); i++) {
                String row = pattern.get(i);
                int first = 0;
                while (first < row.length() && row.charAt(first) == ' ') {
                    first++;
                }
                int last = row.length() - 1;
                while (last >= 0 && row.charAt(last) == ' ') {
                    last--;
                }
                if (last < 0) {
                    continue;
                }
                minColumn = Math.min(minColumn, first);
                maxColumn = Math.max(maxColumn, last);
                if (firstRow < 0) {
                    firstRow = i;
                }
                lastRow = i;
            }
            if (firstRow < 0) {
                return new String[0];
            }
            String[] rows = new String[lastRow - firstRow + 1];
            for (int i = 0; i < rows.length; i++) {
                rows[i] = pattern.get(firstRow + i).substring(minColumn, maxColumn + 1);
            }
            return rows;
        }
    }

    public static class Serializer implements RecipeSerializer<ShapedResearchRecipe> {
        public static final MapCodec<ShapedResearchRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ResearchRecipe.RESEARCH_ID_CODEC.fieldOf("research").forGetter(ResearchRecipe::research),
                Pattern.MAP_CODEC.forGetter(ShapedResearchRecipe::pattern),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(ResearchRecipe::result)
        ).apply(instance, ShapedResearchRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, ShapedResearchRecipe> STREAM_CODEC = StreamCodec.of(
                Serializer::toNetwork, Serializer::fromNetwork);

        @Override
        public MapCodec<ShapedResearchRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ShapedResearchRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static ShapedResearchRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
            ResourceLocation research = buf.readResourceLocation();
            int width = buf.readVarInt();
            int height = buf.readVarInt();
            List<SizedIngredient> slots = new ArrayList<>(width * height);
            for (int i = 0; i < width * height; i++) {
                slots.add(SizedIngredient.STREAM_CODEC.decode(buf));
            }
            return new ShapedResearchRecipe(research, width, height, slots, ItemStack.STREAM_CODEC.decode(buf));
        }

        private static void toNetwork(RegistryFriendlyByteBuf buf, ShapedResearchRecipe recipe) {
            buf.writeResourceLocation(recipe.research());
            buf.writeVarInt(recipe.width);
            buf.writeVarInt(recipe.height);
            for (SizedIngredient slot : recipe.slots) {
                SizedIngredient.STREAM_CODEC.encode(buf, slot);
            }
            ItemStack.STREAM_CODEC.encode(buf, recipe.result());
        }
    }
}
