/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/container/ContainerResearchBook.
 */
package io.github.capttracker.ancientwarfare.menu;

import io.github.capttracker.ancientwarfare.init.AwCoreScreens;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;

/**
 * The research book's menu. It has no slots: the original added the player slots and then moved them out of sight.
 * The book screen reads the research goals and recipes the client already has.
 *
 * <p>Opened by the server with {@link #open}. The research bench screen also builds one on the client alone
 * ({@code new ResearchBookMenu(-1, inventory)}) to show the book over the bench.
 */
public class ResearchBookMenu extends AwMenu {
    /** Client side of {@link #open}: nothing is sent. */
    public ResearchBookMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory);
    }

    public ResearchBookMenu(int containerId, Inventory inventory) {
        super(AwCoreScreens.RESEARCH_BOOK_MENU.get(), containerId, inventory);
    }

    /** Opens the book screen for the player (1.12.2: GUI_RESEARCH_BOOK, 45). */
    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ResearchBookMenu(id, inventory),
                Component.translatable("item.ancientwarfare.research_book")), buf -> {});
    }
}
