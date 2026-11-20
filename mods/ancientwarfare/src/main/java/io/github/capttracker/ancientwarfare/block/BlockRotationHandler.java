/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/block/BlockRotationHandler.
 */
package io.github.capttracker.ancientwarfare.block;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Facing for placement and the "relative side" math (front, rear, left, right, top, bottom) that machines with
 * configurable side inventories use. Directions are 1.21 {@link Direction}s; the lookup tables are indexed by
 * {@link Direction#get3DDataValue()}, which has the same order as the 1.12.2 {@code EnumFacing} ordinal
 * (down, up, north, south, west, east).
 */
public class BlockRotationHandler {
    /**
     * The facing a block gets when an entity places it. Horizontal rotation types use the placer's horizontal
     * facing; six-way blocks placed against the top or bottom of a block take the opposite of that face. When
     * {@link IRotatableBlock#invertFacing()} is set the result is reversed (the block's front faces the placer).
     */
    public static Direction getFaceForPlacement(LivingEntity entity, IRotatableBlock block, Direction sideHit) {
        return getFaceForPlacement(entity.getDirection(), block, sideHit);
    }

    /** Same, from a placement context (the horizontal direction is the placer's, or north with no placer). */
    public static Direction getFaceForPlacement(BlockPlaceContext context, IRotatableBlock block) {
        return getFaceForPlacement(context.getHorizontalDirection(), block, context.getClickedFace());
    }

    private static Direction getFaceForPlacement(Direction horizontalFacing, IRotatableBlock block, Direction sideHit) {
        if (block.getRotationType() == RotationType.NONE) {
            return Direction.NORTH;
        }
        Direction facing = horizontalFacing;
        if (block.getRotationType() == RotationType.SIX_WAY && sideHit.getAxis() == Direction.Axis.Y) {
            facing = sideHit.getOpposite();
        }
        if (block.invertFacing()) {
            facing = facing.getOpposite();
        }
        return facing;
    }

    public interface IRotatableBlock {
        RotationType getRotationType();

        boolean invertFacing();
    }

    /**
     * A block entity that knows which way its block faces. In 1.21 the facing lives in the block state; implementers
     * read and write that. Kept so code that only has the block entity can still ask.
     */
    public interface IRotatableTile {
        Direction getPrimaryFacing();

        void setPrimaryFacing(Direction face);
    }

    public enum RotationType {
        /**
         * Can have 6 textures / inventories.
         * Top, Bottom, Front, Rear, Left, Right.
         * Can only face in one of four-directions - N/S/E/W.
         */
        FOUR_WAY(EnumSet.of(RelativeSide.TOP, RelativeSide.BOTTOM, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.FRONT, RelativeSide.REAR)),
        /**
         * Can have 3 textures / inventories.
         * Top, Bottom, Sides.
         * Can face in any orientation - U/D/N/S/E/W.
         */
        SIX_WAY(EnumSet.of(RelativeSide.TOP, RelativeSide.BOTTOM, RelativeSide.ANY_SIDE)),
        /** No rotation, can still have relative sides, but FRONT always == NORTH. */
        NONE(EnumSet.of(RelativeSide.TOP, RelativeSide.BOTTOM, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.FRONT, RelativeSide.REAR));

        RotationType(EnumSet<RelativeSide> sides) {
            validSides = sides;
        }

        EnumSet<RelativeSide> validSides;

        public Set<RelativeSide> getValidSides() {
            return validSides;
        }
    }

    public enum RelativeSide {
        TOP("guistrings.inventory.side.top"), BOTTOM("guistrings.inventory.side.bottom"), FRONT("guistrings.inventory.side.front"),
        REAR("guistrings.inventory.side.rear"), LEFT("guistrings.inventory.side.left"), RIGHT("guistrings.inventory.side.right"),
        ANY_SIDE("guistrings.inventory.side.all_sides"), NONE("guistrings.inventory.side.none");

        private static final int DOWN = Direction.DOWN.get3DDataValue();
        private static final int UP = Direction.UP.get3DDataValue();
        private static final int NORTH = Direction.NORTH.get3DDataValue();
        private static final int SOUTH = Direction.SOUTH.get3DDataValue();
        private static final int WEST = Direction.WEST.get3DDataValue();
        private static final int EAST = Direction.EAST.get3DDataValue();
        //[side-viewed][block-facing]=relative side viewed
        protected static final RelativeSide[][] sixWayMap = new RelativeSide[6][6];
        //[side-viewed][block-facing]=relative side viewed
        protected static final RelativeSide[][] fourWayMap = new RelativeSide[6][6];

        static {
            //D,U,N,S,W,E
            //[side-viewed][block-facing]=relative side viewed
            //fourWayMap[X][0-1] SHOULD BE NEVER REFERENCED AS BLOCK CAN NEVER POINT U/D
            sixWayMap[DOWN][DOWN] = TOP;
            sixWayMap[DOWN][UP] = BOTTOM;
            sixWayMap[DOWN][NORTH] = ANY_SIDE;
            sixWayMap[DOWN][SOUTH] = ANY_SIDE;
            sixWayMap[DOWN][WEST] = ANY_SIDE;
            sixWayMap[DOWN][EAST] = ANY_SIDE;

            sixWayMap[UP][DOWN] = BOTTOM;
            sixWayMap[UP][UP] = TOP;
            sixWayMap[UP][NORTH] = ANY_SIDE;
            sixWayMap[UP][SOUTH] = ANY_SIDE;
            sixWayMap[UP][WEST] = ANY_SIDE;
            sixWayMap[UP][EAST] = ANY_SIDE;

            sixWayMap[NORTH][DOWN] = ANY_SIDE;
            sixWayMap[NORTH][UP] = ANY_SIDE;
            sixWayMap[NORTH][NORTH] = TOP;
            sixWayMap[NORTH][SOUTH] = BOTTOM;
            sixWayMap[NORTH][WEST] = ANY_SIDE;
            sixWayMap[NORTH][EAST] = ANY_SIDE;

            sixWayMap[SOUTH][DOWN] = ANY_SIDE;
            sixWayMap[SOUTH][UP] = ANY_SIDE;
            sixWayMap[SOUTH][NORTH] = BOTTOM;
            sixWayMap[SOUTH][SOUTH] = TOP;
            sixWayMap[SOUTH][WEST] = ANY_SIDE;
            sixWayMap[SOUTH][EAST] = ANY_SIDE;

            sixWayMap[WEST][DOWN] = ANY_SIDE;
            sixWayMap[WEST][UP] = ANY_SIDE;
            sixWayMap[WEST][NORTH] = ANY_SIDE;
            sixWayMap[WEST][SOUTH] = ANY_SIDE;
            sixWayMap[WEST][WEST] = TOP;
            sixWayMap[WEST][EAST] = BOTTOM;

            sixWayMap[EAST][DOWN] = ANY_SIDE;
            sixWayMap[EAST][UP] = ANY_SIDE;
            sixWayMap[EAST][NORTH] = ANY_SIDE;
            sixWayMap[EAST][SOUTH] = ANY_SIDE;
            sixWayMap[EAST][WEST] = BOTTOM;
            sixWayMap[EAST][EAST] = TOP;

            fourWayMap[DOWN][DOWN] = ANY_SIDE;
            fourWayMap[DOWN][UP] = ANY_SIDE;
            fourWayMap[DOWN][NORTH] = BOTTOM;
            fourWayMap[DOWN][SOUTH] = BOTTOM;
            fourWayMap[DOWN][WEST] = BOTTOM;
            fourWayMap[DOWN][EAST] = BOTTOM;

            fourWayMap[UP][DOWN] = ANY_SIDE;
            fourWayMap[UP][UP] = ANY_SIDE;
            fourWayMap[UP][NORTH] = TOP;
            fourWayMap[UP][SOUTH] = TOP;
            fourWayMap[UP][WEST] = TOP;
            fourWayMap[UP][EAST] = TOP;

            fourWayMap[NORTH][DOWN] = ANY_SIDE;
            fourWayMap[NORTH][UP] = ANY_SIDE;
            fourWayMap[NORTH][NORTH] = FRONT;
            fourWayMap[NORTH][SOUTH] = REAR;
            fourWayMap[NORTH][WEST] = RIGHT;
            fourWayMap[NORTH][EAST] = LEFT;

            fourWayMap[SOUTH][DOWN] = ANY_SIDE;
            fourWayMap[SOUTH][UP] = ANY_SIDE;
            fourWayMap[SOUTH][NORTH] = REAR;
            fourWayMap[SOUTH][SOUTH] = FRONT;
            fourWayMap[SOUTH][WEST] = LEFT;
            fourWayMap[SOUTH][EAST] = RIGHT;

            fourWayMap[WEST][DOWN] = ANY_SIDE;
            fourWayMap[WEST][UP] = ANY_SIDE;
            fourWayMap[WEST][NORTH] = LEFT;
            fourWayMap[WEST][SOUTH] = RIGHT;
            fourWayMap[WEST][WEST] = FRONT;
            fourWayMap[WEST][EAST] = REAR;

            fourWayMap[EAST][DOWN] = ANY_SIDE;
            fourWayMap[EAST][UP] = ANY_SIDE;
            fourWayMap[EAST][NORTH] = RIGHT;
            fourWayMap[EAST][SOUTH] = LEFT;
            fourWayMap[EAST][WEST] = REAR;
            fourWayMap[EAST][EAST] = FRONT;
        }

        private final String key;

        RelativeSide(String key) {
            this.key = key;
        }

        public String getTranslationKey() {
            return key;
        }

        public Component getDisplayName() {
            return Component.translatable(key);
        }

        /** Which relative side of a block facing {@code facing} the world side {@code side} is. */
        public static RelativeSide getSideViewed(RotationType t, Direction facing, @Nullable Direction side) {
            if (side != null) {
                if (t == RotationType.FOUR_WAY) {
                    return fourWayMap[side.get3DDataValue()][facing.get3DDataValue()];
                } else if (t == RotationType.SIX_WAY) {
                    return sixWayMap[side.get3DDataValue()][facing.get3DDataValue()];
                }
            }
            return ANY_SIDE;
        }

        /** The world side to access to reach the given relative side of a block facing {@code facing}, or null if there is none. */
        @Nullable
        public static Direction getMCSideToAccess(RotationType t, Direction facing, RelativeSide access) {
            RelativeSide[][] map = t == RotationType.FOUR_WAY ? fourWayMap : sixWayMap;
            for (int x = 0; x < map.length; x++) {
                if (map[x][facing.get3DDataValue()] == access) {
                    return Direction.from3DDataValue(x);
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ENGLISH);
        }
    }
}
