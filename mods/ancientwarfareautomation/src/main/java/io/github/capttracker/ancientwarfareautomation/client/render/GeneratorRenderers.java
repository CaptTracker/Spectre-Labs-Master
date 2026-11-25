/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/render/HandCrankedGeneratorRenderer, StirlingGeneratorRenderer,
 * WaterwheelGeneratorRenderer and WindmillGeneratorRenderer (and the animation renderer that added VALID_SETUP in
 * automation/proxy/ClientProxyAutomation).
 */
package io.github.capttracker.ancientwarfareautomation.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileHandCrankedGenerator;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileStirlingGenerator;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileWaterwheelGenerator;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileWindmillController;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * The four generators that have moving parts. Their housing is the static block model (turned by the block state
 * facing); the parts here turn around the pivots that the original renderers hard coded, in block fractions, in the
 * model space of a block that faces north.
 */
public final class GeneratorRenderers {
    private GeneratorRenderers() {}

    /** Hand crank: the output gear on the facing axis, the input gear (the crank) around Y. */
    public static class HandCranked implements BlockEntityRenderer<TileHandCrankedGenerator> {
        public HandCranked(BlockEntityRendererProvider.Context context) {
        }

        @Override
        public void render(TileHandCrankedGenerator generator, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
            if (generator.getLevel() == null) {
                return;
            }
            Direction facing = generator.getPrimaryFacing();
            float outputRotation = generator.getClientOutputRotation(facing, partialTick);
            float inputRotation = generator.getCrankRotation(partialTick);

            poseStack.pushPose();
            PartRenderer.faceFront(poseStack, facing);

            poseStack.pushPose();
            PartRenderer.rotateAboutCenter(poseStack, Axis.ZP, outputRotation);
            PartRenderer.draw(poseStack, buffers, AutomationModels.HAND_CRANKED_GENERATOR.get("output_gear"), light, overlay);
            poseStack.popPose();

            poseStack.pushPose();
            PartRenderer.rotateAbout(poseStack, Axis.YP, inputRotation, 8.0D / 16.0D, 10.5D / 16.0D, 11.5D / 16.0D);
            PartRenderer.draw(poseStack, buffers, AutomationModels.HAND_CRANKED_GENERATOR.get("input_gear"), light, overlay);
            poseStack.popPose();

            poseStack.popPose();
        }

        @Override
        public AABB getRenderBoundingBox(TileHandCrankedGenerator generator) {
            return generator.getRenderBounds();
        }
    }

    /**
     * Stirling generator: flywheel, two piston cranks and the three arms, with the slider-crank trigonometry of the
     * original. The two arm angles were instance fields of the renderer singleton (shared between the baking and the
     * render thread); they are local values here.
     */
    public static class Stirling implements BlockEntityRenderer<TileStirlingGenerator> {
        public Stirling(BlockEntityRendererProvider.Context context) {
        }

        @Override
        public void render(TileStirlingGenerator generator, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
            if (generator.getLevel() == null) {
                return;
            }
            Direction facing = generator.getPrimaryFacing();
            float rotation = generator.getClientOutputRotation(facing, partialTick);
            float armAngle = armAngle(-rotation, 1.0F, 9.0F, 0.0F);
            // The original passes "-rotation - 90" with the rotation in radians: 90 radians, not 90 degrees. This looks like
            // a slip but it is what the animation was tuned with, so it is kept.
            float armAngle2 = armAngle(-rotation - 90.0F, 1.0F, 7.0F, -2.0F);

            poseStack.pushPose();
            PartRenderer.faceFront(poseStack, facing);

            // flywheel
            poseStack.pushPose();
            PartRenderer.rotateAbout(poseStack, Axis.ZP, rotation, 1.0D / 2.0D, 1.0D / 2.0D, 0.0D);
            PartRenderer.draw(poseStack, buffers, AutomationModels.STIRLING_GENERATOR.get("flywheel"), light, overlay);
            poseStack.popPose();

            // piston cranks
            poseStack.pushPose();
            PartRenderer.rotateAbout(poseStack, Axis.ZP, -rotation, 12.0D / 16.0D, 12.0D / 16.0D, 0.0D);
            PartRenderer.draw(poseStack, buffers, AutomationModels.STIRLING_GENERATOR.get("piston_crank"), light, overlay);
            poseStack.popPose();

            poseStack.pushPose();
            PartRenderer.rotateAbout(poseStack, Axis.ZP, rotation, 12.0D / 16.0D, 11.0D / 16.0D, 0.0D);
            PartRenderer.draw(poseStack, buffers, AutomationModels.STIRLING_GENERATOR.get("piston_crank2"), light, overlay);
            poseStack.popPose();

            // flywheel arm: its own rotation first, then the flywheel rotation
            poseStack.pushPose();
            PartRenderer.rotateAbout(poseStack, Axis.ZP, rotation, 1.0D / 2.0D, 1.0D / 2.0D, 0.0D);
            PartRenderer.rotateAbout(poseStack, Axis.ZP, -rotation, 9.0D / 16.0D, 8.0D / 16.0D, 7.5D / 16.0D);
            PartRenderer.draw(poseStack, buffers, AutomationModels.STIRLING_GENERATOR.get("flywheel_arm"), light, overlay);
            poseStack.popPose();

            // piston arms: the arm swing first, then the rotation of the crank they hang on
            poseStack.pushPose();
            PartRenderer.rotateAbout(poseStack, Axis.ZP, -rotation, 12.0D / 16.0D, 12.0D / 16.0D, 0.0D);
            PartRenderer.rotateAbout(poseStack, Axis.ZP, rotation + armAngle, 11.0D / 16.0D, 12.0D / 16.0D, 0.0D);
            PartRenderer.draw(poseStack, buffers, AutomationModels.STIRLING_GENERATOR.get("piston_arm"), light, overlay);
            poseStack.popPose();

            poseStack.pushPose();
            PartRenderer.rotateAbout(poseStack, Axis.ZP, -rotation, 12.0D / 16.0D, 12.0D / 16.0D, 0.0D);
            PartRenderer.rotateAbout(poseStack, Axis.ZP, rotation + armAngle2, 11.0D / 16.0D, 12.0D / 16.0D, 0.0D);
            PartRenderer.draw(poseStack, buffers, AutomationModels.STIRLING_GENERATOR.get("piston_arm2"), light, overlay);
            poseStack.popPose();

            poseStack.popPose();
        }

