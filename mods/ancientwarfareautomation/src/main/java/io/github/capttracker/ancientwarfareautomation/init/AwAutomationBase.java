/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/init/AWAutomationBlocks, AWAutomationItems and automation/AWAutomationTab,
 * and the registration done in automation/AncientWarfareAutomation (parsers, chunk loading callback, command).
 */
package io.github.capttracker.ancientwarfareautomation.init;

import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.ancientwarfare.api.AwCapabilities;
import io.github.capttracker.ancientwarfare.item.OwnedBlockItem;
import io.github.capttracker.ancientwarfare.registry.RegistryLoader;
import io.github.capttracker.ancientwarfare.upgrade.WorksiteUpgrade;
import io.github.capttracker.ancientwarfareautomation.AncientWarfareAutomation;
import io.github.capttracker.ancientwarfareautomation.block.worksite.AutoCraftingBlock;
import io.github.capttracker.ancientwarfareautomation.block.chunkloader.ChunkLoaderDeluxeBlock;
import io.github.capttracker.ancientwarfareautomation.block.chunkloader.ChunkLoaderSimpleBlock;
import io.github.capttracker.ancientwarfareautomation.block.warehouse.MailboxBlock;
import io.github.capttracker.ancientwarfareautomation.block.warehouse.WarehouseCraftingStationBlock;
import io.github.capttracker.ancientwarfareautomation.block.warehouse.WarehouseInterfaceBlock;
import io.github.capttracker.ancientwarfareautomation.block.warehouse.WarehouseStockLinkerBlock;
import io.github.capttracker.ancientwarfareautomation.block.warehouse.WarehouseStockViewerBlock;
import io.github.capttracker.ancientwarfareautomation.block.warehouse.WarehouseStorageBlock;
import io.github.capttracker.ancientwarfareautomation.block.worksite.WorksiteBlock;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseCraftingStationBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseInterfaceBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseStockLinkerBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseStockViewerBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseSetup;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseStorageBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.WorksiteEvents;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileConduitHeavy;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileConduitLight;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileConduitMedium;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileDistributorHeavy;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileDistributorLight;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileDistributorMedium;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileFlywheelControllerHeavy;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileFlywheelControllerLight;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileFlywheelControllerMedium;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileHandCrankedGenerator;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileStirlingGenerator;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileTorqueShaftHeavy;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileTorqueShaftLight;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileTorqueShaftMedium;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileWaterwheelGenerator;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileWindmillController;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TorqueCapabilities;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.multiblock.TileFlywheelStorage;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.multiblock.TileWindmillBlade;
import io.github.capttracker.ancientwarfareautomation.block.torque.BlockFlywheelController;
import io.github.capttracker.ancientwarfareautomation.block.torque.BlockFlywheelStorage;
import io.github.capttracker.ancientwarfareautomation.block.torque.BlockHandCrankedGenerator;
import io.github.capttracker.ancientwarfareautomation.block.torque.BlockStirlingGenerator;
import io.github.capttracker.ancientwarfareautomation.block.torque.BlockTorqueBase;
import io.github.capttracker.ancientwarfareautomation.block.torque.BlockTorqueDistributor;
import io.github.capttracker.ancientwarfareautomation.block.torque.BlockTorqueJunction;
import io.github.capttracker.ancientwarfareautomation.block.torque.BlockTorqueTransportShaft;
import io.github.capttracker.ancientwarfareautomation.block.torque.BlockWaterwheelGenerator;
import io.github.capttracker.ancientwarfareautomation.block.torque.BlockWindmillBlade;
import io.github.capttracker.ancientwarfareautomation.block.torque.BlockWindmillGenerator;
import io.github.capttracker.ancientwarfareautomation.block.torque.TorqueTier;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.AnimalFarmBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.AutoCraftingBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.ApiaryBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.DockBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.FishFarmBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.InnBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.MarketStallBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.StableBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.SmelterBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.InventoryWorksiteBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.QuarryBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.WorksiteBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.cropfarm.CropFarmBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.fruitfarm.FruitFarmBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.treefarm.TreeFarmBlockEntity;
import io.github.capttracker.ancientwarfareautomation.chunkloader.AWChunkLoader;
import io.github.capttracker.ancientwarfareautomation.chunkloader.ChunkLoaderDeluxeBlockEntity;
import io.github.capttracker.ancientwarfareautomation.chunkloader.ChunkLoaderSimpleBlockEntity;
import io.github.capttracker.ancientwarfareautomation.command.WarehouseCommand;
import io.github.capttracker.ancientwarfareautomation.gamedata.MailboxBlockEntity;
import io.github.capttracker.ancientwarfareautomation.gamedata.MailboxData;
import io.github.capttracker.ancientwarfareautomation.item.WarehouseStockLinkerBlockItem;
import io.github.capttracker.ancientwarfareautomation.item.WorksiteBlockItem;
import io.github.capttracker.ancientwarfareautomation.item.WorksiteUpgradeItem;
import io.github.capttracker.ancientwarfareautomation.registry.CropFarmRegistry;
import io.github.capttracker.ancientwarfareautomation.registry.FruitFarmRegistry;
import io.github.capttracker.ancientwarfareautomation.registry.TreeFarmRegistry;
import io.github.capttracker.lib.registry.ModRegistrar;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The master list of everything the Automation module registers: the 38 blocks, the 11 worksite upgrade items, the
 * block entity types, the {@code warehouse_pos} data component and the creative tab, plus the capability, chunk
 * ticket, registry parser, command and tick listeners that belong to them.
 *
 * <p><b>Flattening.</b> 1.12.2 registered 26 blocks, six of them with metadata variants. Every variant is its own
 * block now: the five tiered torque blocks (flywheel controller, flywheel storage, torque junction, torque shaft,
 * torque distributor) x {@code TorqueTier} = 15, the warehouse storage x {@link WarehouseSize} = 3, and the 20
 * single blocks = 38. The 11 metadata variants of {@code worksite_upgrade} are 11 items ({@link #UPGRADE_ITEMS}).
 * Registry names: the 1.12.2 name for single blocks, {@code <name>_<tier>} / {@code <name>_<size>} for the others,
 * {@code worksite_upgrade_<variant>} for the upgrades (variant = the 1.12.2 model variant, see
 * {@link #upgradeItemName}). The creative tab lists them in the order of the original
 * {@code AWAutomationTab} (worksites, warehouse, mailbox, torque, flywheel storage, windmill blade, chunk loaders,
 * then the upgrades).
 *
 * <p><b>Block entity types.</b> 34 types instead of the 36 tile ids of 1.12.2: the tiered torque tiles keep one type
 * per tier (the torque slice has three tile classes per tile type) except the flywheel storage, whose three blocks share
 * one type; the three warehouse storage sizes share one type, a block entity reads its size from its block. The type ids
 * are the registry name of the block, no longer {@code *_tile}.
 *
 * <p><b>Classes of the other Automation slices.</b> The torque classes exist ({@code block.torque.Block*},
 * {@code block.entity.torque.Tile*}, taken as they are written). The warehouse and concrete worksite classes were not
 * written yet when this list was made; the names below are the contract for them (a block entity class has a public
 * constructor {@code (BlockPos, BlockState)} that passes its type from this class, like core's
 * {@code EngineeringStationBlockEntity}; a block class has a public constructor taking the
 * {@code BlockBehaviour.Properties} first). If a slice uses other names, only the references in this file change.
 * <pre>
 * block (io.github.capttracker.ancientwarfareautomation.block):
 *   WorksiteBlock(Properties, Supplier&lt;? extends BlockEntityType&lt;?&gt;&gt;)  quarry, tree/crop/fruit/animal/fish farm, warehouse control
 *   AutoCraftingBlock(Properties)
 *   WarehouseStorageBlock(Properties, WarehouseSize), WarehouseInterfaceBlock, WarehouseCraftingStationBlock,
 *   WarehouseStockViewerBlock, WarehouseStockLinkerBlock, MailboxBlock, ChunkLoaderSimpleBlock, ChunkLoaderDeluxeBlock
 *       (all (Properties))
 * block.entity.worksite:    QuarryBlockEntity, AnimalFarmBlockEntity, FishFarmBlockEntity, AutoCraftingBlockEntity;
 *   .cropfarm.CropFarmBlockEntity, .fruitfarm.FruitFarmBlockEntity, .treefarm.TreeFarmBlockEntity
 *   (all extend InventoryWorksiteBlockEntity or FarmWorksiteBlockEntity, except AutoCraftingBlockEntity: WorksiteBlockEntity)
 * block.entity.warehouse:   WarehouseBlockEntity (the control block, extends BoundedWorksiteBlockEntity),
 *   WarehouseStorageBlockEntity, WarehouseInterfaceBlockEntity, WarehouseCraftingStationBlockEntity,
 *   WarehouseStockViewerBlockEntity, WarehouseStockLinkerBlockEntity
 * </pre>
 * Written here (this slice): {@code ChunkLoaderSimpleBlockEntity}/{@code ChunkLoaderDeluxeBlockEntity} (package
 * {@code chunkloader}) and {@code MailboxBlockEntity} (package {@code gamedata}), the base classes in
 * {@code block.entity.worksite} and the item classes.
 *
 * <p><b>Wiring</b> (mod constructor, before {@code REG.register(modBus)}): {@code AwAutomationBase.load();
 * AwAutomationBase.registerEvents(modBus); AWAutomationStatics.register(modBus, container);}.
 *
 * <p>Not ported from the module entry: the RedstoneFlux proxy (the worksite and torque block entities expose
 * {@code Capabilities.EnergyStorage.BLOCK}, see {@code TorqueEnergyStorage}), the AgriCraft compat, BuildCraft
 * (the 1.12.2 dependency string was the only trace), the data fixer walker, the 21 {@code registerContainer} calls
 * (menu types belong to the GUI slice), the proxy classes and the config changed handler.
 *
 * <p>Block properties: wooden blocks are {@code wood()} (axe), stone ones {@code rock()} (pickaxe, tool required, see
 * {@code data/minecraft/tags/block/mineable}); the torque blocks use their own classes' default properties.
 */
public final class AwAutomationBase {
    private AwAutomationBase() {}

    private static final ModRegistrar REG = AncientWarfareAutomation.REG;

    // ---- block properties ----

    /** 1.12.2 {@code Material.WOOD}, hardness 2. */
    private static BlockBehaviour.Properties wood() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2f).sound(SoundType.WOOD).ignitedByLava();
    }

    /** 1.12.2 {@code Material.ROCK}, hardness 2; needs a tool to drop anything, like rock did. */
    private static BlockBehaviour.Properties rock() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2f).sound(SoundType.STONE).requiresCorrectToolForDrops();
    }

    // ---- registration helpers ----

    /** A block with its own item class. */
    private static <B extends Block> DeferredBlock<B> block(String name, Function<BlockBehaviour.Properties, B> factory,
                                                            BlockBehaviour.Properties props,
                                                            BiFunction<? super B, Item.Properties, BlockItem> itemFactory) {
        DeferredBlock<B> block = REG.blockWithoutItem(name, factory, props);
        REG.item(name, itemProps -> itemFactory.apply(block.get(), itemProps));
        return block;
    }

    private static DeferredBlock<WorksiteBlock> worksite(String name, Supplier<? extends BlockEntityType<?>> blockEntityType) {
        return block(name, props -> new WorksiteBlock(props, blockEntityType), wood(), WorksiteBlockItem::new);
    }

    @SuppressWarnings("unchecked")
    private static Supplier<? extends Block>[] suppliers(Collection<? extends Supplier<? extends Block>> blocks) {
        return blocks.toArray(new Supplier[0]);
    }

    // ---- blocks: worksites, warehouse, mailbox (creative tab order of the original) ----

    public static final DeferredBlock<WorksiteBlock> TREE_FARM = worksite("tree_farm", () -> AwAutomationBase.TREE_FARM_BE.get());
    public static final DeferredBlock<WorksiteBlock> CROP_FARM = worksite("crop_farm", () -> AwAutomationBase.CROP_FARM_BE.get());
    public static final DeferredBlock<WorksiteBlock> FRUIT_FARM = worksite("fruit_farm", () -> AwAutomationBase.FRUIT_FARM_BE.get());
    public static final DeferredBlock<WorksiteBlock> ANIMAL_FARM = worksite("animal_farm", () -> AwAutomationBase.ANIMAL_FARM_BE.get());
    public static final DeferredBlock<WorksiteBlock> FISH_FARM = worksite("fish_farm", () -> AwAutomationBase.FISH_FARM_BE.get());
    /** Rework phase 4: the fishing dock, a worksite that owns a fishing boat. */
    public static final DeferredBlock<WorksiteBlock> DOCK = worksite("dock", () -> AwAutomationBase.DOCK_BE.get());
    public static final DeferredBlock<WorksiteBlock> APIARY = worksite("apiary", () -> AwAutomationBase.APIARY_BE.get());
    public static final DeferredBlock<WorksiteBlock> SMELTER = worksite("smelter", () -> AwAutomationBase.SMELTER_BE.get());
    /** Rework phase 5: the market stall, staffed by a trader NPC (no work area, so its item is a plain owned block item). */
    public static final DeferredBlock<WorksiteBlock> MARKET_STALL = block("market_stall",
            props -> new WorksiteBlock(props, () -> AwAutomationBase.MARKET_STALL_BE.get()), wood(), OwnedBlockItem::new);
    /** Rework phase 5: the inn, staffed by an innkeeper NPC (no work area, plain owned block item). */
    public static final DeferredBlock<WorksiteBlock> INN = block("inn",
            props -> new WorksiteBlock(props, () -> AwAutomationBase.INN_BE.get()), wood(), OwnedBlockItem::new);
    /** Rework phase 5: the stable, staffed by a stable hand NPC (no work area, plain owned block item). */
    public static final DeferredBlock<WorksiteBlock> STABLE = block("stable",
            props -> new WorksiteBlock(props, () -> AwAutomationBase.STABLE_BE.get()), wood(), OwnedBlockItem::new);
    public static final DeferredBlock<WorksiteBlock> QUARRY = worksite("quarry", () -> AwAutomationBase.QUARRY_BE.get());

    /** Not a full cube (1.12.2: not opaque). */
    public static final DeferredBlock<AutoCraftingBlock> AUTO_CRAFTING =
            block("auto_crafting", AutoCraftingBlock::new, wood().noOcclusion(), OwnedBlockItem::new);

    /** Uses the worksite block class as in 1.12.2 (a controller has work bounds, upgrades and an owner). */
    public static final DeferredBlock<WorksiteBlock> WAREHOUSE_CONTROL =
            worksite("warehouse_control", () -> AwAutomationBase.WAREHOUSE_CONTROL_BE.get());

    public static final DeferredBlock<WarehouseInterfaceBlock> WAREHOUSE_INTERFACE =
            REG.block("warehouse_interface", WarehouseInterfaceBlock::new, rock());
    public static final DeferredBlock<WarehouseCraftingStationBlock> WAREHOUSE_CRAFTING =
            REG.block("warehouse_crafting", WarehouseCraftingStationBlock::new, rock());

    /** The three sizes of the warehouse storage block (metadata 0-2 of {@code warehouse_storage} in 1.12.2). */
    public static final Map<WarehouseSize, DeferredBlock<WarehouseStorageBlock>> WAREHOUSE_STORAGES = new EnumMap<>(WarehouseSize.class);

    static {
        for (WarehouseSize size : WarehouseSize.values()) {
            WAREHOUSE_STORAGES.put(size, REG.block("warehouse_storage_" + size.getSerializedName(),
                    props -> new WarehouseStorageBlock(props, size), wood()));
        }
    }

    public static final DeferredBlock<WarehouseStockViewerBlock> WAREHOUSE_STOCK_VIEWER =
            block("warehouse_stock_viewer", WarehouseStockViewerBlock::new, rock().noOcclusion(), OwnedBlockItem::new);
    public static final DeferredBlock<WarehouseStockLinkerBlock> WAREHOUSE_STOCK_LINKER =
            block("warehouse_stock_linker", WarehouseStockLinkerBlock::new, rock().noOcclusion(), WarehouseStockLinkerBlockItem::new);
    public static final DeferredBlock<MailboxBlock> MAILBOX =
            block("mailbox", MailboxBlock::new, rock(), OwnedBlockItem::new);

    // ---- blocks: torque (classes of the automation-torque slice, package block.torque) ----
    // Registered in the creative tab order of the original: flywheel controllers, junctions, shafts, distributors,
    // the generators, then the flywheel storage and the windmill blade. The blocks take a supplier of their block
    // entity type (see IBlockEntityTypeProvider); inside the lambdas the fields are named with the class, which avoids
    // javac's illegal forward reference error. Items are plain block items (the blocks set owner and facing themselves).

    public static final DeferredBlock<BlockFlywheelController> FLYWHEEL_CONTROLLER_LIGHT = REG.block("flywheel_controller_light",
            p -> new BlockFlywheelController(p, TorqueTier.LIGHT, () -> AwAutomationBase.FLYWHEEL_CONTROLLER_LIGHT_BE.get()), BlockTorqueBase.defaultProperties());
    public static final DeferredBlock<BlockFlywheelController> FLYWHEEL_CONTROLLER_MEDIUM = REG.block("flywheel_controller_medium",
            p -> new BlockFlywheelController(p, TorqueTier.MEDIUM, () -> AwAutomationBase.FLYWHEEL_CONTROLLER_MEDIUM_BE.get()), BlockTorqueBase.defaultProperties());
    public static final DeferredBlock<BlockFlywheelController> FLYWHEEL_CONTROLLER_HEAVY = REG.block("flywheel_controller_heavy",
            p -> new BlockFlywheelController(p, TorqueTier.HEAVY, () -> AwAutomationBase.FLYWHEEL_CONTROLLER_HEAVY_BE.get()), BlockTorqueBase.defaultProperties());

    public static final DeferredBlock<BlockTorqueJunction> TORQUE_JUNCTION_LIGHT = REG.block("torque_junction_light",
            p -> new BlockTorqueJunction(p, TorqueTier.LIGHT, () -> AwAutomationBase.TORQUE_JUNCTION_LIGHT_BE.get()), BlockTorqueBase.defaultProperties());
    public static final DeferredBlock<BlockTorqueJunction> TORQUE_JUNCTION_MEDIUM = REG.block("torque_junction_medium",
            p -> new BlockTorqueJunction(p, TorqueTier.MEDIUM, () -> AwAutomationBase.TORQUE_JUNCTION_MEDIUM_BE.get()), BlockTorqueBase.defaultProperties());
    public static final DeferredBlock<BlockTorqueJunction> TORQUE_JUNCTION_HEAVY = REG.block("torque_junction_heavy",
            p -> new BlockTorqueJunction(p, TorqueTier.HEAVY, () -> AwAutomationBase.TORQUE_JUNCTION_HEAVY_BE.get()), BlockTorqueBase.defaultProperties());

    public static final DeferredBlock<BlockTorqueTransportShaft> TORQUE_SHAFT_LIGHT = REG.block("torque_shaft_light",
            p -> new BlockTorqueTransportShaft(p, TorqueTier.LIGHT, () -> AwAutomationBase.TORQUE_SHAFT_LIGHT_BE.get()), BlockTorqueBase.defaultProperties());
    public static final DeferredBlock<BlockTorqueTransportShaft> TORQUE_SHAFT_MEDIUM = REG.block("torque_shaft_medium",
            p -> new BlockTorqueTransportShaft(p, TorqueTier.MEDIUM, () -> AwAutomationBase.TORQUE_SHAFT_MEDIUM_BE.get()), BlockTorqueBase.defaultProperties());
    public static final DeferredBlock<BlockTorqueTransportShaft> TORQUE_SHAFT_HEAVY = REG.block("torque_shaft_heavy",
            p -> new BlockTorqueTransportShaft(p, TorqueTier.HEAVY, () -> AwAutomationBase.TORQUE_SHAFT_HEAVY_BE.get()), BlockTorqueBase.defaultProperties());

    public static final DeferredBlock<BlockTorqueDistributor> TORQUE_DISTRIBUTOR_LIGHT = REG.block("torque_distributor_light",
            p -> new BlockTorqueDistributor(p, TorqueTier.LIGHT, () -> AwAutomationBase.TORQUE_DISTRIBUTOR_LIGHT_BE.get()), BlockTorqueBase.defaultProperties());
    public static final DeferredBlock<BlockTorqueDistributor> TORQUE_DISTRIBUTOR_MEDIUM = REG.block("torque_distributor_medium",
            p -> new BlockTorqueDistributor(p, TorqueTier.MEDIUM, () -> AwAutomationBase.TORQUE_DISTRIBUTOR_MEDIUM_BE.get()), BlockTorqueBase.defaultProperties());
    public static final DeferredBlock<BlockTorqueDistributor> TORQUE_DISTRIBUTOR_HEAVY = REG.block("torque_distributor_heavy",
            p -> new BlockTorqueDistributor(p, TorqueTier.HEAVY, () -> AwAutomationBase.TORQUE_DISTRIBUTOR_HEAVY_BE.get()), BlockTorqueBase.defaultProperties());

    public static final DeferredBlock<BlockStirlingGenerator> STIRLING_GENERATOR = REG.block("stirling_generator",
            p -> new BlockStirlingGenerator(p, () -> AwAutomationBase.STIRLING_GENERATOR_BE.get()), BlockTorqueBase.defaultProperties());
    public static final DeferredBlock<BlockWaterwheelGenerator> WATERWHEEL_GENERATOR = REG.block("waterwheel_generator",
            p -> new BlockWaterwheelGenerator(p, () -> AwAutomationBase.WATERWHEEL_GENERATOR_BE.get()), BlockTorqueBase.defaultProperties());
    public static final DeferredBlock<BlockHandCrankedGenerator> HAND_CRANKED_GENERATOR = REG.block("hand_cranked_generator",
            p -> new BlockHandCrankedGenerator(p, () -> AwAutomationBase.HAND_CRANKED_GENERATOR_BE.get()), BlockTorqueBase.defaultProperties());
    public static final DeferredBlock<BlockWindmillGenerator> WINDMILL_GENERATOR = REG.block("windmill_generator",
            p -> new BlockWindmillGenerator(p, () -> AwAutomationBase.WINDMILL_GENERATOR_BE.get()), BlockTorqueBase.defaultProperties());

    /** Light, medium and heavy flywheel storage (part of the flywheel multiblock); one block entity type for the three. */
    public static final DeferredBlock<BlockFlywheelStorage> FLYWHEEL_STORAGE_LIGHT = REG.block("flywheel_storage_light",
            p -> new BlockFlywheelStorage(p, TorqueTier.LIGHT, () -> AwAutomationBase.FLYWHEEL_STORAGE_BE.get()), BlockFlywheelStorage.defaultProperties());
    public static final DeferredBlock<BlockFlywheelStorage> FLYWHEEL_STORAGE_MEDIUM = REG.block("flywheel_storage_medium",
            p -> new BlockFlywheelStorage(p, TorqueTier.MEDIUM, () -> AwAutomationBase.FLYWHEEL_STORAGE_BE.get()), BlockFlywheelStorage.defaultProperties());
    public static final DeferredBlock<BlockFlywheelStorage> FLYWHEEL_STORAGE_HEAVY = REG.block("flywheel_storage_heavy",
            p -> new BlockFlywheelStorage(p, TorqueTier.HEAVY, () -> AwAutomationBase.FLYWHEEL_STORAGE_BE.get()), BlockFlywheelStorage.defaultProperties());

    public static final DeferredBlock<BlockWindmillBlade> WINDMILL_BLADE = REG.block("windmill_blade",
            p -> new BlockWindmillBlade(p, () -> AwAutomationBase.WINDMILL_BLADE_BE.get()), BlockWindmillBlade.defaultProperties());

    // ---- blocks: chunk loaders ----

    public static final DeferredBlock<ChunkLoaderSimpleBlock> CHUNK_LOADER_SIMPLE =
            REG.block("chunk_loader_simple", ChunkLoaderSimpleBlock::new, rock());
    public static final DeferredBlock<ChunkLoaderDeluxeBlock> CHUNK_LOADER_DELUXE =
            REG.block("chunk_loader_deluxe", ChunkLoaderDeluxeBlock::new, rock());

    // ---- items ----

    /**
     * The 11 worksite upgrades, one item each (1.12.2: one {@code worksite_upgrade} item with the damage value
     * {@code upgrade.ordinal()}). Registered after the blocks, so they come last in the creative tab.
     */
    public static final Map<WorksiteUpgrade, DeferredItem<WorksiteUpgradeItem>> UPGRADE_ITEMS = new EnumMap<>(WorksiteUpgrade.class);

    static {
        for (WorksiteUpgrade upgrade : WorksiteUpgrade.values()) {
            UPGRADE_ITEMS.put(upgrade, REG.item(upgradeItemName(upgrade), props -> new WorksiteUpgradeItem(props, upgrade)));
        }
    }

    /**
     * The registry name of the item of an upgrade: {@code worksite_upgrade_} plus the 1.12.2 model variant name
     * ({@code bounds_medium}, {@code bounds_large}, {@code quarry_medium}, {@code quarry_large},
     * {@code enchanted_tools_1}, {@code enchanted_tools_2}, {@code quality_tools_1}, {@code quality_tools_2},
     * {@code quality_tools_3}, {@code chunkloader_basic}, {@code chunkloader_quarry}).
     */
    public static String upgradeItemName(WorksiteUpgrade upgrade) {
        return "worksite_upgrade_" + switch (upgrade) {
            case SIZE_MEDIUM -> "bounds_medium";
            case SIZE_LARGE -> "bounds_large";
            case QUARRY_MEDIUM -> "quarry_medium";
            case QUARRY_LARGE -> "quarry_large";
            case ENCHANTED_TOOLS_1 -> "enchanted_tools_1";
            case ENCHANTED_TOOLS_2 -> "enchanted_tools_2";
            case TOOL_QUALITY_1 -> "quality_tools_1";
            case TOOL_QUALITY_2 -> "quality_tools_2";
            case TOOL_QUALITY_3 -> "quality_tools_3";
            case BASIC_CHUNK_LOADER -> "chunkloader_basic";
            case QUARRY_CHUNK_LOADER -> "chunkloader_quarry";
        };
    }

    /** The position of a warehouse control block, stored in a warehouse stock linker stack (1.12.2: item NBT {@code warehousePosTag}). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> WAREHOUSE_POS =
            REG.dataComponent("warehouse_pos", BlockPos.CODEC, BlockPos.STREAM_CODEC);

    // ---- block entity types ----

    // worksites (1.12.2 ids: quarry_tile, forestry_tile, crop_farm_tile, fruit_farm_tile, animal_farm_tile, fish_farm_tile, auto_crafting_tile)
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<QuarryBlockEntity>> QUARRY_BE =
            REG.blockEntity("quarry", QuarryBlockEntity::new, QUARRY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TreeFarmBlockEntity>> TREE_FARM_BE =
            REG.blockEntity("tree_farm", TreeFarmBlockEntity::new, TREE_FARM);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CropFarmBlockEntity>> CROP_FARM_BE =
            REG.blockEntity("crop_farm", CropFarmBlockEntity::new, CROP_FARM);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FruitFarmBlockEntity>> FRUIT_FARM_BE =
            REG.blockEntity("fruit_farm", FruitFarmBlockEntity::new, FRUIT_FARM);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AnimalFarmBlockEntity>> ANIMAL_FARM_BE =
            REG.blockEntity("animal_farm", AnimalFarmBlockEntity::new, ANIMAL_FARM);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FishFarmBlockEntity>> FISH_FARM_BE =
            REG.blockEntity("fish_farm", FishFarmBlockEntity::new, FISH_FARM);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DockBlockEntity>> DOCK_BE =
            REG.blockEntity("dock", DockBlockEntity::new, DOCK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ApiaryBlockEntity>> APIARY_BE =
            REG.blockEntity("apiary", ApiaryBlockEntity::new, APIARY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmelterBlockEntity>> SMELTER_BE =
            REG.blockEntity("smelter", SmelterBlockEntity::new, SMELTER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MarketStallBlockEntity>> MARKET_STALL_BE =
            REG.blockEntity("market_stall", MarketStallBlockEntity::new, MARKET_STALL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InnBlockEntity>> INN_BE =
            REG.blockEntity("inn", InnBlockEntity::new, INN);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StableBlockEntity>> STABLE_BE =
            REG.blockEntity("stable", StableBlockEntity::new, STABLE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutoCraftingBlockEntity>> AUTO_CRAFTING_BE =
            REG.blockEntity("auto_crafting", AutoCraftingBlockEntity::new, AUTO_CRAFTING);

    // mailbox and chunk loaders (this slice)
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MailboxBlockEntity>> MAILBOX_BE =
            REG.blockEntity("mailbox", MailboxBlockEntity::new, MAILBOX);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChunkLoaderSimpleBlockEntity>> CHUNK_LOADER_SIMPLE_BE =
            REG.blockEntity("chunk_loader_simple", ChunkLoaderSimpleBlockEntity::new, CHUNK_LOADER_SIMPLE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChunkLoaderDeluxeBlockEntity>> CHUNK_LOADER_DELUXE_BE =
            REG.blockEntity("chunk_loader_deluxe", ChunkLoaderDeluxeBlockEntity::new, CHUNK_LOADER_DELUXE);

    // warehouse (1.12.2 ids: warehouse_control_tile, warehouse_storage_{small,medium,large}_tile, ...)
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WarehouseBlockEntity>> WAREHOUSE_CONTROL_BE =
            REG.blockEntity("warehouse_control", WarehouseBlockEntity::new, WAREHOUSE_CONTROL);
    /** One type for the three sizes; the block entity reads its size from its block. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WarehouseStorageBlockEntity>> WAREHOUSE_STORAGE_BE =
            REG.blockEntity("warehouse_storage", WarehouseStorageBlockEntity::new, suppliers(WAREHOUSE_STORAGES.values()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WarehouseInterfaceBlockEntity>> WAREHOUSE_INTERFACE_BE =
            REG.blockEntity("warehouse_interface", WarehouseInterfaceBlockEntity::new, WAREHOUSE_INTERFACE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WarehouseCraftingStationBlockEntity>> WAREHOUSE_CRAFTING_BE =
            REG.blockEntity("warehouse_crafting", WarehouseCraftingStationBlockEntity::new, WAREHOUSE_CRAFTING);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WarehouseStockViewerBlockEntity>> WAREHOUSE_STOCK_VIEWER_BE =
            REG.blockEntity("warehouse_stock_viewer", WarehouseStockViewerBlockEntity::new, WAREHOUSE_STOCK_VIEWER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WarehouseStockLinkerBlockEntity>> WAREHOUSE_STOCK_LINKER_BE =
            REG.blockEntity("warehouse_stock_linker", WarehouseStockLinkerBlockEntity::new, WAREHOUSE_STOCK_LINKER);

    // torque (1.12.2 ids: flywheel_{light,medium,large}_tile, torque_junction_{...}_tile, torque_shaft_{...}_tile, ...).
    // One type per tier for the tiered tiles (the torque slice keeps three tile classes per tile type), one shared
    // type for the three flywheel storage blocks.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileFlywheelControllerLight>> FLYWHEEL_CONTROLLER_LIGHT_BE =
            REG.blockEntity("flywheel_controller_light", TileFlywheelControllerLight::new, FLYWHEEL_CONTROLLER_LIGHT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileFlywheelControllerMedium>> FLYWHEEL_CONTROLLER_MEDIUM_BE =
            REG.blockEntity("flywheel_controller_medium", TileFlywheelControllerMedium::new, FLYWHEEL_CONTROLLER_MEDIUM);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileFlywheelControllerHeavy>> FLYWHEEL_CONTROLLER_HEAVY_BE =
            REG.blockEntity("flywheel_controller_heavy", TileFlywheelControllerHeavy::new, FLYWHEEL_CONTROLLER_HEAVY);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileConduitLight>> TORQUE_JUNCTION_LIGHT_BE =
            REG.blockEntity("torque_junction_light", TileConduitLight::new, TORQUE_JUNCTION_LIGHT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileConduitMedium>> TORQUE_JUNCTION_MEDIUM_BE =
            REG.blockEntity("torque_junction_medium", TileConduitMedium::new, TORQUE_JUNCTION_MEDIUM);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileConduitHeavy>> TORQUE_JUNCTION_HEAVY_BE =
            REG.blockEntity("torque_junction_heavy", TileConduitHeavy::new, TORQUE_JUNCTION_HEAVY);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileTorqueShaftLight>> TORQUE_SHAFT_LIGHT_BE =
            REG.blockEntity("torque_shaft_light", TileTorqueShaftLight::new, TORQUE_SHAFT_LIGHT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileTorqueShaftMedium>> TORQUE_SHAFT_MEDIUM_BE =
            REG.blockEntity("torque_shaft_medium", TileTorqueShaftMedium::new, TORQUE_SHAFT_MEDIUM);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileTorqueShaftHeavy>> TORQUE_SHAFT_HEAVY_BE =
            REG.blockEntity("torque_shaft_heavy", TileTorqueShaftHeavy::new, TORQUE_SHAFT_HEAVY);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileDistributorLight>> TORQUE_DISTRIBUTOR_LIGHT_BE =
            REG.blockEntity("torque_distributor_light", TileDistributorLight::new, TORQUE_DISTRIBUTOR_LIGHT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileDistributorMedium>> TORQUE_DISTRIBUTOR_MEDIUM_BE =
            REG.blockEntity("torque_distributor_medium", TileDistributorMedium::new, TORQUE_DISTRIBUTOR_MEDIUM);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileDistributorHeavy>> TORQUE_DISTRIBUTOR_HEAVY_BE =
            REG.blockEntity("torque_distributor_heavy", TileDistributorHeavy::new, TORQUE_DISTRIBUTOR_HEAVY);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileFlywheelStorage>> FLYWHEEL_STORAGE_BE =
            REG.blockEntity("flywheel_storage", TileFlywheelStorage::new, FLYWHEEL_STORAGE_LIGHT, FLYWHEEL_STORAGE_MEDIUM, FLYWHEEL_STORAGE_HEAVY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileStirlingGenerator>> STIRLING_GENERATOR_BE =
            REG.blockEntity("stirling_generator", TileStirlingGenerator::new, STIRLING_GENERATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileWaterwheelGenerator>> WATERWHEEL_GENERATOR_BE =
            REG.blockEntity("waterwheel_generator", TileWaterwheelGenerator::new, WATERWHEEL_GENERATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileHandCrankedGenerator>> HAND_CRANKED_GENERATOR_BE =
            REG.blockEntity("hand_cranked_generator", TileHandCrankedGenerator::new, HAND_CRANKED_GENERATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileWindmillBlade>> WINDMILL_BLADE_BE =
            REG.blockEntity("windmill_blade", TileWindmillBlade::new, WINDMILL_BLADE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileWindmillController>> WINDMILL_GENERATOR_BE =
            REG.blockEntity("windmill_generator", TileWindmillController::new, WINDMILL_GENERATOR);

    // ---- creative tab (1.12.2: AWAutomationTab, icon: the iron hammer) ----

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            REG.creativeTab(() -> new ItemStack(AncientWarfareCore.HAMMERS.get("iron_hammer").get()));

    // ---- wiring ----

    /** Forces this class (and so the registrations above) to initialise. Call from the mod constructor. */
    public static void load() {}

    /** Registers the mod-bus and game-bus listeners and the registry data parsers. Call from the mod constructor. */
    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(AwAutomationBase::registerCapabilities);
        modBus.addListener(AWChunkLoader::register);
        modBus.addListener(AwAutomationBase::onCommonSetup);
        WarehouseSetup.registerEvents(modBus);
        WorksiteEvents.registerEvents(modBus);

        // 1.12.2 preInit: the seven registry parsers (the AgriCraft compat is not ported)
        RegistryLoader.registerParser(new CropFarmRegistry.TillableParser());
        RegistryLoader.registerParser(new CropFarmRegistry.CropParser());
        RegistryLoader.registerParser(new CropFarmRegistry.SoilParser());
        RegistryLoader.registerParser(new FruitFarmRegistry.FruitParser());
        RegistryLoader.registerParser(new TreeFarmRegistry.PlantableParser());
        RegistryLoader.registerParser(new TreeFarmRegistry.SoilParser());
        RegistryLoader.registerParser(new TreeFarmRegistry.TreeScannerParser());

        NeoForge.EVENT_BUS.addListener(MailboxData::onServerTick);
        NeoForge.EVENT_BUS.addListener(AwAutomationBase::onRegisterCommands);
    }

    /**
     * What the base classes of this slice expose: Forge Energy and torque on every worksite (the warehouse control
     * block is one too), the side mapped item handler on the worksites with an inventory, and the mailbox's item
     * handler. The block entities of the other slices register their own capabilities (the auto crafting table's
     * handler, the warehouse blocks); the torque tiles are registered here through the torque slice's
     * {@code TorqueCapabilities} helper.
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        registerWorksite(event, QUARRY_BE);
        registerWorksite(event, TREE_FARM_BE);
        registerWorksite(event, CROP_FARM_BE);
        registerWorksite(event, FRUIT_FARM_BE);
        registerWorksite(event, ANIMAL_FARM_BE);
        registerWorksite(event, FISH_FARM_BE);
        registerWorksite(event, DOCK_BE);
        registerWorksite(event, APIARY_BE);
        registerWorksite(event, SMELTER_BE);
        registerWorksite(event, MARKET_STALL_BE);
        registerWorksite(event, INN_BE);
        registerWorksite(event, STABLE_BE);
        registerWorksite(event, AUTO_CRAFTING_BE);
        registerWorksite(event, WAREHOUSE_CONTROL_BE);

        registerInventoryWorksite(event, QUARRY_BE);
        registerInventoryWorksite(event, TREE_FARM_BE);
        registerInventoryWorksite(event, CROP_FARM_BE);
        registerInventoryWorksite(event, FRUIT_FARM_BE);
        registerInventoryWorksite(event, ANIMAL_FARM_BE);
        registerInventoryWorksite(event, FISH_FARM_BE);
        registerInventoryWorksite(event, DOCK_BE);
        registerInventoryWorksite(event, APIARY_BE);
        registerInventoryWorksite(event, SMELTER_BE);
        registerInventoryWorksite(event, MARKET_STALL_BE);
        registerInventoryWorksite(event, INN_BE);
        registerInventoryWorksite(event, STABLE_BE);

        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, MAILBOX_BE.get(), (mailbox, side) -> mailbox.getItemHandler(side));

        // torque tiles (automation-torque slice): torque and energy on shafts, conduits, distributors, flywheel controllers and the generators
        TorqueCapabilities.register(event, FLYWHEEL_CONTROLLER_LIGHT_BE.get());
        TorqueCapabilities.register(event, FLYWHEEL_CONTROLLER_MEDIUM_BE.get());
        TorqueCapabilities.register(event, FLYWHEEL_CONTROLLER_HEAVY_BE.get());
        TorqueCapabilities.register(event, TORQUE_JUNCTION_LIGHT_BE.get());
        TorqueCapabilities.register(event, TORQUE_JUNCTION_MEDIUM_BE.get());
        TorqueCapabilities.register(event, TORQUE_JUNCTION_HEAVY_BE.get());
        TorqueCapabilities.register(event, TORQUE_SHAFT_LIGHT_BE.get());
        TorqueCapabilities.register(event, TORQUE_SHAFT_MEDIUM_BE.get());
        TorqueCapabilities.register(event, TORQUE_SHAFT_HEAVY_BE.get());
        TorqueCapabilities.register(event, TORQUE_DISTRIBUTOR_LIGHT_BE.get());
        TorqueCapabilities.register(event, TORQUE_DISTRIBUTOR_MEDIUM_BE.get());
        TorqueCapabilities.register(event, TORQUE_DISTRIBUTOR_HEAVY_BE.get());
        TorqueCapabilities.register(event, STIRLING_GENERATOR_BE.get());
        TorqueCapabilities.register(event, WATERWHEEL_GENERATOR_BE.get());
        TorqueCapabilities.register(event, HAND_CRANKED_GENERATOR_BE.get());
        TorqueCapabilities.register(event, WINDMILL_GENERATOR_BE.get());
        TorqueCapabilities.registerStirling(event, STIRLING_GENERATOR_BE.get());
    }

    private static <T extends WorksiteBlockEntity> void registerWorksite(RegisterCapabilitiesEvent event,
                                                                         DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type) {
        event.registerBlockEntity(AwCapabilities.TORQUE, type.get(), (worksite, side) -> worksite);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type.get(), (worksite, side) -> worksite.getEnergyStorage(side));
    }

    private static <T extends InventoryWorksiteBlockEntity> void registerInventoryWorksite(RegisterCapabilitiesEvent event,
                                                                                          DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> type) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type.get(), (worksite, side) -> worksite.getItemHandler(side));
    }

    /** The worksite blocks burn like the original ones did: fire spread 5, flammability 20 (the values of wooden planks). */
    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            FireBlock fire = (FireBlock) Blocks.FIRE;
            List<Supplier<? extends Block>> worksites = List.of(TREE_FARM, CROP_FARM, FRUIT_FARM, ANIMAL_FARM, FISH_FARM, DOCK, APIARY, SMELTER, MARKET_STALL, INN, STABLE, QUARRY,
                    AUTO_CRAFTING, WAREHOUSE_CONTROL);
            for (Supplier<? extends Block> block : worksites) {
                fire.setFlammable(block.get(), 5, 20);
            }
        });
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        new WarehouseCommand().register(event.getDispatcher());
    }
}
