/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2 (1.12.2-2.7.0.1038), Copyright (C) 2013-2021 Shadowmage, P3pp3rF1y and contributors.
 */
package io.github.capttracker.ancientwarfareautomation;

import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationEntities;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import io.github.capttracker.lib.registry.ModRegistrar;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Mod entry point. Content is registered by the classes in the init package through {@link #REG}.
 */
@Mod(AncientWarfareAutomation.MOD_ID)
public final class AncientWarfareAutomation {
    public static final String MOD_ID = "ancientwarfareautomation";
    public static final ModRegistrar REG = ModRegistrar.create(MOD_ID);

    public AncientWarfareAutomation(IEventBus modBus, ModContainer container) {
        AwAutomationBase.load();
        AwAutomationBase.registerEvents(modBus);
        AwAutomationEntities.registerEvents(modBus);
        AwAutomationMenus.load();
        AwAutomationMenus.registerEvents(modBus);
        AWAutomationStatics.register(modBus, container);
        REG.register(modBus);
    }
}
