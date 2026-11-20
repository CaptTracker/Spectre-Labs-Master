/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2 (1.12.2-2.7.0.1038), Copyright (C) 2013-2021 Shadowmage, P3pp3rF1y and contributors.
 */
package io.github.capttracker.ancientwarfarevehicle;

import io.github.capttracker.ancientwarfarevehicle.init.AwVehicleEntity;
import io.github.capttracker.ancientwarfarevehicle.init.AwVehicleMenus;
import io.github.capttracker.ancientwarfarevehicle.init.AwVehicles;
import io.github.capttracker.lib.registry.ModRegistrar;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Mod entry point. Content is registered by the classes in the init package through {@link #REG};
 * the init classes are loaded from the constructor below.
 */
@Mod(AncientWarfareVehicles.MOD_ID)
public final class AncientWarfareVehicles {
    public static final String MOD_ID = "ancientwarfarevehicle";
    public static final ModRegistrar REG = ModRegistrar.create(MOD_ID);

    public AncientWarfareVehicles(IEventBus modBus, ModContainer container) {
        AwVehicles.load();
        AwVehicles.registerEvents(modBus, container);   // entity types, payloads, common setup, config
        AwVehicleEntity.load();
        AwVehicleEntity.registerEvents(modBus);         // item handler capability of the vehicle
        AwVehicleMenus.load();
        AwVehicleMenus.registerEvents(modBus);          // menu types, menu payloads, inventory opener
        REG.register(modBus);
    }
}
