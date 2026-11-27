/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): what a build costs and the settlement storage that pays for it.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementApi;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The materials of builds: which item pays for a block ({@link #itemFor}, {@link #countFor}), what a project still needs ({@link #remaining}), what the
 * linked storages of the settlement hold ({@link #stock}) and taking from or returning to them ({@link #extract}, {@link #insert}). Server side only.
 *
 * <p>Planks of any wood count as one material when the config {@code planks_any_wood} is on (the block of the design is placed as written).
 */
public final class BuildMaterials {
    /** The key of the planks of any wood. */
    public static final String PLANKS_KEY = "#planks";

    private BuildMaterials() {}

    /**
     * One material: the key, the item (the sample item for planks of any wood), how many are needed. {@code anyPlanks} means any planks fit.
     */
    public record Req(String key, Item item, boolean anyPlanks, int count) {
        public boolean matches(ItemStack stack) {
            return !stack.isEmpty() && (anyPlanks ? stack.is(ItemTags.PLANKS) : stack.is(item));
        }

        public Predicate<ItemStack> matcher() {
            return this::matches;
        }

        /** The translation key of the name of the material. */
        public String label() {
            return anyPlanks ? "guistrings.settlement.need.material.planks" : item.getDescriptionId();
        }

        Req withCount(int newCount) {
            return new Req(key, item, anyPlanks, newCount);
        }
    }

    // ---------------------------------------------------------------- what a block costs

    /** The item that pays for a block, or {@link Items#AIR} when the block is free (it has no item). */
    public static Item itemFor(BlockState state) {
        Block block = state.getBlock();
        Item item = block.asItem();
        if (item != Items.AIR) {
            return item;
        }
        if (block == Blocks.FARMLAND) {
            return Items.DIRT;
        }
        if (block == Blocks.WHEAT) {
            return Items.WHEAT_SEEDS;
        }
        if (block == Blocks.CARROTS) {
            return Items.CARROT;
        }
        if (block == Blocks.POTATOES) {
            return Items.POTATO;
        }
        if (block == Blocks.BEETROOTS) {
            return Items.BEETROOT_SEEDS;
        }
        if (block == Blocks.WATER_CAULDRON || block == Blocks.LAVA_CAULDRON || block == Blocks.POWDER_SNOW_CAULDRON) {
            return Items.CAULDRON;
        }
        return Items.AIR;
    }

    /** How many items of {@link #itemFor} the block costs: 0 for the second half of a door, bed or tall plant, 2 for a double slab. */
    public static int countFor(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            return 0;
        }
        if (block instanceof DoublePlantBlock && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
            return 0;
        }
        if (block instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
            return 0;
        }
        if (block instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) {
            return 2;
        }
        if (state.hasProperty(BlockStateProperties.LAYERS)) {
            return state.getValue(BlockStateProperties.LAYERS);
        }
        if (state.hasProperty(BlockStateProperties.CANDLES)) {
            return state.getValue(BlockStateProperties.CANDLES);
        }
        return 1;
    }

    /** The material of a block (with the count of one block), or null when the block is free. */
    public static Req reqFor(BlockState state) {
        Item item = itemFor(state);
        int count = countFor(state);
        if (item == Items.AIR || count <= 0) {
            return null;
        }
        return makeReq(item, count);
    }

    private static Req makeReq(Item item, int count) {
        if (SettlementConfig.planksAnyWood && new ItemStack(item).is(ItemTags.PLANKS)) {
            return new Req(PLANKS_KEY, item, true, count);
        }
        return new Req(BuiltInRegistries.ITEM.getKey(item).toString(), item, false, count);
    }

    private static void add(Map<String, Req> into, Req req, int times) {
        into.merge(req.key(), req.withCount(req.count() * times), (a, b) -> a.withCount(a.count() + b.count()));
    }

    // ---------------------------------------------------------------- what a build needs

    /** The materials of a whole design (what a new build of it costs). */
    public static List<Req> designRequirements(BuildingDesign design) {
        Map<String, Req> map = new LinkedHashMap<>();
        for (Map.Entry<BlockState, Integer> entry : design.materialCounts().entrySet()) {
            Req req = reqFor(entry.getKey());
            if (req != null) {
                add(map, req, entry.getValue());
            }
        }
        return new ArrayList<>(map.values());
    }

    /**
     * The materials the steps that are not done yet still need. A demolition needs none. Earth fills cost dirt only with the config
     * {@code terrain_fill_costs_material}.
     */
    public static List<Req> remaining(BuildProject project, BuildingDesign design) {
        Map<String, Req> map = new LinkedHashMap<>();
        if (project.getKind() != BuildProject.Kind.BUILD) {
            return List.of();
        }
        for (BuildStep step : project.steps(design)) {
            if (!step.places() || project.isStepDone(step.index())) {
                continue;
            }
            if (step.kind() == BuildStep.Kind.FILL && !SettlementConfig.terrainFillCostsMaterial) {
                continue;
            }
            Req req = reqFor(step.state());
            if (req != null) {
                add(map, req, 1);
            }
        }
        return new ArrayList<>(map.values());
    }

    // ---------------------------------------------------------------- the storage

    /** A snapshot of the stacks in the loaded storages of a settlement (copies, so counting is cheap and safe). */
    public static List<ItemStack> stock(ServerLevel level, Settlement settlement) {
        List<ItemStack> stacks = new ArrayList<>();
        for (SettlementApi.StorageHandle storage : SettlementApi.allStorages(level, settlement)) {
            IItemHandler handler = storage.handler();
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    stacks.add(stack.copy());
                }
            }
        }
        return stacks;
    }

    public static int count(List<ItemStack> stock, Req req) {
        int total = 0;
        for (ItemStack stack : stock) {
            if (req.matches(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** The materials of the list that the stock does not cover, each with the missing count. */
    public static List<Req> missing(List<Req> needed, List<ItemStack> stock) {
        List<Req> result = new ArrayList<>();
        for (Req req : needed) {
            int have = count(stock, req);
            if (have < req.count()) {
                result.add(req.withCount(req.count() - have));
            }
        }
        return result;
    }

    /**
     * Takes {@code count} items that match from the storages (all or nothing).
     * @return true if they were taken
     */
    public static boolean extract(ServerLevel level, Settlement settlement, Req req, int count) {
        if (count <= 0) {
            return true;
        }
        List<SettlementApi.StorageHandle> storages = SettlementApi.allStorages(level, settlement);
        int available = 0;
        for (SettlementApi.StorageHandle storage : storages) {
            IItemHandler handler = storage.handler();
            for (int slot = 0; slot < handler.getSlots() && available < count; slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (req.matches(stack)) {
                    available += stack.getCount();
                }
            }
        }
        if (available < count) {
            return false;
        }
        int left = count;
        for (SettlementApi.StorageHandle storage : storages) {
            IItemHandler handler = storage.handler();
            for (int slot = 0; slot < handler.getSlots() && left > 0; slot++) {
                if (req.matches(handler.getStackInSlot(slot))) {
                    left -= handler.extractItem(slot, left, false).getCount();
                }
            }
            if (left <= 0) {
                break;
            }
        }
        return left <= 0;
    }

    /** Puts a stack into the storages of the settlement (the hall first). Returns what did not fit (empty if all went in). */
    public static ItemStack insert(ServerLevel level, Settlement settlement, ItemStack stack) {
        ItemStack rest = stack.copy();
        for (SettlementApi.StorageHandle storage : SettlementApi.allStorages(level, settlement)) {
            if (rest.isEmpty()) {
                break;
            }
            rest = ItemHandlerHelper.insertItem(storage.handler(), rest, false);
        }
        return rest;
    }
}
