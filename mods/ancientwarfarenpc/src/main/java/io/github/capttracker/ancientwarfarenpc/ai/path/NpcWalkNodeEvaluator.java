/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/NpcWalkNodeProcessor.
 */
package io.github.capttracker.ancientwarfarenpc.ai.path;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * The walk node evaluator of the NPCs.
 *
 * <p>1.12.2 copied the neighbour search of {@code WalkNodeProcessor} (its {@code getSafePoint} was private) and changed two things; in 1.21.1 the
 * vanilla evaluator already does the first, so only the second is left here:
 * <ul>
 *   <li>Wooden doors can be opened and passed ({@code setCanOpenDoors}, {@code setCanPassDoors}: both on in the constructor, the 1.21 vanilla ground
 *       navigation only sets the second).</li>
 *   <li>Fence gates are doors for the path finder: an open gate is {@link PathType#DOOR_OPEN}, a closed one {@link PathType#DOOR_WOOD_CLOSED} (which
 *       becomes the walkable door type while doors can be opened). Vanilla treats a closed gate as a fence, which blocks the path. {@code NpcAIDoor}
 *       opens the gate when the NPC arrives.</li>
 * </ul>
 */
public class NpcWalkNodeEvaluator extends WalkNodeEvaluator {
    public NpcWalkNodeEvaluator() {
        setCanOpenDoors(true);
        setCanPassDoors(true);
    }

    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        BlockState state = context.getBlockState(new BlockPos.MutableBlockPos(x, y, z));
        if (state.getBlock() instanceof FenceGateBlock) {
            return state.getValue(FenceGateBlock.OPEN) ? PathType.DOOR_OPEN : PathType.DOOR_WOOD_CLOSED;
        }
        return super.getPathType(context, x, y, z);
    }
}
