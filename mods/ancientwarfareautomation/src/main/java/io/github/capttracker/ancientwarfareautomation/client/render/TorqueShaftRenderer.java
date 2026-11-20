/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/render/TorqueShaftRenderer and TorqueShaftAnimationRenderer.
 */
package io.github.capttracker.ancientwarfareautomation.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.capttracker.ancientwarfare.api.ITorque.ITorqueTile;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileTorqueShaft;
import io.github.capttracker.ancientwarfareautomation.block.torque.TorqueTier;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * Draws the shaft, the gear box and the two heads of a torque shaft. Everything is drawn here, the block model has no
 * geometry: the gear box and the input head only when no shaft of the same tier feeds this one from behind, the output
 * head only when no shaft follows. The input head turns with the output rotation of the block behind it when that
 * block provides one (a generator, a flywheel), else with the shaft.
 */
public class TorqueShaftRenderer implements BlockEntityRenderer<TileTorqueShaft> {

    public TorqueShaftRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TileTorqueShaft shaft, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (shaft.getLevel() == null) {
            return;
        }
        TorqueTier tier = shaft.getTier();
        Direction facing = shaft.getPrimaryFacing();
        float rotation = shaft.getClientOutputRotation(facing, partialTick);
        boolean hasPrevious = shaft.hasPrevious();
        boolean hasNext = shaft.hasNext();

        poseStack.pushPose();
        PartRenderer.faceFront(poseStack, facing);
        if (!hasPrevious) {
            PartRenderer.draw(poseStack, buffers, AutomationModels.TORQUE_SHAFT.get("gear_box", tier), light, overlay);
        }

        poseStack.pushPose();
        PartRenderer.rotateAboutCenter(poseStack, Axis.ZP, rotation);
        PartRenderer.draw(poseStack, buffers, AutomationModels.TORQUE_SHAFT.get("shaft", tier), light, overlay);
        if (!hasNext) {
            PartRenderer.draw(poseStack, buffers, AutomationModels.TORQUE_SHAFT.get("output_head", tier), light, overlay);
        }
        poseStack.popPose();

        if (!hasPrevious) {
            float inputRotation = rotation;
            ITorqueTile behind = shaft.getTorqueCache()[facing.getOpposite().get3DDataValue()];
            if (behind != null && behind.canOutputTorque(facing) && behind.useOutputRotation(null)) {
                inputRotation = behind.getClientOutputRotation(facing, partialTick);
            }
            poseStack.pushPose();
            PartRenderer.rotateAboutCenter(poseStack, Axis.ZP, inputRotation);
            PartRenderer.draw(poseStack, buffers, AutomationModels.TORQUE_SHAFT.get("input_head", tier), light, overlay);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(TileTorqueShaft shaft) {
        return shaft.getRenderBounds();
    }
}
