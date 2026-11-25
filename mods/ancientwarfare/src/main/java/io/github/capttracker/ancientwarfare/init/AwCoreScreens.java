/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the GUI ids of core/network/NetworkHandler (GUI_RESEARCH_BOOK 45, GUI_INFO_TOOL 57)
 * and the container registration of core/AncientWarfareCore.
 */
package io.github.capttracker.ancientwarfare.init;

import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.ancientwarfare.item.InfoToolItem;
import io.github.capttracker.ancientwarfare.menu.InfoToolMenu;
import io.github.capttracker.ancientwarfare.menu.ResearchBookMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Registrations of the core screens slice: the menu types of the research book and the info tool, and the hook that
 * opens the info tool's picker. The manual has no menu type (it is a client-only screen), and the other core menus
 * (backpack, engineering bench, research bench) are registered by their own slices.
 *
 * <p>Wiring: {@code AwCoreScreens.load()} and {@code AwCoreScreens.registerEvents(modBus)} from the mod constructor
 * before {@code REG.register(modBus)}.
 */
public final class AwCoreScreens {
    private AwCoreScreens() {}

    // 1.12.2: GUI_RESEARCH_BOOK (45)
    public static final DeferredHolder<MenuType<?>, MenuType<ResearchBookMenu>> RESEARCH_BOOK_MENU =
            AncientWarfareCore.REG.menus.register("research_book", () -> IMenuTypeExtension.create(ResearchBookMenu::new));

    // 1.12.2: GUI_INFO_TOOL (57)
    public static final DeferredHolder<MenuType<?>, MenuType<InfoToolMenu>> INFO_TOOL_MENU =
            AncientWarfareCore.REG.menus.register("info_tool", () -> IMenuTypeExtension.create(InfoToolMenu::new));

    /** Forces this class (and so the registrations above) to initialise. Call from the mod constructor. */
    public static void load() {}

    /** Installs the info tool's picker opener. Call from the mod constructor. */
    public static void registerEvents(IEventBus modBus) {
        InfoToolItem.setGuiOpener(InfoToolMenu::open);
    }
}
