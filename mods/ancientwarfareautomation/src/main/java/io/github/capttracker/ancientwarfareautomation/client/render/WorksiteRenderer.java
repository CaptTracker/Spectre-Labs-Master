/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/render/WorksiteRenderer and core/util/RenderTools.drawOutlinedBoundingBox2.
 */
package io.github.capttracker.ancientwarfareautomation.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.BoundedWorksiteBlockEntity;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Draws the work area of the worksites (quarry, the farms, the warehouse control block) as a translucent white wire
 * frame of 12 bars, 1/16 block thick, while the client option {@code render_work_bounds} is on (F8 toggles it).
 * One renderer instance serves every bounded worksite block entity type.
 */
public class WorksiteRenderer implements BlockEntityRenderer<BoundedWorksiteBlockEntity> {
    private static final float BAR_WIDTH = 0.0625F;
    private static final float ALPHA = 0.4F;

    public WorksiteRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BoundedWorksiteBlockEntity worksite, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (!AWAutomationStatics.renderWorkBounds || !worksite.hasWorkBounds()) {
            return;
        }
        BlockPos pos = worksite.getBlockPos();
        BlockPos min = worksite.getWorkBoundsMin();
        BlockPos max = worksite.getWorkBoundsMax();
        AABB bb = new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1)
                .move(-pos.getX(), -pos.getY(), -pos.getZ());
        drawOutlinedBox(poseStack.last().pose(), buffers.getBuffer(RenderType.debugQuads()), bb, 1.0F, 1.0F, 1.0F, ALPHA, BAR_WIDTH);
    }

    /** The box stays visible when the worksite block itself is out of view, as the widened 1.12.2 render bounds did. */
    @Override
    public boolean shouldRenderOffScreen(BoundedWorksiteBlockEntity worksite) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(BoundedWorksiteBlockEntity worksite) {
        return worksite.getRenderBounds();
    }

    private static void drawOutlinedBox(Matrix4f pose, VertexConsumer consumer, AABB bb, float r, float g, float b, float a, float width) {
        float hw = width / 2;
        float minX = (float) bb.minX;
        float minY = (float) bb.minY;
        float minZ = (float) bb.minZ;
        float maxX = (float) bb.maxX;
        float maxY = (float) bb.maxY;
        float maxZ = (float) bb.maxZ;
        // four bars along X
        cuboid(pose, consumer, minX, minY - hw, minZ - hw, maxX, minY + hw, minZ + hw, r, g, b, a);
        cuboid(pose, consumer, minX, maxY - hw, minZ - hw, maxX, maxY + hw, minZ + hw, r, g, b, a);
        cuboid(pose, consumer, minX, minY - hw, maxZ - hw, maxX, minY + hw, maxZ + hw, r, g, b, a);
        cuboid(pose, consumer, minX, maxY - hw, maxZ - hw, maxX, maxY + hw, maxZ + hw, r, g, b, a);
        // four bars along Y
        cuboid(pose, consumer, minX - hw, minY, minZ - hw, minX + hw, maxY, minZ + hw, r, g, b, a);
        cuboid(pose, consumer, maxX - hw, minY, minZ - hw, maxX + hw, maxY, minZ + hw, r, g, b, a);
        cuboid(pose, consumer, minX - hw, minY, maxZ - hw, minX + hw, maxY, maxZ + hw, r, g, b, a);
        cuboid(pose, consumer, maxX - hw, minY, maxZ - hw, maxX + hw, maxY, maxZ + hw, r, g, b, a);
        // four bars along Z
        cuboid(pose, consumer, minX - hw, minY - hw, minZ, minX + hw, minY + hw, maxZ, r, g, b, a);
        cuboid(pose, consumer, minX - hw, maxY - hw, minZ, minX + hw, maxY + hw, maxZ, r, g, b, a);
        cuboid(pose, consumer, maxX - hw, minY - hw, minZ, maxX + hw, minY + hw, maxZ, r, g, b, a);
        cuboid(pose, consumer, maxX - hw, maxY - hw, minZ, maxX + hw, maxY + hw, maxZ, r, g, b, a);
    }

    /** One box as six quads (the debug quad render type does not cull, so the winding does not matter). */
    private static void cuboid(Matrix4f pose, VertexConsumer c, float x, float y, float z, float mx, float my, float mz, float r, float g, float b, float a) {
        // z+ side
        quad(pose, c, x, my, mz, x, y, mz, mx, y, mz, mx, my, mz, r, g, b, a);
        // x+ side
        quad(pose, c, mx, my, mz, mx, y, mz, mx, y, z, mx, my, z, r, g, b, a);
        // y+ side
        quad(pose, c, x, my, z, x, my, mz, mx, my, mz, mx, my, z, r, g, b, a);
        // z- side
        quad(pose, c, x, my, z, mx, my, z, mx, y, z, x, y, z, r, g, b, a);
        // x- side
        quad(pose, c, x, y, mz, x, my, mz, x, my, z, x, y, z, r, g, b, a);
        // y- side
        quad(pose, c, x, y, z, mx, y, z, mx, y, mz, x, y, mz, r, g, b, a);
    }

    private static void quad(Matrix4f pose, VertexConsumer c,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float r, float g, float b, float a) {
        c.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
        c.addVertex(pose, x2, y2, z2).setColor(r, g, b, a);
        c.addVertex(pose, x3, y3, z3).setColor(r, g, b, a);
        c.addVertex(pose, x4, y4, z4).setColor(r, g, b, a);
    }
}
