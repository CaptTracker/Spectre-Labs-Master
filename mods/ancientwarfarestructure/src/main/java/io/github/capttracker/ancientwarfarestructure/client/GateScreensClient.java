/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 */
package io.github.capttracker.ancientwarfarestructure.client;

import io.github.capttracker.ancientwarfarestructure.AncientWarfareStructure;
import io.github.capttracker.ancientwarfarestructure.client.screen.GuiGateControl;
import io.github.capttracker.ancientwarfarestructure.init.AwGateMenus;
import io.github.capttracker.ancientwarfarestructure.menu.GateControlMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only registration of the gate control screen. Nothing outside the client package may reference this class. */
@EventBusSubscriber(modid = AncientWarfareStructure.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class GateScreensClient {
    private GateScreensClient() {}

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.<GateControlMenu, GuiGateControl>register(AwGateMenus.GATE_CONTROL.get(), (menu, inventory, title) -> new GuiGateControl(menu));
    }
}
