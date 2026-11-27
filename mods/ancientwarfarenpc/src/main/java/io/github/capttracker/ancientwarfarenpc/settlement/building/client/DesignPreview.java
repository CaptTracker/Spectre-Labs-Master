/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): building designs. Client only.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.capttracker.ancientwarfarenpc.settlement.building.BuildingDesign;
import io.github.capttracker.ancientwarfarenpc.settlement.building.DesignMeta;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The ghost of a design at a position and rotation, for the planner screen. Call {@link #show} (for example every tick while a plot is being
 * picked) and {@link #clear} when done; it is drawn by the level render hook of {@link ScannerRenderer}.
 *
 * <p>It always draws the bounding box and a green bar on the front. When the full design is known (the client asks the server for it, once) it
 * also draws a faint outline of every placed cell, up to {@link #MAX_GHOST_BLOCKS} blocks. Real block models are not drawn (outlines only).
 */
public final class DesignPreview {
    /** The most block outlines drawn; bigger designs only get the box. */
    public static final int MAX_GHOST_BLOCKS = 3000;

    @Nullable
    private static Ghost active;

    private DesignPreview() {}

    private record Ghost(ResourceLocation id, int[] size, BlockPos origin, Direction facing, float r, float g, float b,
                         @Nullable List<BuildingDesign.Placement> blocks) {}

    /** Shows the design with this id (it must be in the synced list) with its lowest north-west corner at {@code origin}. {@code color} is 0xRRGGBB. */
    public static void show(ResourceLocation id, BlockPos origin, Direction facing, int color) {
        DesignMeta meta = DesignClientHooks.meta(id);
        if (meta == null) {
            active = null;
            return;
        }
        Ghost old = active;
        List<BuildingDesign.Placement> blocks = null;
        if (old != null && old.id.equals(id) && old.origin.equals(origin) && old.facing == facing) {
            blocks = old.blocks;
        }
        if (blocks == null) {
            BuildingDesign full = DesignClientHooks.full(id);
            if (full != null) {
                blocks = full.blocks(origin, facing);
            }
        }
        active = new Ghost(id, BuildingDesign.rotatedSize(meta.sizeX(), meta.sizeY(), meta.sizeZ(), meta.front(), facing), origin, facing,
                ((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F, blocks);
    }

    public static void clear() {
        active = null;
    }

    /** Draws a box of a placed design ({@code size} already rotated) with a bar on its front. For callers that draw without an id. */
    public static void renderOutline(PoseStack poseStack, MultiBufferSource buffers, Vec3 cam, BlockPos origin, int[] size, Direction front,
                                     float r, float g, float b) {
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        BlockPos max = origin.offset(size[0] - 1, size[1] - 1, size[2] - 1);
        ScannerRenderer.box(poseStack, lines, cam, origin, max, r, g, b, 1.0F, 0.0);
        ScannerRenderer.drawFrontArrow(poseStack, lines, cam, origin, max, front);
    }

    static void renderActive(PoseStack poseStack, MultiBufferSource buffers, Vec3 cam) {
        Ghost ghost = active;
        if (ghost == null) {
            return;
        }
        renderOutline(poseStack, buffers, cam, ghost.origin, ghost.size, ghost.facing, ghost.r, ghost.g, ghost.b);
        if (ghost.blocks != null && ghost.blocks.size() <= MAX_GHOST_BLOCKS) {
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            for (BuildingDesign.Placement placement : ghost.blocks) {
                if (!placement.state().isAir()) {
                    ScannerRenderer.box(poseStack, lines, cam, placement.pos(), placement.pos(), ghost.r, ghost.g, ghost.b, 0.35F, -0.08);
                }
            }
        }
    }
}
