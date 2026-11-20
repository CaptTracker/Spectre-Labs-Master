/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/init/AWCoreLoot (the loot part is data now) and the registration
 * done in core/AncientWarfareCore for utilities, config, commands and soft dependencies.
 */
package io.github.capttracker.ancientwarfare.init;

import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.ancientwarfare.command.CommandUtils;
import io.github.capttracker.ancientwarfare.compat.CompatLoader;
import io.github.capttracker.ancientwarfare.compat.ftb.FTBCompat;
import io.github.capttracker.ancientwarfare.item.InfoToolItem;
import io.github.capttracker.ancientwarfare.item.ManualItem;
import io.github.capttracker.ancientwarfare.registry.RegistryLoader;
import io.github.capttracker.ancientwarfare.util.parsing.JsonHelper;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Registrations of the core utilities slice: the info tool's mode component, the registry data reload
 * listener, {@code /awutils}, the first-craft manual hand-out and the soft-dependency hooks.
 *
 * <p>Not registered here, see the port notes: the config spec ({@code AWCoreStatics.register}, needs the mod
 * container), the payloads ({@code CoreMiscPayloads.registerPayloads}) and the item classes of the info tool and
 * manual (they replace plain {@code Item::new} in {@code AncientWarfareCore}).
 *
 * <p>The loot injection of the original ({@code AWCoreLoot}) is pure data now: global loot modifiers under
 * {@code data/ancientwarfare/loot_modifiers}, listed in {@code data/neoforge/loot_modifiers/global_loot_modifiers.json}.
 */
public final class AwCoreMisc {
    private AwCoreMisc() {}

    /** The info tool's mode (was the item tag {@code mode}). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<InfoToolItem.Mode>> INFO_TOOL_MODE =
            AncientWarfareCore.REG.dataComponent("info_tool_mode", InfoToolItem.Mode.CODEC, InfoToolItem.Mode.STREAM_CODEC);

    /** Forces this class (and so the registrations above) to initialise. Call from the mod constructor. */
    public static void load() {}

    /** Registers the mod-bus and game-bus listeners. Call from the mod constructor. */
    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(AwCoreMisc::onCommonSetup);

        CompatLoader.registerCompat(new FTBCompat());

        NeoForge.EVENT_BUS.addListener(AwCoreMisc::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(AwCoreMisc::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(ManualItem::handlePlayerFirstAWCraft);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(CompatLoader::init);
    }

    /** The server side registry data files ({@code data/<namespace>/registry}); parsers are registered by the slices that own them. */
    private static void onAddReloadListeners(AddReloadListenerEvent event) {
        JsonHelper.setServerRegistryAccess(event.getRegistryAccess());
        event.addListener(RegistryLoader.SERVER);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        new CommandUtils().register(event.getDispatcher());
    }
}
