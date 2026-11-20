/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/registry/TreeFarmRegistry.
 */
package io.github.capttracker.ancientwarfareautomation.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfare.registry.IRegistryDataParser;
import io.github.capttracker.ancientwarfare.util.parsing.BlockStateMatcher;
import io.github.capttracker.ancientwarfare.util.parsing.JsonHelper;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.treefarm.ChorusFlowerDrop;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.treefarm.ChorusScanner;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.treefarm.DefaultTreeScanner;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.treefarm.IBlockExtraDrop;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.treefarm.ISapling;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.treefarm.ITreeScanner;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.treefarm.Sapling;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * What the tree farm knows: which items can be planted ({@link ISapling}), which blocks trees can grow on, how to
 * find out which blocks belong to a tree ({@link ITreeScanner}) and which blocks drop something extra when cut.
 *
 * <p>Built in: sapling blocks and mushrooms as plantable items, the chorus scanner and the chorus flower extra drop, and
 * the fallback scanner for trees that have no scanner of their own: trunks are blocks of the {@code minecraft:logs}
 * tag (1.12.2: every {@code Material.WOOD} block except the tree farm itself, which counted planks, chests and crafting
 * tables as trunks), leaves are blocks of the {@code minecraft:leaves} tag. Data files add the rest:
 * {@code saplings}, {@code tree_soil_blocks} and {@code tree_scanners} ({@code data/<namespace>/registry/**});
 * a reload clears what the parsers loaded.
 */
public class TreeFarmRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();

    private TreeFarmRegistry() {}

    private static final ITreeScanner DEFAULT_TREE_SCANNER =
            new DefaultTreeScanner(st -> st.is(BlockTags.LOGS) && st.getBlock() != AwAutomationBase.TREE_FARM.get(),
                    sl -> sl.is(BlockTags.LEAVES));

    /** the built-in plantables */
    private static final Set<ISapling> builtInSaplings = new HashSet<>();
    /** filled by the sapling parser */
    private static final Set<ISapling> dataSaplings = new HashSet<>();
    /** filled by the soil parser */
    private static final Set<BlockStateMatcher> soilBlocks = new HashSet<>();
    private static final Set<IBlockExtraDrop> extraBlockDrops = new HashSet<>();
    /** built in, the first one is tried first */
    private static final List<ITreeScanner> builtInTreeScanners = new ArrayList<>();
    /** filled by the scanner parser, the first one is tried first */
    private static final List<ITreeScanner> treeScanners = new CopyOnWriteArrayList<>();

    static {
        builtInSaplings.add(new Sapling(s -> s.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof SaplingBlock, false));
        builtInSaplings.add(new Sapling(s -> s.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof MushroomBlock, false));

        extraBlockDrops.add(new ChorusFlowerDrop());

        builtInTreeScanners.add(new ChorusScanner());
    }

    private static void registerTreeScanner(ITreeScanner treeScanner) {
        treeScanners.add(0, treeScanner);
    }

    public static ITreeScanner getTreeScanner(BlockState state) {
        return getRegisteredTreeScanner(state).orElse(DEFAULT_TREE_SCANNER);
    }

    public static Optional<ITreeScanner> getRegisteredTreeScanner(BlockState state) {
        Optional<ITreeScanner> fromData = treeScanners.stream().filter(ts -> ts.matches(state)).findFirst();
        if (fromData.isPresent()) {
            return fromData;
        }
        return builtInTreeScanners.stream().filter(ts -> ts.matches(state)).findFirst();
    }

    public static Optional<ISapling> getSapling(ItemStack stack) {
        Optional<ISapling> fromData = dataSaplings.stream().filter(s -> s.matches(stack)).findFirst();
        if (fromData.isPresent()) {
            return fromData;
        }
        return builtInSaplings.stream().filter(s -> s.matches(stack)).findFirst();
    }

    public static boolean isPlantable(ItemStack stack) {
        return getSapling(stack).isPresent();
    }

    public static boolean isSoil(BlockState state) {
        return soilBlocks.stream().anyMatch(m -> m.test(state));
    }

    public static IBlockExtraDrop getBlockExtraDrop(BlockState state) {
        return extraBlockDrops.stream().filter(b -> b.matches(state)).findFirst().orElse(EMPTY_EXTRA_DROP);
    }

    /** Reads {@code saplings}: an item matcher and an optional {@code right_click} flag (plant by using the item instead of placing it). */
    public static class PlantableParser implements IRegistryDataParser {

        @Override
        public String getName() {
            return "saplings";
        }

        @Override
        public void beginLoad() {
            dataSaplings.clear();
        }

        @Override
        public void parse(JsonObject json) {
            JsonArray saplings = GsonHelper.getAsJsonArray(json, "saplings");

            for (JsonElement e : saplings) {
                JsonObject saplingDefinition = GsonHelper.convertToJsonObject(e, "saplings entry");
                try {
                    dataSaplings.add(new Sapling(JsonHelper.getItemStackMatcher(GsonHelper.getAsJsonObject(saplingDefinition, "sapling")),
                            saplingDefinition.has("right_click") && GsonHelper.getAsBoolean(saplingDefinition, "right_click")));
                } catch (RuntimeException ex) {
                    LOGGER.error("Skipping invalid saplings entry {}", saplingDefinition, ex);
                }
            }
        }
    }

    /** Reads {@code soils}: block state matchers of the blocks saplings can be planted on. */
    public static class SoilParser implements IRegistryDataParser {

        @Override
        public String getName() {
            return "tree_soil_blocks";
        }

        @Override
        public void beginLoad() {
            soilBlocks.clear();
        }

        @Override
        public void parse(JsonObject json) {
            JsonArray soils = GsonHelper.getAsJsonArray(json, "soils");

            for (JsonElement t : soils) {
                JsonObject soil = GsonHelper.convertToJsonObject(t, "soils entry");
                try {
                    soilBlocks.add(JsonHelper.getBlockStateMatcher(soil));
                } catch (RuntimeException ex) {
                    LOGGER.error("Skipping invalid soils entry {}", soil, ex);
                }
            }
        }
    }

    /** Reads {@code tree_scanners}: trunk and leaf matchers with a leaf distance and the way the scan walks from block to block. */
    public static class TreeScannerParser implements IRegistryDataParser {
        @Override
        public String getName() {
            return "tree_scanners";
        }

        @Override
        public void beginLoad() {
            treeScanners.clear();
        }

        @Override
        public void parse(JsonObject json) {
            JsonArray scanners = GsonHelper.getAsJsonArray(json, "tree_scanners");

            for (JsonElement ts : scanners) {
                JsonObject treeScanner = GsonHelper.convertToJsonObject(ts, "tree_scanners entry");
                try {
                    // "default" is the only type; unknown types are read as it, as in the original
                    DefaultSearchParser.parse(treeScanner);
                } catch (RuntimeException ex) {
                    LOGGER.error("Skipping invalid tree_scanners entry {}", treeScanner, ex);
                }
            }
        }

        private static class DefaultSearchParser {
            private DefaultSearchParser() {}

            public static void parse(JsonObject treeScanner) {
                Predicate<BlockState> trunkMatcher = JsonHelper.getBlockStateMatcher(treeScanner, "trunks", "trunk");
                Predicate<BlockState> leafMatcher = JsonHelper.getBlockStateMatcher(treeScanner, "leaves", "leaf");

                int maxLeafDistance = GsonHelper.getAsInt(treeScanner, "max_leaf_distance");

                DefaultTreeScanner.INextPositionGetter nextPosGetter = parseNextPositionGetter(treeScanner);

                // scanners with the same trunk matcher are merged: the leaf matcher is added to the existing scanner
                Optional<DefaultTreeScanner> currentScanner = treeScanners.stream()
                        .filter(m -> m instanceof DefaultTreeScanner scanner && scanner.getTrunkMatcher().hashCode() == trunkMatcher.hashCode())
                        .map(m -> (DefaultTreeScanner) m).findFirst();

                if (currentScanner.isPresent()) {
                    currentScanner.get().addLeafMatcher(leafMatcher);
                } else {
                    registerTreeScanner(new DefaultTreeScanner(trunkMatcher, leafMatcher, nextPosGetter, maxLeafDistance));
                }
            }

            private static DefaultTreeScanner.INextPositionGetter parseNextPositionGetter(JsonObject treeScanner) {
                switch (GsonHelper.getAsString(treeScanner, "next_block_search")) {
                    case "all_up_or_level":
                        return DefaultTreeScanner.ALL_UP_OR_LEVEL;
                    case "all_around":
                        return DefaultTreeScanner.ALL_AROUND;
                    case "connected_around":
                        return DefaultTreeScanner.CONNECTED_AROUND;
                    case "connected_down_or_level":
                        return DefaultTreeScanner.CONNECTED_DOWN_OR_LEVEL;
                    case "connected_up_or_level":
                    default:
                        return DefaultTreeScanner.CONNECTED_UP_OR_LEVEL;
                }
            }
        }
    }

    private static final IBlockExtraDrop EMPTY_EXTRA_DROP = new IBlockExtraDrop() {
        @Override
        public boolean matches(BlockState state) {
            return true;
        }

        @Override
        public NonNullList<ItemStack> getDrops(BlockGetter world, BlockPos pos, BlockState state, int fortune) {
            return NonNullList.create();
        }
    };
}
