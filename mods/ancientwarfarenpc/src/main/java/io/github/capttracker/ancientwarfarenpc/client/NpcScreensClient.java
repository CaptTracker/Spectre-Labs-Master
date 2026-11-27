/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the registerGui calls of npc/proxy/NpcClientProxy.
 */
package io.github.capttracker.ancientwarfarenpc.client;

import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.client.screen.GuiCombatOrder;
import io.github.capttracker.ancientwarfarenpc.client.screen.GuiNpcBard;
import io.github.capttracker.ancientwarfarenpc.client.screen.GuiNpcCreativeControls;
import io.github.capttracker.ancientwarfarenpc.client.screen.GuiNpcFactionBard;
import io.github.capttracker.ancientwarfarenpc.client.screen.GuiNpcInnShop;
import io.github.capttracker.ancientwarfarenpc.client.screen.GuiNpcMarketShop;
import io.github.capttracker.ancientwarfarenpc.client.screen.GuiNpcFactionSpellcasterWizardry;
import io.github.capttracker.ancientwarfarenpc.client.screen.GuiNpcFactionTradeSetup;
import io.github.capttracker.ancientwarfarenpc.client.screen.GuiNpcFactionTradeView;
import io.github.capttracker.ancientwarfarenpc.client.screen.GuiNpcInventory;
import io.github.capttracker.ancientwarfarenpc.client.screen.GuiNpcPlayerOwnedTrade;
import io.github.capttracker.ancientwarfarenpc.client.screen.GuiNpcTalk;
import io.github.capttracker.ancientwarfarenpc.client.screen.GuiRoutingOrder;
import io.github.capttracker.ancientwarfarenpc.client.screen.GuiTownHallInventory;
import io.github.capttracker.ancientwarfarenpc.client.screen.GuiSeaRoute;
import io.github.capttracker.ancientwarfarenpc.client.screen.GuiTradeOrder;
import io.github.capttracker.ancientwarfarenpc.client.screen.GuiUpkeepOrder;
import io.github.capttracker.ancientwarfarenpc.client.screen.GuiWorkOrder;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcMenus;
import io.github.capttracker.ancientwarfarenpc.menu.CombatOrderMenu;
import io.github.capttracker.ancientwarfarenpc.menu.FactionTradeSetupMenu;
import io.github.capttracker.ancientwarfarenpc.menu.FactionTradeViewMenu;
import io.github.capttracker.ancientwarfarenpc.menu.InnShopMenu;
import io.github.capttracker.ancientwarfarenpc.menu.MarketShopMenu;
import io.github.capttracker.ancientwarfarenpc.menu.NpcBardMenu;
import io.github.capttracker.ancientwarfarenpc.menu.NpcCreativeControlsMenu;
import io.github.capttracker.ancientwarfarenpc.menu.NpcFactionBardMenu;
import io.github.capttracker.ancientwarfarenpc.menu.NpcInventoryMenu;
import io.github.capttracker.ancientwarfarenpc.menu.NpcSpellcasterMenu;
import io.github.capttracker.ancientwarfarenpc.menu.NpcTalkMenu;
import io.github.capttracker.ancientwarfarenpc.menu.PlayerOwnedTradeMenu;
import io.github.capttracker.ancientwarfarenpc.menu.RoutingOrderMenu;
import io.github.capttracker.ancientwarfarenpc.menu.TownHallMenu;
import io.github.capttracker.ancientwarfarenpc.menu.SeaRouteMenu;
import io.github.capttracker.ancientwarfarenpc.menu.TradeOrderMenu;
import io.github.capttracker.ancientwarfarenpc.menu.UpkeepOrderMenu;
import io.github.capttracker.ancientwarfarenpc.menu.WorkOrderMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only setup of the NPC screens: the screen for each of the 14 menus. The four helper screens (skin selection, town hall death list, trade
 * template save and select) have no menu type of their own: they are shown over the screen they belong to. Nothing outside the client package may
 * reference this class.
 */
