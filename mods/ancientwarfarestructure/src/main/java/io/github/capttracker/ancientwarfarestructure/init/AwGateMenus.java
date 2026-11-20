/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: GUI_GATE_CONTROL and GUI_GATE_CONTROL_CREATIVE of core/network/NetworkHandler.
 */
package io.github.capttracker.ancientwarfarestructure.init;

import io.github.capttracker.ancientwarfarestructure.AncientWarfareStructure;
import io.github.capttracker.ancientwarfarestructure.entity.GateControl;
import io.github.capttracker.ancientwarfarestructure.menu.GateControlMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;

/** The gate control menu type (one type; the creative flag travels in the extra data). Call {@link #load()} before {@code REG.register}. */
public final class AwGateMenus {
    private AwGateMenus() {}

    public static final DeferredHolder<MenuType<?>, MenuType<GateControlMenu>> GATE_CONTROL =
            AncientWarfareStructure.REG.menus.register("gate_control", () -> IMenuTypeExtension.create(GateControlMenu::new));

    /** Forces this class to initialise and makes the sneaking click on a gate open the menu. */
    public static void load() {
        GateControl.setOpener(GateControlMenu::open);
    }
}
