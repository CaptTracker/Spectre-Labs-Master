/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2 (1.12.2-2.7.0.1038), Copyright (C) 2013-2021 Shadowmage, P3pp3rF1y and contributors.
 */
package io.github.capttracker.ancientwarfarenpc;

import io.github.capttracker.ancientwarfarenpc.init.AwNpcItemComponents;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcMenus;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcSettlements;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcs;
import io.github.capttracker.lib.registry.ModRegistrar;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Mod entry point. Content is registered by the classes in the init package through {@link #REG};
 * the init classes are loaded from the constructor below.
 */
@Mod(AncientWarfareNPC.MOD_ID)
public final class AncientWarfareNPC {
    public static final String MOD_ID = "ancientwarfarenpc";
    public static final ModRegistrar REG = ModRegistrar.create(MOD_ID);

    public AncientWarfareNPC(IEventBus modBus, ModContainer container) {
        AwNpcs.load();
        AwNpcs.registerEvents(modBus, container);   // config, entity types, serializers, payloads, attributes, capabilities, parsers, game events, commands
        AwNpcItemComponents.load();
        AwNpcItemComponents.registerEvents(modBus); // bridges to the item slice, creative tab spawner stacks, scythe
        AwNpcMenus.load();
        AwNpcMenus.registerEvents(modBus);          // menu types, menu payloads, GUI openers
        AwNpcSettlements.load();
        AwNpcSettlements.registerEvents(modBus, container); // settlements: marker, ledger, config, geometry payload
        REG.register(modBus);
    }
}
