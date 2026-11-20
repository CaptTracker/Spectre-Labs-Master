/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the registerGui calls of automation/proxy/ClientProxyAutomation and the
 * registerClient methods of the automation blocks.
 */
package io.github.capttracker.ancientwarfareautomation.client;

import io.github.capttracker.ancientwarfareautomation.AncientWarfareAutomation;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiChunkLoaderDeluxe;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiMailboxInventory;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiStirlingGenerator;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiWarehouseControl;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiWarehouseCraftingStation;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiWarehouseInterface;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiWarehouseStockLinker;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiWarehouseStockViewer;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiWarehouseStorage;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiWorksiteAnimalControl;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiWorksiteAnimalFarm;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiWorksiteAutoCrafting;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiWorksiteBoundsAdjust;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiWorksiteCropFarm;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiWorksiteApiary;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiWorksiteSmelter;
import io.github.capttracker.ancientwarfareautomation.menu.ApiaryMenu;
import io.github.capttracker.ancientwarfareautomation.menu.SmelterMenu;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiWorksiteDock;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiWorksiteInn;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiWorksiteStable;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiWorksiteMarketOffers;
import io.github.capttracker.ancientwarfareautomation.menu.InnMenu;
import io.github.capttracker.ancientwarfareautomation.menu.StableMenu;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiWorksiteMarketStall;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiWorksiteFishControl;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiWorksiteFishFarm;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiWorksiteFruitFarm;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiWorksiteInventorySideSelection;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiWorksiteQuarry;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiWorksiteQuarryBounds;
import io.github.capttracker.ancientwarfareautomation.client.screen.GuiWorksiteTreeFarm;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import io.github.capttracker.ancientwarfareautomation.menu.AnimalControlMenu;
import io.github.capttracker.ancientwarfareautomation.menu.AnimalFarmMenu;
import io.github.capttracker.ancientwarfareautomation.menu.AutoCraftingMenu;
import io.github.capttracker.ancientwarfareautomation.menu.BoundsAdjustMenu;
import io.github.capttracker.ancientwarfareautomation.menu.ChunkLoaderDeluxeMenu;
import io.github.capttracker.ancientwarfareautomation.menu.CropFarmMenu;
import io.github.capttracker.ancientwarfareautomation.menu.DockMenu;
import io.github.capttracker.ancientwarfareautomation.menu.MarketOffersMenu;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only setup of the Automation screens: the screen of every Automation menu type. Nothing outside the client
 * package may reference this class. (The two mailbox sub screens and the research crafting element are not menu screens;
 * the screens that use them create them.)
 */
@EventBusSubscriber(modid = AncientWarfareAutomation.MOD_ID, value = Dist.CLIENT)
public final class AutomationScreensClient {
    private AutomationScreensClient() {}

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        // worksite inventories
        event.<QuarryMenu, GuiWorksiteQuarry>register(AwAutomationMenus.QUARRY.get(), (menu, inventory, title) -> new GuiWorksiteQuarry(menu));
        event.<TreeFarmMenu, GuiWorksiteTreeFarm>register(AwAutomationMenus.TREE_FARM.get(), (menu, inventory, title) -> new GuiWorksiteTreeFarm(menu));
        event.<AnimalFarmMenu, GuiWorksiteAnimalFarm>register(AwAutomationMenus.ANIMAL_FARM.get(), (menu, inventory, title) -> new GuiWorksiteAnimalFarm(menu));
        event.<CropFarmMenu, GuiWorksiteCropFarm>register(AwAutomationMenus.CROP_FARM.get(), (menu, inventory, title) -> new GuiWorksiteCropFarm(menu));
        event.<ApiaryMenu, GuiWorksiteApiary>register(AwAutomationMenus.APIARY.get(), (menu, inventory, title) -> new GuiWorksiteApiary(menu));
        event.<SmelterMenu, GuiWorksiteSmelter>register(AwAutomationMenus.SMELTER.get(), (menu, inventory, title) -> new GuiWorksiteSmelter(menu));
        event.<MarketStallMenu, GuiWorksiteMarketStall>register(AwAutomationMenus.MARKET_STALL.get(), (menu, inventory, title) -> new GuiWorksiteMarketStall(menu));
        event.<InnMenu, GuiWorksiteInn>register(AwAutomationMenus.INN.get(), (menu, inventory, title) -> new GuiWorksiteInn(menu));
        event.<StableMenu, GuiWorksiteStable>register(AwAutomationMenus.STABLE.get(), (menu, inventory, title) -> new GuiWorksiteStable(menu));
        event.<FishFarmMenu, GuiWorksiteFishFarm>register(AwAutomationMenus.FISH_FARM.get(), (menu, inventory, title) -> new GuiWorksiteFishFarm(menu));
        event.<FruitFarmMenu, GuiWorksiteFruitFarm>register(AwAutomationMenus.FRUIT_FARM.get(), (menu, inventory, title) -> new GuiWorksiteFruitFarm(menu));

