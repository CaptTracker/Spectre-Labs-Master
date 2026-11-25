/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: structure/block/BlockTotemPart.Variant (the 16 variants, their bounding boxes and the
 * extra parts each one places around the main block).
 */
package io.github.capttracker.ancientwarfarestructure.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The 16 totem part variants. The variant is a block-state property of the single {@code totem_part} block (it was
 * kept in the tile entity in 1.12.2). The ids are the 1.12.2 item NBT values and are kept for reference.
 *
 * <p>A multi-block variant lists the extra blocks it needs with {@link #additionalParts}; the block places them when
 * the main block is placed, and the positions double as the placement check ({@link #additionalPositions}).
 */
public enum TotemVariant implements StringRepresentable {
    BASE(0, true) {
        @Override
        public List<Part> additionalParts(BlockPos pos, Direction facing) {
            return List.of(Part.invisible(pos.above(), BASE, Direction.NORTH));
        }
    },
    MID(1, true),
    MID_ALT(2, true),
    TOP(3, true) {
        @Override
        public List<Part> additionalParts(BlockPos pos, Direction facing) {
            return List.of(
                    Part.invisible(pos.above(), BASE, Direction.NORTH),
                    Part.side(pos.relative(facing.getClockWise()), WINGS, facing.getOpposite()),
                    Part.side(pos.relative(facing.getCounterClockWise()), WINGS, facing));
        }
    },
    WINGS(4, true),
    IRMINSUL_BASE(5, true),
    IRMINSUL_MID(6, true),
    IRMINSUL_TOP(7, true) {
        @Override
        public List<Part> additionalParts(BlockPos pos, Direction facing) {
            return List.of(
                    Part.side(pos.relative(facing.getClockWise()), IRMINSUL_SIDE, facing.getOpposite()),
                    Part.side(pos.relative(facing.getCounterClockWise()), IRMINSUL_SIDE, facing));
        }
    },
    IRMINSUL_SIDE(8, true),
    ELEPHANT_LEFT(9, false),
    ELEPHANT_RIGHT(10, false),
    ELEPHANT_BODY(11, true) {
        @Override
        public List<Part> additionalParts(BlockPos pos, Direction facing) {
            BlockPos right = pos.relative(facing.getClockWise());
            BlockPos left = pos.relative(facing.getCounterClockWise());
            return List.of(
                    Part.invisible(right.above(), ELEPHANT_RIGHT, Direction.NORTH),
                    Part.side(right, ELEPHANT_RIGHT, facing),
                    Part.invisible(left.above(), ELEPHANT_LEFT, Direction.NORTH),
                    Part.side(left, ELEPHANT_LEFT, facing),
                    Part.invisible(pos.above(), ELEPHANT_BODY, Direction.NORTH));
        }
    },
    XOLTEC_IDOL_BODY_LEFT(12, true) {
        @Override
        public List<Part> additionalParts(BlockPos pos, Direction facing) {
            BlockPos right = pos.relative(facing.getClockWise());
            return List.of(
                    Part.side(right, XOLTEC_IDOL_BODY_RIGHT, facing),
                    Part.side(right.above(), XOLTEC_IDOL_HEAD_RIGHT, facing),
                    Part.invisible(right.above().above(), XOLTEC_IDOL_HEAD_RIGHT, facing),
                    Part.side(pos.above(), XOLTEC_IDOL_HEAD_LEFT, facing),
                    Part.invisible(pos.above().above(), XOLTEC_IDOL_HEAD_LEFT, facing));
        }
    },
    XOLTEC_IDOL_BODY_RIGHT(13, false),
    XOLTEC_IDOL_HEAD_LEFT(14, false),
    XOLTEC_IDOL_HEAD_RIGHT(15, false);

    /** One extra block of a multi-block variant. A part with {@code visible=false} only occupies the space. */
    public record Part(BlockPos pos, TotemVariant variant, Direction facing, boolean visible) {
        static Part invisible(BlockPos pos, TotemVariant variant, Direction facing) {
            return new Part(pos, variant, facing, false);
        }

        static Part side(BlockPos pos, TotemVariant variant, Direction facing) {
            return new Part(pos, variant, facing, true);
        }
    }

    private static final TotemVariant[] BY_ID = new TotemVariant[16];

    static {
        for (TotemVariant variant : values()) {
            BY_ID[variant.id] = variant;
        }
    }

    private final int id;
    private final boolean inCreativeTab;

    TotemVariant(int id, boolean inCreativeTab) {
        this.id = id;
        this.inCreativeTab = inCreativeTab;
    }

    /** The 1.12.2 item NBT {@code variant} byte. */
    public int getId() {
        return id;
    }

    /** False for the five parts that only exist as side or filler blocks of another variant (1.12.2: includeInSubBlocks). */
    public boolean includeInCreativeTab() {
        return inCreativeTab;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public static TotemVariant fromId(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : null;
    }

    /** The extra blocks this variant places when its main block is placed at {@code pos} facing {@code facing}. */
    public List<Part> additionalParts(BlockPos pos, Direction facing) {
        return List.of();
    }

    /** The positions of {@link #additionalParts} (1.12.2: getAdditionalPartPositions). */
    public List<BlockPos> additionalPositions(BlockPos pos, Direction facing) {
        List<Part> parts = additionalParts(pos, facing);
        List<BlockPos> positions = new ArrayList<>(parts.size());
        for (Part part : parts) {
            positions.add(part.pos());
        }
        return positions;
    }

    /** Selection and collision shape for a part facing {@code facing}. */
    public VoxelShape getShape(Direction facing) {
        boolean x = facing.getAxis() == Direction.Axis.X;
        return switch (this) {
            case WINGS -> x ? Boxes.WING_X : Boxes.WING_Z;
            case IRMINSUL_BASE, IRMINSUL_MID -> x ? Boxes.IRMINSUL_X : Boxes.IRMINSUL_Z;
            case IRMINSUL_TOP -> x ? Boxes.IRMINSUL_TOP_X : Boxes.IRMINSUL_TOP_Z;
            case IRMINSUL_SIDE -> x ? Boxes.IRMINSUL_SIDE_X : Boxes.IRMINSUL_SIDE_Z;
            case ELEPHANT_LEFT, ELEPHANT_RIGHT, ELEPHANT_BODY -> Shapes.block();
            case XOLTEC_IDOL_BODY_LEFT, XOLTEC_IDOL_BODY_RIGHT, XOLTEC_IDOL_HEAD_LEFT, XOLTEC_IDOL_HEAD_RIGHT -> switch (facing) {
                case NORTH -> Boxes.XOLTEC_NORTH;
                case SOUTH -> Boxes.XOLTEC_SOUTH;
                case WEST -> Boxes.XOLTEC_WEST;
                default -> Boxes.XOLTEC_EAST;
            };
            default -> Boxes.DEFAULT;
        };
    }

    /** The 1.12.2 AxisAlignedBB constants, in sixteenths of a block. */
    private static final class Boxes {
        static final VoxelShape DEFAULT = Block.box(3, 0, 3, 13, 16, 13);
        static final VoxelShape WING_X = Block.box(6, 4, 0, 10, 15, 16);
        static final VoxelShape WING_Z = Block.box(0, 4, 6, 16, 15, 10);
        static final VoxelShape IRMINSUL_X = Block.box(4, 0, 3, 12, 16, 13);
        static final VoxelShape IRMINSUL_Z = Block.box(3, 0, 4, 13, 16, 12);
        static final VoxelShape IRMINSUL_TOP_X = Block.box(4, 0, 0, 12, 14, 16);
        static final VoxelShape IRMINSUL_TOP_Z = Block.box(0, 0, 4, 16, 14, 12);
        static final VoxelShape IRMINSUL_SIDE_X = Block.box(4, 3, 0, 12, 14, 16);
        static final VoxelShape IRMINSUL_SIDE_Z = Block.box(0, 3, 4, 16, 14, 12);
        static final VoxelShape XOLTEC_SOUTH = Block.box(0, 0, 8, 16, 16, 16);
        static final VoxelShape XOLTEC_NORTH = Block.box(0, 0, 0, 16, 16, 8);
        static final VoxelShape XOLTEC_WEST = Block.box(0, 0, 0, 8, 16, 16);
        static final VoxelShape XOLTEC_EAST = Block.box(8, 0, 0, 16, 16, 16);
    }
}
