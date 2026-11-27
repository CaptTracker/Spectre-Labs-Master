/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 11): the rope and hook of a grappling line.
 */
package io.github.capttracker.ancientwarfarevehicle.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.GrappleHook;
import io.github.capttracker.ancientwarfarevehicle.init.AwVehicles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws a grappling line the way the hitch rope is drawn (a strip in the leash render type that sags in the middle): from the line's anchor on the source ship to the
 * hook on the target ship, both read from the ships on the client, with the hook item at the far end. The rope hangs slack while the hooks fly and pulls tight
 * once the ships are together. Client only.
 */
public class GrappleHookRenderer extends EntityRenderer<GrappleHook> {
    private static final int SEGMENTS = 24;
    private final ItemStack hookStack = new ItemStack(AwVehicles.GRAPPLING_HOOK.get());

    public GrappleHookRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    /** The rope is longer than the entity's small box: it is drawn whenever the entity is near enough. */
    @Override
    public boolean shouldRender(GrappleHook hook, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(GrappleHook hook) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(GrappleHook hook, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(hook, yaw, partialTick, poseStack, buffer, packedLight);
        Vec3 from = hook.sourceAnchor(partialTick);
        Vec3 to = hook.targetAnchor();
        if (from == null || to == null) {
            return;
        }
        if (hook.phase() == GrappleHook.Phase.FLYING) {
            // the hook is still in the air: the rope runs to where it is
            to = hook.getPosition(partialTick);
        }
        Vec3 origin = hook.getPosition(partialTick);
        Vec3 start = from.subtract(origin);
        Vec3 end = to.subtract(origin);
        boolean taut = hook.phase() == GrappleHook.Phase.HELD;
        drawRope(hook, partialTick, poseStack, buffer, start, end, taut ? 0.04F : 0.25F);

        poseStack.pushPose();
        poseStack.translate(end.x, end.y, end.z);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees((hook.tickCount + partialTick) * 4.0F));
        poseStack.scale(0.6F, 0.6F, 0.6F);
        Minecraft.getInstance().getItemRenderer().renderStatic(hookStack, ItemDisplayContext.GROUND, packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffer, hook.level(), hook.getId());
        poseStack.popPose();
    }

    private static void drawRope(GrappleHook hook, float partialTick, PoseStack poseStack, MultiBufferSource buffer, Vec3 start, Vec3 end, float sagFactor) {
        poseStack.pushPose();
        poseStack.translate(start.x, start.y, start.z);
        float dx = (float) (end.x - start.x);
        float dy = (float) (end.y - start.y);
        float dz = (float) (end.z - start.z);
        VertexConsumer consumer = buffer.getBuffer(RenderType.leash());
        Matrix4f pose = poseStack.last().pose();
        float length = Mth.invSqrt(dx * dx + dz * dz + 1.0E-4F);
        float sideX = dz * length * 0.025F / 2.0F;
        float sideZ = dx * length * 0.025F / 2.0F;
        BlockPos startPos = BlockPos.containing(hook.getPosition(partialTick).add(start));
        BlockPos endPos = BlockPos.containing(hook.getPosition(partialTick).add(end));
        int startBlock = hook.level().getBrightness(LightLayer.BLOCK, startPos);
        int endBlock = hook.level().getBrightness(LightLayer.BLOCK, endPos);
        int startSky = hook.level().getBrightness(LightLayer.SKY, startPos);
        int endSky = hook.level().getBrightness(LightLayer.SKY, endPos);
        float span = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        float sag = Math.min(2.0F, span * sagFactor);
        for (int i = 0; i <= SEGMENTS; i++) {
            addVertexPair(consumer, pose, dx, dy, dz, startBlock, endBlock, startSky, endSky, 0.025F, sideX, sideZ, i, false, sag);
        }
        for (int i = SEGMENTS; i >= 0; i--) {
            addVertexPair(consumer, pose, dx, dy, dz, startBlock, endBlock, startSky, endSky, 0.025F, sideX, sideZ, i, true, sag);
        }
        poseStack.popPose();
    }

    private static void addVertexPair(VertexConsumer consumer, Matrix4f pose, float x, float y, float z, int startBlock, int endBlock, int startSky, int endSky,
                                      float width, float sideX, float sideZ, int index, boolean reverse, float sag) {
        float t = (float) index / SEGMENTS;
        int light = LightTexture.pack((int) Mth.lerp(t, (float) startBlock, (float) endBlock), (int) Mth.lerp(t, (float) startSky, (float) endSky));
        float shade = index % 2 == (reverse ? 1 : 0) ? 0.7F : 1.0F;
        float r = 0.5F * shade;
        float g = 0.4F * shade;
        float b = 0.3F * shade;
        float px = x * t;
        float py = y * t - 4.0F * t * (1.0F - t) * sag;
        float pz = z * t;
        consumer.addVertex(pose, px - sideX, py + width, pz + sideZ).setColor(r, g, b, 1.0F).setLight(light);
        consumer.addVertex(pose, px + sideX, py, pz - sideZ).setColor(r, g, b, 1.0F).setLight(light);
    }
}
