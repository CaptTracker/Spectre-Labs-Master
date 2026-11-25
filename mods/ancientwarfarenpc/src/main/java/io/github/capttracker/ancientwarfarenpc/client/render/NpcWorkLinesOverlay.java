/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/render/RenderWorkLines.
 */
package io.github.capttracker.ancientwarfarenpc.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.item.order.ItemOrders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

/**
 * The positions stored on the order slip in the player's hands (work sites, patrol and route points, ...): a white outline around each block, its
 * number above it and a faint white line from each point to the one before. Drawn when the client option {@code render_work_points} is on.
 *
 * <p>1.12.2 used immediate mode GL in {@code RenderWorldLastEvent}; this is a {@link RenderLevelStageEvent} after the particles with the vanilla lines
 * render type and {@code Font.drawInBatch} for the numbers (which now face the camera, 1.12.2 turned them by the player's yaw and pitch). Game bus
 * listener, client only.
 */
@EventBusSubscriber(modid = AncientWarfareNPC.MOD_ID, value = Dist.CLIENT)
public final class NpcWorkLinesOverlay {
    private static final float TEXT_SCALE = 0.016666668F * 1.6F;

    private NpcWorkLinesOverlay() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !AWNPCStatics.renderWorkPoints) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        PoseStack poseStack = event.getPoseStack();
        if (player == null || mc.level == null || poseStack == null) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof ItemOrders)) {
            stack = player.getOffhandItem();
        }
        if (!(stack.getItem() instanceof ItemOrders orders)) {
            return;
        }
        List<BlockPos> points = orders.getPositionsForRender(stack);
        if (points.isEmpty()) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z); // everything below is in world coordinates
        try {
            BlockPos previous = null;
            for (BlockPos point : points) {
                LevelRenderer.renderLineBox(poseStack, lines, new AABB(point), 1.0F, 1.0F, 1.0F, 1.0F);
                if (previous != null) {
                    line(poseStack, lines, point, previous);
                }
                previous = point;
            }
            buffers.endBatch(RenderType.lines());

            int index = 1;
            for (BlockPos point : points) {
                renderNumber(mc, poseStack, buffers, point, String.valueOf(index));
                index++;
            }
        } finally {
            poseStack.popPose();
            buffers.endBatch();
        }
    }

    /** The faint line between the centres of two blocks (alpha 0.4, as before). */
    private static void line(PoseStack poseStack, VertexConsumer lines, BlockPos from, BlockPos to) {
        float x1 = from.getX() + 0.5F;
        float y1 = from.getY() + 0.5F;
        float z1 = from.getZ() + 0.5F;
        float dx = to.getX() - from.getX();
        float dy = to.getY() - from.getY();
        float dz = to.getZ() - from.getZ();
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-6F) {
            return;
        }
        // the lines render type needs the direction of the line as the vertex normal
        float nx = dx / length;
        float ny = dy / length;
        float nz = dz / length;
        PoseStack.Pose pose = poseStack.last();
        lines.addVertex(pose, x1, y1, z1).setColor(1.0F, 1.0F, 1.0F, 0.4F).setNormal(pose, nx, ny, nz);
        lines.addVertex(pose, x1 + dx, y1 + dy, z1 + dz).setColor(1.0F, 1.0F, 1.0F, 0.4F).setNormal(pose, nx, ny, nz);
    }

    private static void renderNumber(Minecraft mc, PoseStack poseStack, MultiBufferSource buffers, BlockPos point, String text) {
        poseStack.pushPose();
        poseStack.translate(point.getX() + 0.5D, point.getY() + 1.5D, point.getZ() + 0.5D);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
        Matrix4f pose = poseStack.last().pose();
        Font font = mc.font;
        font.drawInBatch(text, -font.width(text) / 2.0F, 0, 0xFFFFFFFF, false, pose, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }
}
