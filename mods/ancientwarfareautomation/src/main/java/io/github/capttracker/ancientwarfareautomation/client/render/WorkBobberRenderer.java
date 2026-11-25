/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 3): draws the bobber of the shore fisher; after the vanilla FishingHookRenderer, with any entity (the worker) as the owner.
 */
package io.github.capttracker.ancientwarfareautomation.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.capttracker.ancientwarfareautomation.entity.WorkBobberEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The bobber quad of vanilla (its own texture, {@code textures/entity/fishing_hook.png}) with a line to the worker's hand. The bobber bobs a little on
 * the water and dips while a fish bites.
 */
public class WorkBobberRenderer extends EntityRenderer<WorkBobberEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fishing_hook.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutout(TEXTURE);

    public WorkBobberRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(WorkBobberEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        Entity owner = entity.getOwnerEntity();
        if (owner == null) {
            return;
        }
        float age = entity.tickCount + partialTicks;
        float bob = Mth.sin(age * 0.25F) * 0.015F - (entity.isBiting() ? 0.12F + Mth.sin(age * 1.6F) * 0.04F : 0.0F);

        poseStack.pushPose();
        poseStack.translate(0.0D, bob, 0.0D);
        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer consumer = buffer.getBuffer(RENDER_TYPE);
        vertex(consumer, pose, packedLight, 0.0F, 0, 0, 1);
        vertex(consumer, pose, packedLight, 1.0F, 0, 1, 1);
        vertex(consumer, pose, packedLight, 1.0F, 1, 1, 0);
        vertex(consumer, pose, packedLight, 0.0F, 1, 0, 0);
        poseStack.popPose();

        Vec3 hand = handPosition(owner, partialTicks);
        Vec3 from = entity.getPosition(partialTicks).add(0.0D, 0.25D + bob, 0.0D);
        float dx = (float) (hand.x - from.x);
        float dy = (float) (hand.y - from.y);
        float dz = (float) (hand.z - from.z);
        VertexConsumer line = buffer.getBuffer(RenderType.lineStrip());
        PoseStack.Pose linePose = poseStack.last();
        for (int i = 0; i <= 16; i++) {
            stringVertex(dx, dy, dz, line, linePose, i / 16.0F, (i + 1) / 16.0F);
        }
        poseStack.popPose();
        super.render(entity, yaw, partialTicks, poseStack, buffer, packedLight);
    }

    /** Where the rod tip is: in front of the worker at about chest height, on its right side. */
    private static Vec3 handPosition(Entity owner, float partialTicks) {
        float bodyYaw = owner instanceof LivingEntity living ? Mth.lerp(partialTicks, living.yBodyRotO, living.yBodyRot) : owner.getYRot();
        float rad = bodyYaw * (float) (Math.PI / 180.0D);
        double sin = Mth.sin(rad);
        double cos = Mth.cos(rad);
        double side = 0.35D;
        double forward = 0.8D;
        return owner.getEyePosition(partialTicks).add(-cos * side - sin * forward, -0.45D, -sin * side + cos * forward);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int light, float x, int y, int u, int v) {
        consumer.addVertex(pose, x - 0.5F, y - 0.5F, 0.0F).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static void stringVertex(float x, float y, float z, VertexConsumer consumer, PoseStack.Pose pose, float fraction, float nextFraction) {
        float px = x * fraction;
        float py = y * (fraction * fraction + fraction) * 0.5F + 0.25F;
        float pz = z * fraction;
        float nx = x * nextFraction - px;
        float ny = y * (nextFraction * nextFraction + nextFraction) * 0.5F + 0.25F - py;
        float nz = z * nextFraction - pz;
        float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0E-6F) {
            length = 1.0F;
        }
        consumer.addVertex(pose, px, py, pz).setColor(-16777216).setNormal(pose, nx / length, ny / length, nz / length);
    }

    @Override
    public ResourceLocation getTextureLocation(WorkBobberEntity entity) {
        return TEXTURE;
    }
}