        /** Angle of a connecting rod of the given length for a crank of the given radius turned by the crank angle (radians). */
        private static float armAngle(float crankAngle, float radius, float length, float cx) {
            float cos = Mth.cos(crankAngle);
            float sin = Mth.sin(crankAngle);
            float pistonPos = radius * cos + Mth.sqrt(length * length - radius * radius * sin * sin);
            float bx = sin * radius;
            float by = cos * radius;
            return (float) Math.atan2(cx - bx, pistonPos - by);
        }

        @Override
        public AABB getRenderBoundingBox(TileStirlingGenerator generator) {
            return generator.getRenderBounds();
        }
    }

    /** Waterwheel generator: the output gear, and the wheel (which sticks out of the block) when the water channel is valid. */
    public static class Waterwheel implements BlockEntityRenderer<TileWaterwheelGenerator> {
        public Waterwheel(BlockEntityRendererProvider.Context context) {
        }

        @Override
        public void render(TileWaterwheelGenerator generator, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
            if (generator.getLevel() == null) {
                return;
            }
            Direction facing = generator.getPrimaryFacing();
            float outputRotation = generator.getClientOutputRotation(facing, partialTick);

            poseStack.pushPose();
            PartRenderer.faceFront(poseStack, facing);

            poseStack.pushPose();
            PartRenderer.rotateAboutCenter(poseStack, Axis.ZP, outputRotation);
            PartRenderer.draw(poseStack, buffers, AutomationModels.WATERWHEEL_GENERATOR.get("output_gear"), light, overlay);
            poseStack.popPose();

            if (generator.isValidSetup()) {
                float wheelRotation = generator.getClientOutputRotation(facing.getOpposite(), partialTick);
                poseStack.pushPose();
                PartRenderer.rotateAbout(poseStack, Axis.ZP, wheelRotation, 8.0D / 16.0D, 8.0D / 16.0D, 16.0D / 16.0D);
                PartRenderer.draw(poseStack, buffers, AutomationModels.WATERWHEEL_GENERATOR.get("wheel"), light, overlay);
                poseStack.popPose();
            }

            poseStack.popPose();
        }

        @Override
        public AABB getRenderBoundingBox(TileWaterwheelGenerator generator) {
            return generator.getRenderBounds();
        }
    }

    /** Windmill generator (the controller block under the rotor): only the output gear moves. */
    public static class Windmill implements BlockEntityRenderer<TileWindmillController> {
        public Windmill(BlockEntityRendererProvider.Context context) {
        }

        @Override
        public void render(TileWindmillController generator, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
            if (generator.getLevel() == null) {
                return;
            }
            Direction facing = generator.getPrimaryFacing();
            float outputRotation = generator.getClientOutputRotation(facing, partialTick);

            poseStack.pushPose();
            PartRenderer.faceFront(poseStack, facing);
            PartRenderer.rotateAboutCenter(poseStack, Axis.ZP, outputRotation);
            PartRenderer.draw(poseStack, buffers, AutomationModels.WINDMILL_GENERATOR.get("output_gear"), light, overlay);
            poseStack.popPose();
        }

        @Override
        public AABB getRenderBoundingBox(TileWindmillController generator) {
            return generator.getRenderBounds();
        }
    }
}
