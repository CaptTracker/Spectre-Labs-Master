/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/render/AnimatedBlockRenderer, BaseTorqueRenderer and core/render/RotatableBlockRenderer
 * (rotateFacing, the CodeChickenLib Rotation(...).at(pivot) transformations and the FastTESR drawing).
 */
package io.github.capttracker.ancientwarfareautomation.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;

import javax.annotation.Nullable;

/**
 * Drawing helpers shared by the block entity renderers of the machines: draw a baked part model through a
 * {@link PoseStack} and the transformations the original bakeries applied to OBJ groups.
 *
 * <p>The parts are drawn with the entity render types of the block atlas without face culling: CodeChickenLib rendered
 * a back-faced copy of every group, so both sides of a face were visible, and the entity shader lights each quad by
 * its (rotated) normal, as {@code LightModel.standardLightModel} did.
 */
public final class PartRenderer {
    private static final float[] FULL_SHADE = {1.0F, 1.0F, 1.0F, 1.0F};
    /** The six sides, then null: the quad lists a baked model is made of. */
    private static final Direction[] SIDES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null};

    private PartRenderer() {}

    /** Opaque (cut out) parts of the block atlas, both sides visible. */
    public static RenderType solidType() {
        return RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS);
    }

    /** Translucent parts of the block atlas (the flywheel windows). */
    public static RenderType translucentType() {
        return RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
    }

    /** Draws a part with the current pose. */
    public static void draw(PoseStack poseStack, MultiBufferSource buffers, @Nullable BakedModel model, int light, int overlay) {
        if (model != null) {
            drawQuads(poseStack, buffers.getBuffer(solidType()), model, 1.0F, light, overlay);
        }
    }

    /** Draws a part with the current pose and the given opacity (the original windows had alpha 0.25). */
    public static void drawTranslucent(PoseStack poseStack, MultiBufferSource buffers, @Nullable BakedModel model, float alpha, int light, int overlay) {
        if (model != null) {
            drawQuads(poseStack, buffers.getBuffer(translucentType()), model, alpha, light, overlay);
        }
    }

    private static void drawQuads(PoseStack poseStack, VertexConsumer consumer, BakedModel model, float alpha, int light, int overlay) {
        PoseStack.Pose pose = poseStack.last();
        RandomSource random = RandomSource.create();
        int[] lights = {light, light, light, light};
        for (Direction side : SIDES) {
            random.setSeed(42L);
            for (BakedQuad quad : model.getQuads(null, side, random, ModelData.EMPTY, null)) {
                consumer.putBulkData(pose, quad, FULL_SHADE, 1.0F, 1.0F, 1.0F, alpha, lights, overlay, false);
            }
        }
    }

    // ---- transformations ----

    /**
     * Turns the pose around the block centre so that a model whose front is north faces {@code facing}
     * ({@code RotatableBlockRenderer.rotateFacing}: north 0, west +90, south 180, east -90 degrees around Y;
     * up +90 and down -90 degrees around X).
     */
    public static void faceFront(PoseStack poseStack, Direction facing) {
        poseStack.translate(0.5D, 0.5D, 0.5D);
        switch (facing) {
            case UP -> poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            case DOWN -> poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            case SOUTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            case WEST -> poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            case EAST -> poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
            default -> {
                // north: the model is already in place
            }
        }
        poseStack.translate(-0.5D, -0.5D, -0.5D);
    }

    /** Rotates the pose by an angle in radians around the axis through the pivot (CodeChickenLib {@code Rotation(angle, axis).at(pivot)}). */
    public static void rotateAbout(PoseStack poseStack, Axis axis, float radians, double pivotX, double pivotY, double pivotZ) {
        poseStack.translate(pivotX, pivotY, pivotZ);
        poseStack.mulPose(axis.rotation(radians));
        poseStack.translate(-pivotX, -pivotY, -pivotZ);
    }

    /** Rotates the pose around the centre of the block. */
    public static void rotateAboutCenter(PoseStack poseStack, Axis axis, float radians) {
        rotateAbout(poseStack, axis, radians, 0.5D, 0.5D, 0.5D);
    }

    /** The JOML axis of the world axis of a side. */
    public static Axis axisOf(Direction side) {
        return switch (side.getAxis()) {
            case X -> Axis.XP;
            case Y -> Axis.YP;
            case Z -> Axis.ZP;
        };
    }
}
