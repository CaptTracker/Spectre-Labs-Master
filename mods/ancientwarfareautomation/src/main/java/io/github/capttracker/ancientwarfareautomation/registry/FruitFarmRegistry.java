/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/registry/FruitFarmRegistry.
 */
package io.github.capttracker.ancientwarfareautomation.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfare.registry.IRegistryDataParser;
import io.github.capttracker.ancientwarfare.util.parsing.BlockStateMatcher;
import io.github.capttracker.ancientwarfare.util.parsing.JsonHelper;
import io.github.capttracker.ancientwarfare.util.parsing.PropertyState;
import io.github.capttracker.ancientwarfare.util.parsing.PropertyStateMatcher;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.fruitfarm.FruitBreakOnly;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.fruitfarm.FruitCocoa;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.fruitfarm.FruitPicked;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.fruitfarm.FruitPickedDrop;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.fruitfarm.FruitPickedRemoveOne;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.fruitfarm.IFruit;
import net.minecraft.core.BlockPos;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * What the fruit farm knows: the fruits ({@link IFruit}) it can pick and, for those that need planting (cocoa),
 * the items that plant them.
 *
 * <p>{@link FruitCocoa} is built in (the only vanilla fruit of 1.12.2; sweet berries and glow berries are new
 * candidates, see the notes); everything else comes from {@code fruit_blocks} data files read by the
 * {@link FruitParser} ({@code data/<namespace>/registry/**}); a reload clears what the parser loaded.
 */
public class FruitFarmRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();

    private FruitFarmRegistry() {
    }

    private static final String FRUIT_ELEMENT = "fruit";

    /** the built-in fruits */
    private static final Set<IFruit> builtInFruits = new LinkedHashSet<>();
    /** filled by the parser */
    private static final Set<IFruit> dataFruits = new CopyOnWriteArraySet<>();

    static {
        builtInFruits.add(new FruitCocoa());
    }

    private static void registerFruit(IFruit pickable) {
        dataFruits.add(pickable);
    }

    private static java.util.stream.Stream<IFruit> allFruits() {
        Set<IFruit> all = new LinkedHashSet<>(builtInFruits);
        all.addAll(dataFruits);
        return all.stream();
    }

    public static IFruit getPickable(BlockState state) {
        return state.isAir() ? NO_FRUIT : allFruits().filter(p -> p.matches(state)).findFirst().orElse(NO_FRUIT);
    }

    public static boolean isPlantable(ItemStack stack) {
        return allFruits().anyMatch(p -> p.isPlantable() && p.matches(stack));
    }

    public static IFruit getPlantable(ItemStack stack) {
        return stack.isEmpty() ? NO_FRUIT : allFruits().filter(p -> p.isPlantable() && p.matches(stack)).findFirst().orElse(NO_FRUIT);
    }

    /** Builds a picked fruit from the matchers read from its json entry. */
    @FunctionalInterface
    private interface PickedFactory {
        IFruit create(BlockStateMatcher stateMatcher, PropertyStateMatcher ripeStateMatcher, PropertyState<?, ?> newState);
    }

    /** Reads {@code fruits}: entries with a {@code type} (picked_remove_one, picked, picked_drop, break_only). */
    public static class FruitParser implements IRegistryDataParser {

        @Override
        public String getName() {
            return "fruit_blocks";
        }

        @Override
        public void beginLoad() {
            dataFruits.clear();
        }

        @Override
        public void parse(JsonObject json) {
            JsonArray fruits = GsonHelper.getAsJsonArray(json, "fruits");
            for (JsonElement e : fruits) {
                JsonObject fruit = GsonHelper.convertToJsonObject(e, "fruits entry");
                try {
                    parseFruit(fruit);
                } catch (RuntimeException ex) {
                    LOGGER.error("Skipping invalid fruits entry {}", fruit, ex);
                }
            }
        }

        private void parseFruit(JsonObject json) {
            String type = GsonHelper.getAsString(json, "type");
            switch (type) {
                case "picked_remove_one":
                    PickedRemoveOne.parse(json);
                    return;
                case "picked":
                    Picked.parse(json);
                    return;
                case "picked_drop":
                    PickedDrop.parse(json);
                    return;
                case "break_only":
                    BreakOnly.parse(json);
                    return;
                default:
            }
        }

        private static class BreakOnly {
            private BreakOnly() {
            }

            public static void parse(JsonObject json) {
                registerFruit(new FruitBreakOnly(JsonHelper.getBlockStateMatcher(json, FRUIT_ELEMENT)));
            }
        }

        private static class PickedDrop {
            private PickedDrop() {
            }

            public static void parse(JsonObject json) {
                registerFruit(Picked.parsePicked(json, (f, r, n) -> new FruitPickedDrop(f, r, n, JsonHelper.getItemStack(json, "drop"))));
            }
        }

        private static class PickedRemoveOne {
            private PickedRemoveOne() {
            }

            public static void parse(JsonObject json) {
                registerFruit(Picked.parsePicked(json, FruitPickedRemoveOne::new));
            }
        }

        private static class Picked {
            private Picked() {
            }

            public static void parse(JsonObject json) {
                registerFruit(parsePicked(json, FruitPicked::new));
            }

            private static IFruit parsePicked(JsonObject json, PickedFactory instantiate) {
                BlockState fruitState = JsonHelper.getBlockState(json, FRUIT_ELEMENT);
                BlockStateMatcher stateMatcher = JsonHelper.getBlockStateMatcher(json, FRUIT_ELEMENT);
                PropertyStateMatcher ripeStateMatcher = JsonHelper.getPropertyStateMatcher(fruitState, json, "ripe");
                PropertyState<?, ?> newState = JsonHelper.getPropertyState(fruitState, json, "new");

                return instantiate.create(stateMatcher, ripeStateMatcher, newState);
            }
        }
    }

    private static final IFruit NO_FRUIT = new IFruit() {
        @Override
        public boolean isRipe(BlockState state) {
            return false;
        }

        @Override
        public boolean matches(BlockState state) {
            return false;
        }

        @Override
        public boolean pick(Level world, BlockState state, BlockPos pos, int fortune, IItemHandler inventory) {
            return false;
        }

        @Override
        public boolean isPlantable() {
            return false;
        }

        @Override
        public boolean canPlant(Level world, BlockPos currentPos, BlockState state) {
            return false;
        }

        @Override
        public boolean plant(Level world, BlockPos plantPos) {
            return false;
        }
    };
}
