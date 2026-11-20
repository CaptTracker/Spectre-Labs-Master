/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/IKeepFood.
 */
package io.github.capttracker.ancientwarfarenpc.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Optional;

/** Marks the NPCs that need upkeep (food). Implemented by {@link NpcPlayerOwned} only. */
public interface IKeepFood {

    int getUpkeepAmount();

    Direction getUpkeepBlockSide();

    /** 1.12.2: {@code getUpkeepDimensionId()}, an int. */
    ResourceKey<Level> getUpkeepDimension();

    void setUpkeepAutoPosition(BlockPos pos);

    Optional<BlockPos> getUpkeepPoint();
}
