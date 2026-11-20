/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/AutomationInputHandler, and the client config screen entry of
 * automation/proxy/ClientProxyAutomation (AutomationCategory).
 */
package io.github.capttracker.ancientwarfareautomation.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.capttracker.ancientwarfare.client.input.InputHandler;
import io.github.capttracker.ancientwarfareautomation.AncientWarfareAutomation;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * Client-only setup of the Automation module: the F8 key that toggles drawing the work bounds of worksites
 * (the {@code render_work_bounds} client option) and the module's entry in the in-game config screen. Nothing outside the
 * client package may reference this class.
 *
 * <p>Not ported from the client proxy: the 28 model sprites stitched for the block entity renderers and the renderer
 * bindings themselves (the rendering slices), and the 1.12.2 {@code GuiVehicleStats} registration (the vehicle
 * module's own client setup).
 */
@EventBusSubscriber(modid = AncientWarfareAutomation.MOD_ID, value = Dist.CLIENT)
public final class AutomationInputHandler {
    private static final String KEYBIND_CATEGORY = "keybind.category.awAutomation";
    private static final String KEY_TOGGLE_WORKBOUNDS_RENDER = "keybind.awAutomation.toggle_workbounds_render";

    public static final KeyMapping TOGGLE_WORKBOUNDS_RENDER = new KeyMapping(KEY_TOGGLE_WORKBOUNDS_RENDER, KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, InputConstants.KEY_F8, KEYBIND_CATEGORY);

    static {
        InputHandler.registerCallBack(TOGGLE_WORKBOUNDS_RENDER, AWAutomationStatics::toggleRenderWorkBounds);
    }

    private AutomationInputHandler() {}

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_WORKBOUNDS_RENDER);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // The in-game config screen is NeoForge's built-in one.
        ModList.get().getModContainerById(AncientWarfareAutomation.MOD_ID)
                .ifPresent(container -> container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new));
    }
}
