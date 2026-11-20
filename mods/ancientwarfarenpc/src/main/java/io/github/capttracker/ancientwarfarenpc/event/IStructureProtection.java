/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the StructureMap, TownMap, TileProtectionFlag and ISpecialLootContainer calls of npc/event/EventHandler.
 */
package io.github.capttracker.ancientwarfarenpc.event;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Optional;

/**
 * What {@link NpcEventHandler} needs from the Structures module, which the NPC module cannot depend on. The Structures module implements this
 * and installs it with {@link NpcEventHandler#setStructureProtection}; until then the structure rules (no hostile spawns, locked loot chests,
 * slow digging) are off.
 */
public interface IStructureProtection {
    /** A generated structure and where its protection flag is. */
    record StructureInfo(BlockPos protectionFlagPos, AABB bounds) {}

    /** Whether hostile mobs must not enter the world at this position (inside a structure or town bounding box). */
    boolean shouldPreventSpawnAt(Level level, BlockPos pos);

    /** The structure that contains the position. */
    Optional<StructureInfo> getStructureAt(Level level, BlockPos pos);

    /** Whether the protection flag of the structure protects it against this player (the flag has not been claimed by them or their team). */
    boolean shouldProtectAgainst(Level level, StructureInfo structure, Player player);

    /** Whether the block is a generated loot container ({@code generatedInventory} flag on its block entity, or a special loot container). */
    boolean isLockedContainer(Level level, BlockPos pos);

    /** Whether the block is a mob spawner (spawner blocks can be dug normally inside protected structures). */
    boolean isSpawnerBlock(BlockState state);
}
