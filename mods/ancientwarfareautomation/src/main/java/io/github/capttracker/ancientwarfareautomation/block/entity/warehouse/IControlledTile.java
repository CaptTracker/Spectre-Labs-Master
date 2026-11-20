/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/warehouse2/IControlledTile.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.warehouse;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.Optional;

/** A warehouse block that attaches to a control block: storage, interface, stock viewer and stock linker. */
public interface IControlledTile {
    void setController(@Nullable WarehouseBlockEntity controller);

    Optional<WarehouseBlockEntity> getController();

    BlockPos getPosition();

    boolean isValidController(IControllerTile tile);
}
