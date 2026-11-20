/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the container registration of automation/AncientWarfareAutomation (registerContainer)
 * and the GUI ids of core/network/NetworkHandler.
 */
package io.github.capttracker.ancientwarfareautomation.init;

import io.github.capttracker.ancientwarfare.api.IBoundedSite;
import io.github.capttracker.ancientwarfareautomation.AncientWarfareAutomation;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileStirlingGenerator;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseMenus;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.AnimalFarmBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.AutoCraftingBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.ApiaryBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.DockBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.FishFarmBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.MarketStallBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.SmelterBlockEntity;
import io.github.capttracker.ancientwarfareautomation.menu.ApiaryMenu;
import io.github.capttracker.ancientwarfareautomation.menu.SmelterMenu;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.InventoryWorksiteBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.QuarryBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.WorksiteMenus;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.cropfarm.CropFarmBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.fruitfarm.FruitFarmBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.treefarm.TreeFarmBlockEntity;
import io.github.capttracker.ancientwarfareautomation.menu.AnimalControlMenu;
import io.github.capttracker.ancientwarfareautomation.menu.AnimalFarmMenu;
import io.github.capttracker.ancientwarfareautomation.menu.AutoCraftingMenu;
import io.github.capttracker.ancientwarfareautomation.menu.BoundsAdjustMenu;
import io.github.capttracker.ancientwarfareautomation.menu.ChunkLoaderDeluxeMenu;
import io.github.capttracker.ancientwarfareautomation.menu.CropFarmMenu;
import io.github.capttracker.ancientwarfareautomation.menu.DockMenu;
import io.github.capttracker.ancientwarfareautomation.menu.InnMenu;
import io.github.capttracker.ancientwarfareautomation.menu.StableMenu;
import io.github.capttracker.ancientwarfareautomation.menu.MarketOffersMenu;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.InnBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.StableBlockEntity;
import io.github.capttracker.ancientwarfareautomation.menu.MarketStallMenu;
import io.github.capttracker.ancientwarfareautomation.menu.FishControlMenu;
import io.github.capttracker.ancientwarfareautomation.menu.FishFarmMenu;
import io.github.capttracker.ancientwarfareautomation.menu.FruitFarmMenu;
import io.github.capttracker.ancientwarfareautomation.menu.InventorySideMenu;
import io.github.capttracker.ancientwarfareautomation.menu.MailboxMenu;
import io.github.capttracker.ancientwarfareautomation.menu.QuarryBoundsMenu;
import io.github.capttracker.ancientwarfareautomation.menu.QuarryMenu;
import io.github.capttracker.ancientwarfareautomation.menu.StirlingGeneratorMenu;
import io.github.capttracker.ancientwarfareautomation.menu.TreeFarmMenu;
import io.github.capttracker.ancientwarfareautomation.menu.WarehouseControlMenu;
import io.github.capttracker.ancientwarfareautomation.menu.WarehouseCraftingStationMenu;
import io.github.capttracker.ancientwarfareautomation.menu.WarehouseInterfaceMenu;
import io.github.capttracker.ancientwarfareautomation.menu.WarehouseStockLinkerMenu;
import io.github.capttracker.ancientwarfareautomation.menu.WarehouseStockViewerMenu;
import io.github.capttracker.ancientwarfareautomation.menu.WarehouseStorageMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The menu types of the Automation module (one per 1.12.2 container) and the hooks that let the block entities open them.
 * Every menu is opened with the block position as extra data, which the client constructors read.
 *
 * <p>The 1.12.2 GUI ids map as follows (id: holder): 5 {@link #INVENTORY_SIDES}, 14 {@link #ANIMAL_CONTROL},
 * 15 {@link #AUTO_CRAFTING}, 16 {@link #FISH_CONTROL}, 17 {@link #MAILBOX}, 18 {@link #WAREHOUSE_CONTROL},
 * 19 {@link #WAREHOUSE_STORAGE}, 20 {@link #WAREHOUSE_STOCK_VIEWER}, 21 {@link #WAREHOUSE_INTERFACE},
 * 22 {@link #WAREHOUSE_CRAFTING_STATION}, 23 {@link #CHUNK_LOADER_DELUXE}, 24 {@link #QUARRY}, 25 {@link #TREE_FARM},
 * 26 {@link #ANIMAL_FARM}, 27 {@link #CROP_FARM}, 29 {@link #FISH_FARM}, 30 {@link #QUARRY_BOUNDS},
 * 31 {@link #STIRLING_GENERATOR}, 32 {@link #WAREHOUSE_STOCK_LINKER}, 46 {@link #BOUNDS}, 53 {@link #FRUIT_FARM}.
 *
 * <p>Wiring: {@code AwAutomationMenus.load()} and {@code AwAutomationMenus.registerEvents(modBus)} from the mod constructor
 * before {@code REG.register(modBus)}. {@code registerEvents} hands the menu constructors to the hooks the worksites, the
 * warehouse blocks and the stirling generator open their menus through.
 */
public final class AwAutomationMenus {
    private AwAutomationMenus() {}

    // worksite inventories
    public static final DeferredHolder<MenuType<?>, MenuType<QuarryMenu>> QUARRY =
            AncientWarfareAutomation.REG.menus.register("worksite_quarry", () -> IMenuTypeExtension.create(QuarryMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<TreeFarmMenu>> TREE_FARM =
            AncientWarfareAutomation.REG.menus.register("worksite_tree_farm", () -> IMenuTypeExtension.create(TreeFarmMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<AnimalFarmMenu>> ANIMAL_FARM =
            AncientWarfareAutomation.REG.menus.register("worksite_animal_farm", () -> IMenuTypeExtension.create(AnimalFarmMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<CropFarmMenu>> CROP_FARM =
            AncientWarfareAutomation.REG.menus.register("worksite_crop_farm", () -> IMenuTypeExtension.create(CropFarmMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<FishFarmMenu>> FISH_FARM =
            AncientWarfareAutomation.REG.menus.register("worksite_fish_farm", () -> IMenuTypeExtension.create(FishFarmMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<DockMenu>> DOCK =
            AncientWarfareAutomation.REG.menus.register("worksite_dock", () -> IMenuTypeExtension.create(DockMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<FruitFarmMenu>> FRUIT_FARM =
            AncientWarfareAutomation.REG.menus.register("worksite_fruit_farm", () -> IMenuTypeExtension.create(FruitFarmMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ApiaryMenu>> APIARY =
            AncientWarfareAutomation.REG.menus.register("worksite_apiary", () -> IMenuTypeExtension.create(ApiaryMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<SmelterMenu>> SMELTER =
            AncientWarfareAutomation.REG.menus.register("worksite_smelter", () -> IMenuTypeExtension.create(SmelterMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<MarketStallMenu>> MARKET_STALL =
            AncientWarfareAutomation.REG.menus.register("worksite_market_stall", () -> IMenuTypeExtension.create(MarketStallMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<InnMenu>> INN =
            AncientWarfareAutomation.REG.menus.register("worksite_inn", () -> IMenuTypeExtension.create(InnMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<StableMenu>> STABLE =
            AncientWarfareAutomation.REG.menus.register("worksite_stable", () -> IMenuTypeExtension.create(StableMenu::new));

    // worksite second screens
    public static final DeferredHolder<MenuType<?>, MenuType<MarketOffersMenu>> MARKET_OFFERS =
            AncientWarfareAutomation.REG.menus.register("worksite_market_offers", () -> IMenuTypeExtension.create(MarketOffersMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<AnimalControlMenu>> ANIMAL_CONTROL =
            AncientWarfareAutomation.REG.menus.register("worksite_animal_control", () -> IMenuTypeExtension.create(AnimalControlMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<FishControlMenu>> FISH_CONTROL =
            AncientWarfareAutomation.REG.menus.register("worksite_fish_control", () -> IMenuTypeExtension.create(FishControlMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<QuarryBoundsMenu>> QUARRY_BOUNDS =
            AncientWarfareAutomation.REG.menus.register("worksite_quarry_bounds", () -> IMenuTypeExtension.create(QuarryBoundsMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<BoundsAdjustMenu>> BOUNDS =
            AncientWarfareAutomation.REG.menus.register("worksite_bounds", () -> IMenuTypeExtension.create(BoundsAdjustMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<InventorySideMenu>> INVENTORY_SIDES =
            AncientWarfareAutomation.REG.menus.register("worksite_inventory_sides", () -> IMenuTypeExtension.create(InventorySideMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<AutoCraftingMenu>> AUTO_CRAFTING =
            AncientWarfareAutomation.REG.menus.register("worksite_auto_crafting", () -> IMenuTypeExtension.create(AutoCraftingMenu::new));

    // other blocks
    public static final DeferredHolder<MenuType<?>, MenuType<StirlingGeneratorMenu>> STIRLING_GENERATOR =
            AncientWarfareAutomation.REG.menus.register("stirling_generator", () -> IMenuTypeExtension.create(StirlingGeneratorMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<ChunkLoaderDeluxeMenu>> CHUNK_LOADER_DELUXE =
            AncientWarfareAutomation.REG.menus.register("chunk_loader_deluxe", () -> IMenuTypeExtension.create(ChunkLoaderDeluxeMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<MailboxMenu>> MAILBOX =
            AncientWarfareAutomation.REG.menus.register("mailbox", () -> IMenuTypeExtension.create(MailboxMenu::new));

    // warehouse
    public static final DeferredHolder<MenuType<?>, MenuType<WarehouseControlMenu>> WAREHOUSE_CONTROL =
            AncientWarfareAutomation.REG.menus.register("warehouse_control", () -> IMenuTypeExtension.create(WarehouseControlMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<WarehouseStorageMenu>> WAREHOUSE_STORAGE =
            AncientWarfareAutomation.REG.menus.register("warehouse_storage", () -> IMenuTypeExtension.create(WarehouseStorageMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<WarehouseInterfaceMenu>> WAREHOUSE_INTERFACE =
            AncientWarfareAutomation.REG.menus.register("warehouse_interface", () -> IMenuTypeExtension.create(WarehouseInterfaceMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<WarehouseStockViewerMenu>> WAREHOUSE_STOCK_VIEWER =
            AncientWarfareAutomation.REG.menus.register("warehouse_stock_viewer", () -> IMenuTypeExtension.create(WarehouseStockViewerMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<WarehouseStockLinkerMenu>> WAREHOUSE_STOCK_LINKER =
            AncientWarfareAutomation.REG.menus.register("warehouse_stock_linker", () -> IMenuTypeExtension.create(WarehouseStockLinkerMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<WarehouseCraftingStationMenu>> WAREHOUSE_CRAFTING_STATION =
            AncientWarfareAutomation.REG.menus.register("warehouse_crafting_station", () -> IMenuTypeExtension.create(WarehouseCraftingStationMenu::new));

    /** Forces this class (and so the registrations above) to initialise. Call from the mod constructor. */
    public static void load() {}

    /**
     * Gives the menu constructors to the block entities' open hooks ({@link WorksiteMenus}, {@link WarehouseMenus} and
     * {@code TileStirlingGenerator.setMenuFactory}). Call from the mod constructor.
     */
    public static void registerEvents(IEventBus modBus) {
        WorksiteMenus.setFactory(WorksiteMenus.Kind.QUARRY, (id, inventory, be) -> new QuarryMenu(id, inventory, (QuarryBlockEntity) be));
        WorksiteMenus.setFactory(WorksiteMenus.Kind.TREE_FARM, (id, inventory, be) -> new TreeFarmMenu(id, inventory, (TreeFarmBlockEntity) be));
        WorksiteMenus.setFactory(WorksiteMenus.Kind.CROP_FARM, (id, inventory, be) -> new CropFarmMenu(id, inventory, (CropFarmBlockEntity) be));
        WorksiteMenus.setFactory(WorksiteMenus.Kind.FRUIT_FARM, (id, inventory, be) -> new FruitFarmMenu(id, inventory, (FruitFarmBlockEntity) be));
        WorksiteMenus.setFactory(WorksiteMenus.Kind.ANIMAL_FARM, (id, inventory, be) -> new AnimalFarmMenu(id, inventory, (AnimalFarmBlockEntity) be));
        WorksiteMenus.setFactory(WorksiteMenus.Kind.ANIMAL_CONTROL, (id, inventory, be) -> new AnimalControlMenu(id, inventory, (AnimalFarmBlockEntity) be));
        WorksiteMenus.setFactory(WorksiteMenus.Kind.FISH_FARM, (id, inventory, be) -> new FishFarmMenu(id, inventory, (FishFarmBlockEntity) be));
        WorksiteMenus.setFactory(WorksiteMenus.Kind.FISH_CONTROL, (id, inventory, be) -> new FishControlMenu(id, inventory, (FishFarmBlockEntity) be));
        WorksiteMenus.setFactory(WorksiteMenus.Kind.DOCK, (id, inventory, be) -> new DockMenu(id, inventory, (DockBlockEntity) be));
        WorksiteMenus.setFactory(WorksiteMenus.Kind.APIARY, (id, inventory, be) -> new ApiaryMenu(id, inventory, (ApiaryBlockEntity) be));
        WorksiteMenus.setFactory(WorksiteMenus.Kind.SMELTER, (id, inventory, be) -> new SmelterMenu(id, inventory, (SmelterBlockEntity) be));
        WorksiteMenus.setFactory(WorksiteMenus.Kind.MARKET_STALL, (id, inventory, be) -> new MarketStallMenu(id, inventory, (MarketStallBlockEntity) be));
        WorksiteMenus.setFactory(WorksiteMenus.Kind.MARKET_OFFERS, (id, inventory, be) -> new MarketOffersMenu(id, inventory, (MarketStallBlockEntity) be));
        WorksiteMenus.setFactory(WorksiteMenus.Kind.INN, (id, inventory, be) -> new InnMenu(id, inventory, (InnBlockEntity) be));
        WorksiteMenus.setFactory(WorksiteMenus.Kind.STABLE, (id, inventory, be) -> new StableMenu(id, inventory, (StableBlockEntity) be));
        WorksiteMenus.setFactory(WorksiteMenus.Kind.AUTO_CRAFTING, (id, inventory, be) -> new AutoCraftingMenu(id, inventory, (AutoCraftingBlockEntity) be));

        WarehouseMenus.setControlFactory(WarehouseControlMenu::new);
        WarehouseMenus.setStorageFactory(WarehouseStorageMenu::new);
        WarehouseMenus.setInterfaceFactory(WarehouseInterfaceMenu::new);
        WarehouseMenus.setCraftingStationFactory(WarehouseCraftingStationMenu::new);
        WarehouseMenus.setStockViewerFactory(WarehouseStockViewerMenu::new);
        WarehouseMenus.setStockLinkerFactory(WarehouseStockLinkerMenu::new);
        WarehouseMenus.setMailboxFactory(MailboxMenu::new);

        TileStirlingGenerator.setMenuFactory(StirlingGeneratorMenu::new);
    }

    // ---- opening one menu from another (server side) ----

    private interface MenuBuilder {
        AbstractContainerMenu build(int containerId, Inventory inventory);
    }

    private static void open(ServerPlayer player, BlockEntity blockEntity, MenuBuilder builder) {
        player.openMenu(
                new SimpleMenuProvider((id, inventory, p) -> builder.build(id, inventory), blockEntity.getBlockState().getBlock().getName()),
                buf -> buf.writeBlockPos(blockEntity.getBlockPos()));
    }

    /** Opens the inventory side screen of a worksite (1.12.2 {@code GUI_WORKSITE_INVENTORY_SIDE_ADJUST}). */
    public static void openInventorySides(ServerPlayer player, InventoryWorksiteBlockEntity worksite) {
        open(player, worksite, (id, inventory) -> new InventorySideMenu(id, inventory, worksite));
    }

    /** Opens the work area screen of a bounded worksite or the warehouse control block (1.12.2 {@code GUI_WORKSITE_BOUNDS}). Ignored for any other block entity. */
    public static void openBounds(ServerPlayer player, BlockEntity blockEntity) {
        if (blockEntity instanceof IBoundedSite) {
            open(player, blockEntity, (id, inventory) -> new BoundsAdjustMenu(id, inventory, blockEntity));
        }
    }

    /** Opens the quarry's depth screen (1.12.2 {@code GUI_WORKSITE_QUARRY_BOUNDS}). */
    public static void openQuarryBounds(ServerPlayer player, QuarryBlockEntity quarry) {
        open(player, quarry, (id, inventory) -> new QuarryBoundsMenu(id, inventory, quarry));
    }
}