@EventBusSubscriber(modid = AncientWarfareNPC.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class NpcScreensClient {
    private NpcScreensClient() {}

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.<NpcInventoryMenu, GuiNpcInventory>register(AwNpcMenus.INVENTORY_MENU.get(), (menu, inventory, title) -> new GuiNpcInventory(menu));
        event.<NpcTalkMenu, GuiNpcTalk>register(AwNpcMenus.TALK_MENU.get(), (menu, inventory, title) -> new GuiNpcTalk(menu));
        event.<NpcCreativeControlsMenu, GuiNpcCreativeControls>register(AwNpcMenus.CREATIVE_MENU.get(), (menu, inventory, title) -> new GuiNpcCreativeControls(menu));
        event.<NpcBardMenu, GuiNpcBard>register(AwNpcMenus.BARD_MENU.get(), (menu, inventory, title) -> new GuiNpcBard(menu));
        event.<NpcFactionBardMenu, GuiNpcFactionBard>register(AwNpcMenus.FACTION_BARD_MENU.get(), (menu, inventory, title) -> new GuiNpcFactionBard(menu));
        event.<NpcSpellcasterMenu, GuiNpcFactionSpellcasterWizardry>register(AwNpcMenus.SPELLCASTER_MENU.get(),
                (menu, inventory, title) -> new GuiNpcFactionSpellcasterWizardry(menu));
        event.<FactionTradeSetupMenu, GuiNpcFactionTradeSetup>register(AwNpcMenus.FACTION_TRADE_SETUP_MENU.get(),
                (menu, inventory, title) -> new GuiNpcFactionTradeSetup(menu));
        event.<FactionTradeViewMenu, GuiNpcFactionTradeView>register(AwNpcMenus.FACTION_TRADE_VIEW_MENU.get(),
                (menu, inventory, title) -> new GuiNpcFactionTradeView(menu));
        event.<MarketShopMenu, GuiNpcMarketShop>register(AwNpcMenus.MARKET_SHOP_MENU.get(), (menu, inventory, title) -> new GuiNpcMarketShop(menu));
        event.<InnShopMenu, GuiNpcInnShop>register(AwNpcMenus.INN_SHOP_MENU.get(), (menu, inventory, title) -> new GuiNpcInnShop(menu));
        event.<PlayerOwnedTradeMenu, GuiNpcPlayerOwnedTrade>register(AwNpcMenus.PLAYER_OWNED_TRADE_MENU.get(),
                (menu, inventory, title) -> new GuiNpcPlayerOwnedTrade(menu));
        event.<TownHallMenu, GuiTownHallInventory>register(AwNpcMenus.TOWN_HALL_MENU.get(), (menu, inventory, title) -> new GuiTownHallInventory(menu));
        event.<WorkOrderMenu, GuiWorkOrder>register(AwNpcMenus.WORK_ORDER_MENU.get(), (menu, inventory, title) -> new GuiWorkOrder(menu));
        event.<UpkeepOrderMenu, GuiUpkeepOrder>register(AwNpcMenus.UPKEEP_ORDER_MENU.get(), (menu, inventory, title) -> new GuiUpkeepOrder(menu));
        event.<CombatOrderMenu, GuiCombatOrder>register(AwNpcMenus.COMBAT_ORDER_MENU.get(), (menu, inventory, title) -> new GuiCombatOrder(menu));
        event.<RoutingOrderMenu, GuiRoutingOrder>register(AwNpcMenus.ROUTING_ORDER_MENU.get(), (menu, inventory, title) -> new GuiRoutingOrder(menu));
        event.<TradeOrderMenu, GuiTradeOrder>register(AwNpcMenus.TRADE_ORDER_MENU.get(), (menu, inventory, title) -> new GuiTradeOrder(menu));
        event.<SeaRouteMenu, GuiSeaRoute>register(AwNpcMenus.SEA_ROUTE_MENU.get(), (menu, inventory, title) -> new GuiSeaRoute(menu));
    }
}
