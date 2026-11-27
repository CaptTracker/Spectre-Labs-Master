/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the GUI ids GUI_NPC_* of core/network/NetworkHandler and the container registration of npc/AncientWarfareNPC.
 */
package io.github.capttracker.ancientwarfarenpc.init;

import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.block.entity.TownHallBlockEntity;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBridge;
import io.github.capttracker.ancientwarfarenpc.item.order.OrderGuiBridge;
import io.github.capttracker.ancientwarfarenpc.menu.CombatOrderMenu;
import io.github.capttracker.ancientwarfarenpc.menu.FactionTradeSetupMenu;
import io.github.capttracker.ancientwarfarenpc.menu.FactionTradeViewMenu;
import io.github.capttracker.ancientwarfarenpc.menu.MarketShopMenu;
import io.github.capttracker.ancientwarfarenpc.menu.InnShopMenu;
import io.github.capttracker.ancientwarfarenpc.menu.NpcBardMenu;
import io.github.capttracker.ancientwarfarenpc.menu.NpcCreativeControlsMenu;
import io.github.capttracker.ancientwarfarenpc.menu.NpcFactionBardMenu;
import io.github.capttracker.ancientwarfarenpc.menu.NpcInventoryMenu;
import io.github.capttracker.ancientwarfarenpc.menu.NpcMenuOpener;
import io.github.capttracker.ancientwarfarenpc.menu.NpcMenuPayloads;
import io.github.capttracker.ancientwarfarenpc.menu.NpcSpellcasterMenu;
import io.github.capttracker.ancientwarfarenpc.menu.NpcTalkMenu;
import io.github.capttracker.ancientwarfarenpc.menu.PlayerOwnedTradeMenu;
import io.github.capttracker.ancientwarfarenpc.menu.RoutingOrderMenu;
import io.github.capttracker.ancientwarfarenpc.menu.SeaRouteMenu;
import io.github.capttracker.ancientwarfarenpc.menu.TownHallMenu;
import io.github.capttracker.ancientwarfarenpc.menu.TradeOrderMenu;
import io.github.capttracker.ancientwarfarenpc.menu.UpkeepOrderMenu;
import io.github.capttracker.ancientwarfarenpc.menu.WorkOrderMenu;
import io.github.capttracker.ancientwarfarenpc.network.NpcNetwork;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The menu types of the NPC module (one per 1.12.2 container) and the hooks that open them.
 *
 * <p>The 1.12.2 GUI ids map as follows (id: holder): 4 {@link #INVENTORY_MENU}, 6 {@link #TRADE_ORDER_MENU}, 34 {@link #WORK_ORDER_MENU},
 * 35 {@link #UPKEEP_ORDER_MENU}, 36 {@link #COMBAT_ORDER_MENU}, 37 {@link #ROUTING_ORDER_MENU}, 39 {@link #FACTION_TRADE_SETUP_MENU},
 * 41 {@link #TOWN_HALL_MENU}, 42 {@link #FACTION_TRADE_VIEW_MENU}, 43 {@link #BARD_MENU}, 44 {@link #CREATIVE_MENU},
 * 47 {@link #PLAYER_OWNED_TRADE_MENU}, 49 {@link #FACTION_BARD_MENU}, 62 {@link #SPELLCASTER_MENU}.
 *
 * <p>Wiring: {@code AwNpcMenus.load()} and {@code AwNpcMenus.registerEvents(modBus)} from the mod constructor before {@code REG.register(modBus)}.
 * {@code registerEvents} installs the hooks of the entity, item and block entity slices and registers the payload. The screens are registered by
 * {@code client.NpcScreensClient} (client only, automatic). The spellcaster menu is registered always (menu types are cheap); it only opens if a
 * spell catalog is installed ({@code NpcSpellSupport}).
 */
public final class AwNpcMenus {
    private AwNpcMenus() {}

    // 1.12.2: GUI_NPC_INVENTORY (4)
    public static final DeferredHolder<MenuType<?>, MenuType<NpcInventoryMenu>> INVENTORY_MENU =
            AncientWarfareNPC.REG.menus.register("npc_inventory", () -> IMenuTypeExtension.create(NpcInventoryMenu::new));
    // GUI_NPC_CREATIVE (44)
    public static final DeferredHolder<MenuType<?>, MenuType<NpcCreativeControlsMenu>> CREATIVE_MENU =
            AncientWarfareNPC.REG.menus.register("npc_creative", () -> IMenuTypeExtension.create(NpcCreativeControlsMenu::new));
    // GUI_NPC_BARD (43)
    public static final DeferredHolder<MenuType<?>, MenuType<NpcBardMenu>> BARD_MENU =
            AncientWarfareNPC.REG.menus.register("npc_bard", () -> IMenuTypeExtension.create(NpcBardMenu::new));
    // GUI_NPC_FACTION_BARD (49)
    public static final DeferredHolder<MenuType<?>, MenuType<NpcFactionBardMenu>> FACTION_BARD_MENU =
            AncientWarfareNPC.REG.menus.register("npc_faction_bard", () -> IMenuTypeExtension.create(NpcFactionBardMenu::new));
    // GUI_NPC_FACTION_SPELLCASTER_WIZARDRY (62)
    public static final DeferredHolder<MenuType<?>, MenuType<NpcSpellcasterMenu>> SPELLCASTER_MENU =
            AncientWarfareNPC.REG.menus.register("npc_faction_spellcaster", () -> IMenuTypeExtension.create(NpcSpellcasterMenu::new));
    // GUI_NPC_FACTION_TRADE_SETUP (39)
    public static final DeferredHolder<MenuType<?>, MenuType<FactionTradeSetupMenu>> FACTION_TRADE_SETUP_MENU =
            AncientWarfareNPC.REG.menus.register("npc_faction_trade_setup", () -> IMenuTypeExtension.create(FactionTradeSetupMenu::new));
    // GUI_NPC_FACTION_TRADE_VIEW (42)
    public static final DeferredHolder<MenuType<?>, MenuType<FactionTradeViewMenu>> FACTION_TRADE_VIEW_MENU =
            AncientWarfareNPC.REG.menus.register("npc_faction_trade_view", () -> IMenuTypeExtension.create(FactionTradeViewMenu::new));
    // GUI_NPC_PLAYER_OWNED_TRADE (47)
    public static final DeferredHolder<MenuType<?>, MenuType<PlayerOwnedTradeMenu>> PLAYER_OWNED_TRADE_MENU =
            AncientWarfareNPC.REG.menus.register("npc_player_owned_trade", () -> IMenuTypeExtension.create(PlayerOwnedTradeMenu::new));
    // rework phase 5: the shop of a market stall trader
    public static final DeferredHolder<MenuType<?>, MenuType<MarketShopMenu>> MARKET_SHOP_MENU =
            AncientWarfareNPC.REG.menus.register("npc_market_shop", () -> IMenuTypeExtension.create(MarketShopMenu::new));
    // rework phase 5: the meal shop of an innkeeper
    public static final DeferredHolder<MenuType<?>, MenuType<InnShopMenu>> INN_SHOP_MENU =
            AncientWarfareNPC.REG.menus.register("npc_inn_shop", () -> IMenuTypeExtension.create(InnShopMenu::new));
    // playtest fixes: talking to an NPC, and its morale
    public static final DeferredHolder<MenuType<?>, MenuType<NpcTalkMenu>> TALK_MENU =
            AncientWarfareNPC.REG.menus.register("npc_talk", () -> IMenuTypeExtension.create(NpcTalkMenu::new));
    // GUI_NPC_TOWN_HALL (41)
    public static final DeferredHolder<MenuType<?>, MenuType<TownHallMenu>> TOWN_HALL_MENU =
            AncientWarfareNPC.REG.menus.register("town_hall", () -> IMenuTypeExtension.create(TownHallMenu::new));
    // GUI_NPC_WORK_ORDER (34)
    public static final DeferredHolder<MenuType<?>, MenuType<WorkOrderMenu>> WORK_ORDER_MENU =
            AncientWarfareNPC.REG.menus.register("work_order", () -> IMenuTypeExtension.create(WorkOrderMenu::new));
    // GUI_NPC_UPKEEP_ORDER (35)
    public static final DeferredHolder<MenuType<?>, MenuType<UpkeepOrderMenu>> UPKEEP_ORDER_MENU =
            AncientWarfareNPC.REG.menus.register("upkeep_order", () -> IMenuTypeExtension.create(UpkeepOrderMenu::new));
    // GUI_NPC_COMBAT_ORDER (36)
    public static final DeferredHolder<MenuType<?>, MenuType<CombatOrderMenu>> COMBAT_ORDER_MENU =
            AncientWarfareNPC.REG.menus.register("combat_order", () -> IMenuTypeExtension.create(CombatOrderMenu::new));
    // GUI_NPC_ROUTING_ORDER (37)
    public static final DeferredHolder<MenuType<?>, MenuType<RoutingOrderMenu>> ROUTING_ORDER_MENU =
            AncientWarfareNPC.REG.menus.register("routing_order", () -> IMenuTypeExtension.create(RoutingOrderMenu::new));
    // GUI_NPC_TRADE_ORDER (6)
    public static final DeferredHolder<MenuType<?>, MenuType<TradeOrderMenu>> TRADE_ORDER_MENU =
            AncientWarfareNPC.REG.menus.register("trade_order", () -> IMenuTypeExtension.create(TradeOrderMenu::new));
    // rework phase 11: the sea route chart
    public static final DeferredHolder<MenuType<?>, MenuType<SeaRouteMenu>> SEA_ROUTE_MENU =
            AncientWarfareNPC.REG.menus.register("sea_route", () -> IMenuTypeExtension.create(SeaRouteMenu::new));

    /** Forces this class (and so the registrations above) to initialise. Call from the mod constructor. */
    public static void load() {}

    /** Installs the hooks that open the menus and registers the request payload. Call from the mod constructor. */
    public static void registerEvents(IEventBus modBus) {
        NpcBridge.setGuiOpener(NpcMenuOpener::open);
        OrderGuiBridge.setGuiOpener((player, hand, gui) -> {
            switch (gui) {
                case WORK_ORDER -> WorkOrderMenu.open(player, hand);
                case UPKEEP_ORDER -> UpkeepOrderMenu.open(player, hand);
                case COMBAT_ORDER -> CombatOrderMenu.open(player, hand);
                case ROUTING_ORDER -> RoutingOrderMenu.open(player, hand);
                case TRADE_ORDER -> TradeOrderMenu.open(player, hand);
                case SEA_ROUTE -> SeaRouteMenu.open(player, hand);
            }
        });
        TownHallBlockEntity.setMenuOpener(TownHallMenu::open);
        modBus.addListener(AwNpcMenus::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        NpcMenuPayloads.registerPayloads(event.registrar(NpcNetwork.VERSION));
    }
}
