/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2 (1.12.2-2.7.0.1038), Copyright (C) 2013-2021 Shadowmage, P3pp3rF1y and contributors.
 */
package io.github.capttracker.ancientwarfarestructure;

import io.github.capttracker.ancientwarfarestructure.decor.FirePitVariant;
import io.github.capttracker.ancientwarfarestructure.init.AwDecor;
import io.github.capttracker.ancientwarfarestructure.init.AwGateMenus;
import io.github.capttracker.ancientwarfarestructure.init.AwGates;
import io.github.capttracker.lib.registry.ModRegistrar;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Mod entry point. Content is registered by the classes in the init package through {@link #REG};
 * the calls that load them are added here when the module is wired up (see port-notes/).
 */
@Mod(AncientWarfareStructure.MOD_ID)
public final class AncientWarfareStructure {
    public static final String MOD_ID = "ancientwarfarestructure";
    public static final ModRegistrar REG = ModRegistrar.create(MOD_ID);

    public AncientWarfareStructure(IEventBus modBus, ModContainer container) {
        AwGates.load();
        AwGates.registerEvents(modBus, container);   // entity type, common setup (vehicle link), gate config
        AwGateMenus.load();                          // gate control menu type and its opener
        AwDecor.load();
        AwDecor.registerEvents(modBus);              // creative tab contents of the decor items
        // The one creative tab (id ancientwarfarestructure:main); AwDecor fills it through BuildCreativeModeTabContentsEvent.
        REG.creativeTab(() -> new ItemStack(AwDecor.firePitItem(FirePitVariant.DEFAULT).get()));
        REG.register(modBus);
    }
}
