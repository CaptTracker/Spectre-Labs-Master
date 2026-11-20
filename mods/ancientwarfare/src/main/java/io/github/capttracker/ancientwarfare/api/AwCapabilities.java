/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: block capabilities for the contracts of core/interfaces (the original found them by casting tile entities).
 */
package io.github.capttracker.ancientwarfare.api;

import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;

/**
 * Block capabilities of the Ancient Warfare API. Provide them from a mod-bus {@code RegisterCapabilitiesEvent}
 * with {@code event.registerBlockEntity(AwCapabilities.TORQUE, type, (be, side) -> be)}; query them with
 * {@code level.getCapability(AwCapabilities.TORQUE, pos, side)}.
 *
 * <p>Only the torque contract is a capability for now. {@link IWorkSite} stays an interface that block entities
 * implement (NPC workers and the hammer test with {@code instanceof}); {@link IInteractableTile} and
 * {@link IBlockBreakHandler} are called by the block base class, so they need no capability.
 */
public final class AwCapabilities {
    private AwCapabilities() {}

    /** A torque tile on the given side (null = the block as a whole). */
    public static final BlockCapability<ITorque.ITorqueTile, Direction> TORQUE =
            BlockCapability.createSided(Ids.of(AncientWarfareCore.MOD_ID, "torque"), ITorque.ITorqueTile.class);
}
