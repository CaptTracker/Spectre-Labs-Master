/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the capability methods of automation/tile/warehouse2/TileWarehouseInterface and the
 * registration of automation/tile/warehouse2/WarehouseDebugger in AncientWarfareAutomation.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.warehouse;

import io.github.capttracker.ancientwarfareautomation.command.WarehouseCommand;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The listeners of the warehouse slice. The block, item and block entity types are registered in
 * {@code AwAutomationBase}; this class adds what hangs on them:
 * <ul>
 *   <li>the item handler of the warehouse interface block, on every side (the control and storage blocks and the other
 *   warehouse blocks offer none, as in the original);</li>
 *   <li>the warehouse debugger: its level tick listener and the {@code /awwarehouse} hookup.</li>
 * </ul>
 * The worksite capabilities (torque, Forge Energy) of the control block are registered by {@code AwAutomationBase}.
 *
 * <p>Wiring: {@code WarehouseSetup.registerEvents(modBus)} in {@code AwAutomationBase.registerEvents} (or the mod
 * constructor), before the mod event bus finishes loading.
 */
public final class WarehouseSetup {
    private WarehouseSetup() {}

    /** Forces the class to initialise (nothing is registered statically here). */
    public static void load() {}

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(WarehouseSetup::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(WarehouseDebugger::onLevelTick);
        NeoForge.EVENT_BUS.addListener(WarehouseDebugger::onServerStopped);
        WarehouseCommand.setDebugger(WarehouseDebugger.COMMAND);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // 1.12.2: ITEM_HANDLER_CAPABILITY for every facing, always the same nine slots.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, AwAutomationBase.WAREHOUSE_INTERFACE_BE.get(),
                (warehouseInterface, side) -> warehouseInterface.getInventory());
    }
}
