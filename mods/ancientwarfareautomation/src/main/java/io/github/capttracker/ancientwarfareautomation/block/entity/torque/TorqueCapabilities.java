/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: replaces getCapability/hasCapability of the 1.12.2 tiles and the cofh IEnergyReceiver interface.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.torque;

import io.github.capttracker.ancientwarfare.api.AwCapabilities;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Helpers for the mod-bus {@link RegisterCapabilitiesEvent} listener of the base slice. Call {@link #register} for the
 * block entity type of every torque block entity (shafts, conduits, distributors, flywheel controllers, the four
 * generators) and {@link #registerStirling} for the stirling generator.
 *
 * <p>The flywheel storage and the windmill blade are not torque tiles and need no capability.
 */
public final class TorqueCapabilities {
    private TorqueCapabilities() {
    }

    /** The torque capability (the block entity itself, as the original found tiles by casting) and the energy (FE) capability of every side. */
    public static <T extends TileTorqueBase> void register(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(AwCapabilities.TORQUE, type, (be, side) -> be);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type, (be, side) -> be.getEnergyStorage(side));
    }

    /** The fuel slot of the stirling generator as an item handler on every side (what {@code hasCapability} answered in 1.12.2). */
    public static void registerStirling(RegisterCapabilitiesEvent event, BlockEntityType<TileStirlingGenerator> type) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, (be, side) -> be.getFuelHandler());
    }
}
