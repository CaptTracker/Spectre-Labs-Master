/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: replaces the direct calls of NpcBase to ItemNpcSpawner.getSpawnerItemForNpc and to
 * NetworkHandler.openGui (GUI ids), which live in other slices of this module.
 */
package io.github.capttracker.ancientwarfarenpc.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Hooks that the item slice and the menu slice of the NPC module install at startup, so that the entity code does not
 * depend on them (same idea as the Vehicles module's {@code VehicleBridge}). If a hook is not installed the feature is
 * absent: no spawner stack (repack gives nothing, pick block gives nothing) and no GUI opens.
 */
public final class NpcBridge {
    private NpcBridge() {}

    /** The NPC GUIs the entity classes open (the 1.12.2 GUI ids of {@code NetworkHandler}). */
    public enum Gui {
        /** GUI_NPC_INVENTORY (4): the equipment, orders and upkeep slots. */
        INVENTORY,
        /** GUI_NPC_PLAYER_OWNED_TRADE (47): a stranger trading with a player-owned trader. */
        PLAYER_OWNED_TRADE,
        /** GUI_NPC_BARD (43). */
        BARD,
        /** GUI_NPC_FACTION_BARD (49). */
        FACTION_BARD,
        /** GUI_NPC_FACTION_TRADE_VIEW (42). */
        FACTION_TRADE_VIEW,
        /** The shop of a trader that staffs a market stall (rework phase 5). */
        MARKET_SHOP,
        /** The meal shop of an innkeeper at an inn (rework phase 5). */
        INN_SHOP
    }

    /** Builds the spawner item of an NPC (1.12.2: {@code ItemNpcSpawner.getSpawnerItemForNpc}, which calls {@code writeAdditionalItemData}). */
    @FunctionalInterface
    public interface SpawnerStackFactory {
        ItemStack create(NpcBase npc);
    }

    /** Opens an NPC menu for a player; the entity id goes into the menu's extra data buffer (1.12.2: {@code openGui(player, id, entityId, 0, 0)}). */
    @FunctionalInterface
    public interface GuiOpener {
        void open(ServerPlayer player, NpcBase npc, Gui gui);
    }

    @Nullable
    private static volatile SpawnerStackFactory spawnerStackFactory;
    @Nullable
    private static volatile GuiOpener guiOpener;

    /** Called once by the item slice ({@code NpcSpawnerItem}). */
    public static void setSpawnerStackFactory(SpawnerStackFactory factory) {
        spawnerStackFactory = factory;
    }

    /** Called once by the menu slice. */
    public static void setGuiOpener(GuiOpener opener) {
        guiOpener = opener;
    }

    public static ItemStack createSpawnerStack(NpcBase npc) {
        SpawnerStackFactory factory = spawnerStackFactory;
        return factory == null ? ItemStack.EMPTY : factory.create(npc);
    }

    public static void openGui(ServerPlayer player, NpcBase npc, Gui gui) {
        GuiOpener opener = guiOpener;
        if (opener != null) {
            opener.open(player, npc, gui);
        }
    }
}
