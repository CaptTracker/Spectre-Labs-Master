/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the GlStateManager state that structure/render/gate/* and RenderGateHelper drew with
 * (the matrix stack, the bound texture and the hurt tint) as one value.
 */
package io.github.capttracker.ancientwarfarestructure.client.render.gate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Everything that drawing a model part needs: the pose stack (which the gate renderers push, pop and translate like the
 * 1.12.2 matrix stack), the buffer of the gate's texture, the light and the colour (the red hurt tint).
 */
public record GateDraw(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {

    /** Draws the part and its children with the current pose. */
    public void render(ModelPart part) {
        part.render(poseStack, buffer, packedLight, packedOverlay, color);
    }

    public void push() {
        poseStack.pushPose();
    }

    public void pop() {
        poseStack.popPose();
    }

    public void translate(double x, double y, double z) {
        poseStack.translate(x, y, z);
    }
}
