/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: replaces the NetworkHandler.INSTANCE.openGui(player, GUI_NPC_*_ORDER, 0, 0, 0) calls of the order items, which belong to
 * the menu slice (same idea as entity.NpcBridge for the NPC GUIs).
 */
package io.github.capttracker.ancientwarfarenpc.item.order;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * The hook the menu slice installs to open the editor of an order slip. The menu reads and writes the order in the stack of the given hand
 * (1.12.2: the held item, GUI ids 6, 34, 35, 36 and 37). If no opener is installed, nothing opens.
 */
public final class OrderGuiBridge {
    /** The order editors. */
    public enum Gui {
        /** GUI_NPC_WORK_ORDER (34). */
        WORK_ORDER,
        /** GUI_NPC_UPKEEP_ORDER (35). */
        UPKEEP_ORDER,
        /** GUI_NPC_COMBAT_ORDER (36). */
        COMBAT_ORDER,
        /** GUI_NPC_ROUTING_ORDER (37). */
        ROUTING_ORDER,
        /** GUI_NPC_TRADE_ORDER (6). */
        TRADE_ORDER
    }

    @FunctionalInterface
    public interface GuiOpener {
        void open(ServerPlayer player, InteractionHand hand, Gui gui);
    }

    @Nullable
    private static volatile GuiOpener opener;

    private OrderGuiBridge() {}

    /** Called once by the menu slice. */
    public static void setGuiOpener(GuiOpener guiOpener) {
        opener = guiOpener;
    }

    /** Opens the editor on the server; does nothing for a client player or when no opener is installed. */
    public static void open(Player player, InteractionHand hand, Gui gui) {
        GuiOpener guiOpener = opener;
        if (guiOpener != null && player instanceof ServerPlayer serverPlayer) {
            guiOpener.open(serverPlayer, hand, gui);
        }
    }
}
