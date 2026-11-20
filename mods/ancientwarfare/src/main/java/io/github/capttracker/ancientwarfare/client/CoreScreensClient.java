/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the registerGui calls of core/proxy/ClientProxy and the block/item registerClient methods,
 * and core/gui/options/OptionsGuiFactory (replaced by NeoForge's config screen).
 */
package io.github.capttracker.ancientwarfare.client;

import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.ancientwarfare.client.screen.GuiBackpack;
import io.github.capttracker.ancientwarfare.client.screen.GuiInfoTool;
import io.github.capttracker.ancientwarfare.client.screen.GuiResearchBook;
import io.github.capttracker.ancientwarfare.client.screen.GuiResearchStation;
import io.github.capttracker.ancientwarfare.client.screen.manual.GuiManual;
import io.github.capttracker.ancientwarfare.init.AwCoreScreens;
import io.github.capttracker.ancientwarfare.init.AwCoreStations;
import io.github.capttracker.ancientwarfare.item.ManualItem;
import io.github.capttracker.ancientwarfare.manual.ManualContentRegistry;
import io.github.capttracker.ancientwarfare.menu.BackpackMenu;
import io.github.capttracker.ancientwarfare.menu.EngineeringStationMenu;
import io.github.capttracker.ancientwarfare.menu.InfoToolMenu;
import io.github.capttracker.ancientwarfare.menu.ManualMenu;
import io.github.capttracker.ancientwarfare.menu.ResearchBookMenu;
import io.github.capttracker.ancientwarfare.menu.ResearchStationMenu;
import io.github.capttracker.ancientwarfare.registry.RegistryLoader;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only setup of the core screens: the screen for every core menu, the manual (content parser, language lookup and
 * the item's opener) and the config screen. Nothing outside the client package may reference this class.
 *
 * <p>Registering the screens here means {@code AwClient.registerScreens} must no longer register the backpack and the
 * engineering bench (a second registration of the same menu type throws); see the port notes.
 */
@EventBusSubscriber(modid = AncientWarfareCore.MOD_ID, value = Dist.CLIENT)
public final class CoreScreensClient {
    private CoreScreensClient() {}

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.<BackpackMenu, GuiBackpack>register(AncientWarfareCore.BACKPACK_MENU.get(),
                (menu, inventory, title) -> new GuiBackpack(menu));
        event.<EngineeringStationMenu, EngineeringStationScreen>register(AncientWarfareCore.ENGINEERING_STATION_MENU.get(),
                (menu, inventory, title) -> new EngineeringStationScreen(menu));
        event.<ResearchStationMenu, GuiResearchStation>register(AwCoreStations.RESEARCH_STATION_MENU.get(),
                (menu, inventory, title) -> new GuiResearchStation(menu));
        event.<ResearchBookMenu, GuiResearchBook>register(AwCoreScreens.RESEARCH_BOOK_MENU.get(),
                (menu, inventory, title) -> new GuiResearchBook(menu));
        event.<InfoToolMenu, GuiInfoTool>register(AwCoreScreens.INFO_TOOL_MENU.get(),
                (menu, inventory, title) -> new GuiInfoTool(menu));
    }

    /**
     * The manual files are client resources read when the resource manager reloads (so a language change or F3+T
     * reloads the manual). The parser has to be known before that reload runs, which is why it is registered here.
     */
    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        ManualContentRegistry.setLanguageSupplier(() -> Minecraft.getInstance().getLanguageManager().getSelected());
        RegistryLoader.registerClientParser(new ManualContentRegistry.ManualContentParser());
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ManualItem.setClientOpener(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.setScreen(new GuiManual(new ManualMenu(minecraft.player.getInventory())));
            }
        });
        // The in-game config screen (1.12.2: OptionsGuiFactory, a stock GuiConfig) is NeoForge's built-in one now.
        ModList.get().getModContainerById(AncientWarfareCore.MOD_ID)
                .ifPresent(container -> container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new));
    }
}
