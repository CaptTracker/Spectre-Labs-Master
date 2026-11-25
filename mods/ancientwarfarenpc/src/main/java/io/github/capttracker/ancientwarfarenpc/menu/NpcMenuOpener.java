/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the NetworkHandler.openGui(player, GUI_NPC_*, entityId, 0, 0) calls of npc/entity/NpcBase, NpcTrader and the
 * NPC screens, and the container registration of npc/AncientWarfareNPC.
 */
package io.github.capttracker.ancientwarfarenpc.menu;

import io.github.capttracker.ancientwarfare.menu.AwMenu;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBard;
import io.github.capttracker.ancientwarfarenpc.entity.NpcInnkeeper;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBridge;
import io.github.capttracker.ancientwarfarenpc.entity.NpcTrader;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionBard;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionTrader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuConstructor;

import java.util.OptionalInt;

/**
 * Opens the NPC menus on the server. {@link #open} is what {@link NpcBridge} calls (the entity code asks for a menu by kind); the other methods
 * serve the requests a client makes from a screen ({@link NpcMenuPayloads.OpenNpcMenu}: the 1.12.2 screens called {@code openGui} on the client, which
 * sent the request to the server) and the spellcaster entity, which has no {@code NpcBridge} kind.
 *
 * <p>Every menu gets the NPC as its entity id in the extra data buffer and the server pushes its first state right after opening. The players that
 * may open each menu: the inventory, the bards and the spellcaster follow {@link NpcMenuBase#mayEdit}; the creative controls, the trade setup and the
 * spellcaster need a creative player; the trade menus are open to anyone (the entity decides when to offer them).
 */
public final class NpcMenuOpener {
    private NpcMenuOpener() {}

    /** The menu of an NPC by kind (installed as {@code NpcBridge.setGuiOpener}). */
    public static void open(ServerPlayer player, NpcBase npc, NpcBridge.Gui gui) {
        switch (gui) {
            case INVENTORY -> {
                if (NpcMenuBase.mayEdit(player, npc)) {
                    openMenu(player, npc, (id, inventory, p) -> new NpcInventoryMenu(id, inventory, npc));
                }
            }
            case PLAYER_OWNED_TRADE -> {
                if (npc instanceof NpcTrader trader) {
                    openMenu(player, npc, (id, inventory, p) -> new PlayerOwnedTradeMenu(id, inventory, trader));
                }
            }
            case BARD -> {
                if (npc instanceof NpcBard bard && NpcMenuBase.mayEdit(player, npc)) {
                    openMenu(player, npc, (id, inventory, p) -> new NpcBardMenu(id, inventory, bard));
                }
            }
            case FACTION_BARD -> {
                if (npc instanceof NpcFactionBard bard && NpcMenuBase.mayEdit(player, npc)) {
                    openMenu(player, npc, (id, inventory, p) -> new NpcFactionBardMenu(id, inventory, bard));
                }
            }
            case MARKET_SHOP -> {
                if (npc instanceof NpcTrader trader) {
                    openMenu(player, npc, (id, inventory, p) -> new MarketShopMenu(id, inventory, trader));
                }
            }
            case INN_SHOP -> {
                if (npc instanceof NpcInnkeeper keeper) {
                    openMenu(player, npc, (id, inventory, p) -> new InnShopMenu(id, inventory, keeper));
                }
            }
            case TALK -> openTalk(player, npc, NpcTalkMenu.TAB_TALK);
            case MORALE -> openTalk(player, npc, NpcTalkMenu.TAB_MORALE);
            case FACTION_TRADE_VIEW -> {
                if (npc instanceof NpcFactionTrader trader) {
                    openMenu(player, npc, (id, inventory, p) -> new FactionTradeViewMenu(id, inventory, trader));
                }
            }
        }
    }

    /** The talk screen on a tab; the morale tab only for the commanders of a player-owned NPC. Anyone who is not an enemy may talk. */
    public static void openTalk(ServerPlayer player, NpcBase npc, int tab) {
        if (!npc.isAlive() || !NpcMenuBase.mayEdit(player, npc) && npc.isHostileTowards(player)) {
            return;
        }
        int shown = tab == NpcTalkMenu.TAB_MORALE && NpcTalkMenu.hasMoraleTab(player, npc) ? tab : NpcTalkMenu.TAB_TALK;
        openMenu(player, npc, (id, inventory, p) -> new NpcTalkMenu(id, inventory, npc, shown));
    }

    /** The creative controls (1.12.2: {@code GUI_NPC_CREATIVE}, 44). Creative players only. */
    public static void openCreative(ServerPlayer player, NpcBase npc) {
        if (player.isCreative()) {
            openMenu(player, npc, (id, inventory, p) -> new NpcCreativeControlsMenu(id, inventory, npc));
        }
    }

    /** The trade setup of a faction trader (1.12.2: {@code GUI_NPC_FACTION_TRADE_SETUP}, 39). Creative players only. */
    public static void openTradeSetup(ServerPlayer player, NpcBase npc) {
        if (player.isCreative() && npc instanceof NpcFactionTrader trader) {
            openMenu(player, npc, (id, inventory, p) -> new FactionTradeSetupMenu(id, inventory, trader));
        }
    }

    /**
     * The spell editor (1.12.2: {@code GUI_NPC_FACTION_SPELLCASTER_WIZARDRY}, 62), for an NPC that implements {@link ISpellcasterNpc}; its
     * {@code openAltGui} calls this. Creative players only, and only if a {@link NpcSpellSupport.Catalog} is installed.
     */
    public static void openSpellcaster(ServerPlayer player, NpcBase npc) {
        if (player.isCreative() && npc instanceof ISpellcasterNpc && NpcSpellSupport.isAvailable()) {
            openMenu(player, npc, (id, inventory, p) -> new NpcSpellcasterMenu(id, inventory, npc));
        }
    }

    /** Serves a request of a client screen; the caller has checked that the NPC is alive and within reach. */
    static void openRequested(ServerPlayer player, NpcBase npc, NpcMenuPayloads.Request request) {
        switch (request) {
            // The screens that ask for these stay open until the other menu opens (they return to it when they close), so a refusal closes
            // the menu the player has open instead of leaving a screen that cannot be closed.
            case INVENTORY -> {
                if (NpcMenuBase.mayEdit(player, npc)) {
                    npc.openGUI(player);
                } else {
                    player.closeContainer();
                }
            }
            case ALT -> {
                if (npc.hasAltGui() && NpcMenuBase.mayEdit(player, npc)) {
                    npc.openAltGui(player);
                } else {
                    player.closeContainer();
                }
            }
            case TALK -> openTalk(player, npc, NpcTalkMenu.TAB_TALK);
            case MORALE -> openTalk(player, npc, NpcTalkMenu.TAB_MORALE);
            case CREATIVE -> openCreative(player, npc);
            case TRADE_SETUP -> openTradeSetup(player, npc);
        }
    }

    private static void openMenu(ServerPlayer player, NpcBase npc, MenuConstructor constructor) {
        OptionalInt opened = player.openMenu(new SimpleMenuProvider(constructor, npc.getDisplayName()), buf -> buf.writeVarInt(npc.getId()));
        if (opened.isPresent() && player.containerMenu instanceof AwMenu menu) {
            menu.sendInitData();
        }
    }
}