        // worksite second screens
        event.<AnimalControlMenu, GuiWorksiteAnimalControl>register(AwAutomationMenus.ANIMAL_CONTROL.get(), (menu, inventory, title) -> new GuiWorksiteAnimalControl(menu));
        event.<MarketOffersMenu, GuiWorksiteMarketOffers>register(AwAutomationMenus.MARKET_OFFERS.get(), (menu, inventory, title) -> new GuiWorksiteMarketOffers(menu));
        event.<DockMenu, GuiWorksiteDock>register(AwAutomationMenus.DOCK.get(), (menu, inventory, title) -> new GuiWorksiteDock(menu));
        event.<FishControlMenu, GuiWorksiteFishControl>register(AwAutomationMenus.FISH_CONTROL.get(), (menu, inventory, title) -> new GuiWorksiteFishControl(menu));
        event.<QuarryBoundsMenu, GuiWorksiteQuarryBounds>register(AwAutomationMenus.QUARRY_BOUNDS.get(), (menu, inventory, title) -> new GuiWorksiteQuarryBounds(menu));
        event.<BoundsAdjustMenu, GuiWorksiteBoundsAdjust>register(AwAutomationMenus.BOUNDS.get(), (menu, inventory, title) -> new GuiWorksiteBoundsAdjust(menu));
        event.<InventorySideMenu, GuiWorksiteInventorySideSelection>register(AwAutomationMenus.INVENTORY_SIDES.get(), (menu, inventory, title) -> new GuiWorksiteInventorySideSelection(menu));
        event.<AutoCraftingMenu, GuiWorksiteAutoCrafting>register(AwAutomationMenus.AUTO_CRAFTING.get(), (menu, inventory, title) -> new GuiWorksiteAutoCrafting(menu));

        // other blocks
        event.<StirlingGeneratorMenu, GuiStirlingGenerator>register(AwAutomationMenus.STIRLING_GENERATOR.get(), (menu, inventory, title) -> new GuiStirlingGenerator(menu));
        event.<ChunkLoaderDeluxeMenu, GuiChunkLoaderDeluxe>register(AwAutomationMenus.CHUNK_LOADER_DELUXE.get(), (menu, inventory, title) -> new GuiChunkLoaderDeluxe(menu));
        event.<MailboxMenu, GuiMailboxInventory>register(AwAutomationMenus.MAILBOX.get(), (menu, inventory, title) -> new GuiMailboxInventory(menu));

        // warehouse
        event.<WarehouseControlMenu, GuiWarehouseControl>register(AwAutomationMenus.WAREHOUSE_CONTROL.get(), (menu, inventory, title) -> new GuiWarehouseControl(menu));
        event.<WarehouseStorageMenu, GuiWarehouseStorage>register(AwAutomationMenus.WAREHOUSE_STORAGE.get(), (menu, inventory, title) -> new GuiWarehouseStorage(menu));
        event.<WarehouseInterfaceMenu, GuiWarehouseInterface>register(AwAutomationMenus.WAREHOUSE_INTERFACE.get(), (menu, inventory, title) -> new GuiWarehouseInterface(menu));
        event.<WarehouseStockViewerMenu, GuiWarehouseStockViewer>register(AwAutomationMenus.WAREHOUSE_STOCK_VIEWER.get(), (menu, inventory, title) -> new GuiWarehouseStockViewer(menu));
        event.<WarehouseStockLinkerMenu, GuiWarehouseStockLinker>register(AwAutomationMenus.WAREHOUSE_STOCK_LINKER.get(), (menu, inventory, title) -> new GuiWarehouseStockLinker(menu));
        event.<WarehouseCraftingStationMenu, GuiWarehouseCraftingStation>register(AwAutomationMenus.WAREHOUSE_CRAFTING_STATION.get(), (menu, inventory, title) -> new GuiWarehouseCraftingStation(menu));
    }
}
