/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/warehouse2/IControllerTile.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.warehouse;

import net.minecraft.core.BlockPos;

/**
 * The side of a warehouse that owns the other blocks: the control block. {@link WarehouseBlockEntity} is the only
 * implementer; the interface keeps {@link IControlledTile#isValidController} independent of the class.
 */
public interface IControllerTile {

    void addControlledTile(IControlledTile tile);

    void removeControlledTile(IControlledTile tile);

    /** The position of the control block (1.12.2: {@code getPosisition}, a typo). */
    BlockPos getControllerPosition();

    BlockPos getWorkBoundsMin();

    BlockPos getWorkBoundsMax();
}
