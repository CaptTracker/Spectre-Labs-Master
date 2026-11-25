/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: the rope between a hitched horse and its cart.
 */
package io.github.capttracker.ancientwarfarevehicle.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.hitch.CartHitch;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws the rope of a hitched cart the way vanilla draws a leash ({@code EntityRenderer#renderLeash}): a strip of 24 segments in the leash
 * render type that sags in the middle, lit by the light at each end. It runs from the front of the cart's drawbar to the rear of each
 * hitched horse; the client knows the horses from the vehicle's synced hitch data. Client only.
 */
final class HitchRopeRenderer {
    private static final int SEGMENTS = 24;
    /** The height of the rope above the cart's feet at the drawbar, and above the horse's feet at its rear. */
    private static final double CART_HEIGHT = 0.6D;
    private static final double HORSE_HEIGHT_FACTOR = 0.55D;

    private HitchRopeRenderer() {}

    /** Called with the pose at the vehicle's (interpolated) position, as the entity renderer has it. */
    static void render(VehicleBase vehicle, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource) {
        int[] ids = vehicle.getHitchedIds();
        if (ids.length == 0) {
            return;
        }
        double yaw = Math.toRadians(Mth.rotLerp(partialTick, vehicle.yRotO, vehicle.getYRot()));
        double reach = vehicle.vehicleType.getHitchOffset();
        Vec3 origin = vehicle.getPosition(partialTick);
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] < 0 || !(vehicle.level().getEntity(ids[i]) instanceof AbstractHorse horse) || !horse.isAlive()) {
                continue;
            }
            // the cart's forward is (-sin, -cos) of its yaw, its right is (cos, -sin)
            double lateral = CartHitch.lateralAt(i, ids.length);
            Vec3 start = new Vec3(
                    -Math.sin(yaw) * reach + Math.cos(yaw) * lateral,
                    CART_HEIGHT,
                    -Math.cos(yaw) * reach - Math.sin(yaw) * lateral);
            Vec3 horsePos = horse.getPosition(partialTick);
            double bodyYaw = Math.toRadians(Mth.rotLerp(partialTick, horse.yBodyRotO, horse.yBodyRot));
            Vec3 end = new Vec3(
                    horsePos.x + Math.sin(bodyYaw) * CartHitch.HORSE_REAR - origin.x,
                    horsePos.y + horse.getBbHeight() * HORSE_HEIGHT_FACTOR - origin.y,
                    horsePos.z - Math.cos(bodyYaw) * CartHitch.HORSE_REAR - origin.z);
            drawRope(vehicle, horse, partialTick, poseStack, bufferSource, start, end);
        }
    }

    private static void drawRope(VehicleBase vehicle, Entity horse, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, Vec3 start, Vec3 end) {
        poseStack.pushPose();
        poseStack.translate(start.x, start.y, start.z);
        float dx = (float) (end.x - start.x);
        float dy = (float) (end.y - start.y);
        float dz = (float) (end.z - start.z);
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.leash());
        Matrix4f pose = poseStack.last().pose();
        float length = Mth.invSqrt(dx * dx + dz * dz);
        // the half width of the strip, turned to lie across the rope
        float sideX = dz * length * 0.025F / 2.0F;
        float sideZ = dx * length * 0.025F / 2.0F;
        BlockPos startPos = BlockPos.containing(vehicle.getPosition(partialTick).add(0.0D, CART_HEIGHT, 0.0D));
        BlockPos endPos = BlockPos.containing(horse.getEyePosition(partialTick));
        int startBlock = vehicle.level().getBrightness(LightLayer.BLOCK, startPos);
        int endBlock = vehicle.level().getBrightness(LightLayer.BLOCK, endPos);
        int startSky = vehicle.level().getBrightness(LightLayer.SKY, startPos);
        int endSky = vehicle.level().getBrightness(LightLayer.SKY, endPos);
        for (int i = 0; i <= SEGMENTS; i++) {
            addVertexPair(consumer, pose, dx, dy, dz, startBlock, endBlock, startSky, endSky, 0.025F, sideX, sideZ, i, false);
        }
        for (int i = SEGMENTS; i >= 0; i--) {
            addVertexPair(consumer, pose, dx, dy, dz, startBlock, endBlock, startSky, endSky, 0.025F, sideX, sideZ, i, true);
        }
        poseStack.popPose();
    }

    private static void addVertexPair(VertexConsumer consumer, Matrix4f pose, float x, float y, float z, int startBlock, int endBlock, int startSky, int endSky,
                                      float width, float sideX, float sideZ, int index, boolean reverse) {
        float t = (float) index / SEGMENTS;
        int light = LightTexture.pack((int) Mth.lerp(t, (float) startBlock, (float) endBlock), (int) Mth.lerp(t, (float) startSky, (float) endSky));
        float shade = index % 2 == (reverse ? 1 : 0) ? 0.7F : 1.0F;
        float r = 0.5F * shade;
        float g = 0.4F * shade;
        float b = 0.3F * shade;
        float px = x * t;
        // the rope hangs in the middle: the sag is zero at both ends
        float py = y * t - sagOf(t, x, z);
        float pz = z * t;
        consumer.addVertex(pose, px - sideX, py + width, pz + sideZ).setColor(r, g, b, 1.0F).setLight(light);
        consumer.addVertex(pose, px + sideX, py, pz - sideZ).setColor(r, g, b, 1.0F).setLight(light);
    }

    /** How far the rope hangs below the straight line at t (0..1): a parabola, deeper the longer the rope, at most 0.3 blocks. */
    private static float sagOf(float t, float x, float z) {
        float span = Mth.sqrt(x * x + z * z);
        return 4.0F * t * (1.0F - t) * Math.min(0.3F, span * 0.08F);
    }
}
