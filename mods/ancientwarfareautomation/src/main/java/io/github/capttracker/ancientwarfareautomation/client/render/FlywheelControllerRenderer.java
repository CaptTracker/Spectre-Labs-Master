/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/render/FlywheelControllerRenderer and FlywheelControllerAnimationRenderer.
 */
package io.github.capttracker.ancientwarfareautomation.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.capttracker.ancientwarfare.api.ITorque.ITorqueTile;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileFlywheelController;
import io.github.capttracker.ancientwarfareautomation.block.torque.TorqueTier;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * Draws the three moving parts of the flywheel controller: the input gear (turns with the block behind when that block
 * gives a rotation, else like the output), the output gear and the spindle, which turns around Y with the rotation of
 * the flywheel it controls. The housing is the static block model.
 */
public class FlywheelControllerRenderer implements BlockEntityRenderer<TileFlywheelController> {

    public FlywheelControllerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TileFlywheelController controller, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (controller.getLevel() == null) {
            return;
        }
        TorqueTier tier = controller.getTier();
        Direction facing = controller.getPrimaryFacing();
        float outputRotation = controller.getClientOutputRotation(facing, partialTick);
        float inputRotation = outputRotation;
        ITorqueTile behind = controller.getTorqueCache()[facing.getOpposite().get3DDataValue()];
        if (behind != null && behind.canOutputTorque(facing) && behind.useOutputRotation(facing.getOpposite())) {
            inputRotation = behind.getClientOutputRotation(facing, partialTick);
        }
        float flywheelRotation = controller.getFlywheelRotation(partialTick);

        poseStack.pushPose();
        PartRenderer.faceFront(poseStack, facing);

        poseStack.pushPose();
        PartRenderer.rotateAboutCenter(poseStack, Axis.ZP, inputRotation);
        PartRenderer.draw(poseStack, buffers, AutomationModels.FLYWHEEL_CONTROLLER.get("input_gear", tier), light, overlay);
        poseStack.popPose();

        poseStack.pushPose();
        PartRenderer.rotateAboutCenter(poseStack, Axis.ZP, outputRotation);
        PartRenderer.draw(poseStack, buffers, AutomationModels.FLYWHEEL_CONTROLLER.get("output_gear", tier), light, overlay);
        poseStack.popPose();

        poseStack.pushPose();
        PartRenderer.rotateAboutCenter(poseStack, Axis.YP, flywheelRotation);
        PartRenderer.draw(poseStack, buffers, AutomationModels.FLYWHEEL_CONTROLLER.get("spindle", tier), light, overlay);
        poseStack.popPose();

        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(TileFlywheelController controller) {
        return controller.getRenderBounds();
    }
}
