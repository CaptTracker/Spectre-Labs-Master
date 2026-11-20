/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/render/WindmillBladeRenderer and WindmillBladeAnimationRenderer.
 */
package io.github.capttracker.ancientwarfareautomation.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.multiblock.TileWindmillBlade;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/**
 * A windmill blade block draws a textured cube while it is not part of a valid windmill. Once formed, only the control
 * block draws anything: the whole rotor (the shaft, two blade shafts and four arms of {@code (size - 1) / 2} blade
 * segments with a joint at the end of each arm), turning around the axis that faces along the blade plane. The block has
 * no static model; in 1.12.2 the unformed cube came from the chunk mesh, here it is drawn by this renderer because the
 * formed state is held by the block entity, not by the block state.
 */
public class WindmillBladeRenderer implements BlockEntityRenderer<TileWindmillBlade> {

    public WindmillBladeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TileWindmillBlade blade, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (blade.getLevel() == null) {
            return;
        }
        if (!blade.isFormed()) {
            PartRenderer.draw(poseStack, buffers, AutomationModels.get(AutomationModels.WINDMILL_BLADE_CUBE), light, overlay);
            return;
        }
        if (!blade.isControl()) {
            return;//formed blades have no static rendering and only the control block draws the rotor
        }
        int height = (blade.getWindmillSize() - 1) / 2;
        float rotation = blade.getRotation(partialTick);

        poseStack.pushPose();
        PartRenderer.faceFront(poseStack, blade.getDirection());
        PartRenderer.rotateAboutCenter(poseStack, Axis.ZP, rotation);
        PartRenderer.draw(poseStack, buffers, AutomationModels.WINDMILL_BLADE.get("windmill_shaft"), light, overlay);
        for (int i = 0; i < 4; i++) {
            poseStack.pushPose();
            PartRenderer.rotateAboutCenter(poseStack, Axis.ZP, i * (float) Math.PI / 2.0F);
            if (i < 2) {
                PartRenderer.draw(poseStack, buffers, AutomationModels.WINDMILL_BLADE.get("blade_shaft"), light, overlay);
            }
            for (int k = 1; k < height; k++) {
                PartRenderer.draw(poseStack, buffers, AutomationModels.WINDMILL_BLADE.get("blade"), light, overlay);
                if (k == height - 1) {
                    PartRenderer.draw(poseStack, buffers, AutomationModels.WINDMILL_BLADE.get("blade_joint"), light, overlay);
                } else {
                    poseStack.translate(0.0D, 1.0D, 0.0D);
                }
            }
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(TileWindmillBlade blade) {
        return false;
    }

    @Override
    public AABB getRenderBoundingBox(TileWindmillBlade blade) {
        return blade.getRenderBounds();
    }
}
