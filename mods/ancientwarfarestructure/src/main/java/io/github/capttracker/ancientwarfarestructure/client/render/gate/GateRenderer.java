/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: structure/render/RenderGateHelper, GateProxyRenderer (the TESR on a render proxy block)
 * and RenderGateInvisible.
 */
package io.github.capttracker.ancientwarfarestructure.client.render.gate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.capttracker.ancientwarfarestructure.entity.EntityGate;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws a gate. In 1.12.2 the gate entity had an invisible renderer and a tile entity renderer on one proxy block drew the
 * gate, because the entity was too large for the frustum culling; the entity here has {@code noCulling} and a render
 * distance of 256 blocks (the TESR's), so it is drawn directly. The transform is the original one (the entity position,
 * then {@code scale(-1, -1, 1)} for the y-down Techne models), the type decides the delegate, a recently hurt gate is
 * tinted red.
 */
public class GateRenderer extends EntityRenderer<EntityGate> {
    private final Map<Integer, GateModelRenderer> renderers = new HashMap<>();

    public GateRenderer(EntityRendererProvider.Context context) {
        super(context);
        GateModelBasic basic = new GateModelBasic(context.bakeLayer(GateModelBasic.LAYER));
        GateModelBridge bridge = new GateModelBridge(context.bakeLayer(GateModelBridge.LAYER));
        renderers.put(0, new RenderGateBasic(basic));
        renderers.put(1, new RenderGateBasic(basic));
        renderers.put(4, new RenderGateSingle(basic));
        renderers.put(5, new RenderGateSingle(basic));
        renderers.put(8, new RenderGateDouble(basic));
        renderers.put(9, new RenderGateDouble(basic));
        renderers.put(12, new RenderGateRotatingBridge(bridge));
    }

    @Override
    public void render(EntityGate gate, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (gate.pos1 == null || gate.pos2 == null) {
            return;
        }
        GateModelRenderer delegate = renderers.get(gate.getGateType().getGlobalID());
        if (delegate == null) {
            return;
        }
        // 1.12.2: GlStateManager.color(1, 1 - p, 1 - p, 1) while the hurt animation runs
        int greenBlue = 255;
        if (gate.hurtAnimationTicks > 0) {
            float percent = Mth.clamp(gate.hurtAnimationTicks / 20.f, 0.f, 1.f);
            greenBlue = (int) ((1.f - percent) * 255.f);
        }
        int color = FastColor.ARGB32.color(255, 255, greenBlue, greenBlue);
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(gate)));

        poseStack.pushPose();
        poseStack.scale(-1, -1, 1);
        delegate.render(gate, partialTick, new GateDraw(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, color));
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(EntityGate gate) {
        return gate.getGateType().getTexture();
    }
}
