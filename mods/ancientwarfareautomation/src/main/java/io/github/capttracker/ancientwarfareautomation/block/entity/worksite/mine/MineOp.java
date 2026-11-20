/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 3, mine styles).
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.mine;

import io.github.capttracker.ancientwarfare.api.WorkAction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * One thing a mine pattern wants done at one position. {@code DIG} and {@code VEIN} are MINE tasks (VEIN: an ore that is followed), the others are PLACE tasks.
 *
 * @param facing the facing of the placed block: the way a ladder or wall torch points (away from its wall); null for the others
 */
public record MineOp(BlockPos pos, Kind kind, @Nullable Direction facing) {
    public enum Kind {
        /** Plugs lava or water with cobblestone (first priority). */
        SEAL(WorkAction.PLACE),
        /** An ore of a vein that is followed (strip mine). */
        VEIN(WorkAction.MINE),
        DIG(WorkAction.MINE),
        LADDER(WorkAction.PLACE),
        /** A standing torch on the floor. */
        TORCH_FLOOR(WorkAction.PLACE),
        /** A torch on a wall. */
        TORCH_WALL(WorkAction.PLACE);

        private final WorkAction action;

        Kind(WorkAction action) {
            this.action = action;
        }

        public WorkAction action() {
            return action;
        }
    }

    public MineOp {
        pos = pos.immutable();
    }

    public MineOp(BlockPos pos, Kind kind) {
        this(pos, kind, null);
    }

    public WorkAction action() {
        return kind.action();
    }

    /** The block a PLACE operation puts down; null for the mining kinds. */
    @Nullable
    public BlockState placeState() {
        return switch (kind) {
            case SEAL -> Blocks.COBBLESTONE.defaultBlockState();
            case LADDER -> Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing == null ? Direction.SOUTH : facing);
            case TORCH_FLOOR -> Blocks.TORCH.defaultBlockState();
            case TORCH_WALL -> Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, facing == null ? Direction.NORTH : facing);
            default -> null;
        };
    }
}
