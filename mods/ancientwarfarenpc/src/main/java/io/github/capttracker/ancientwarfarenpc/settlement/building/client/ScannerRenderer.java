/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): building designs. Client only.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.settlement.building.BlueprintScannerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Draws the selection of the blueprint scanner in the player's hand (the two corners, the box and the side of the entrance), and the design preview. */
@EventBusSubscriber(modid = AncientWarfareNPC.MOD_ID, value = Dist.CLIENT)
public final class ScannerRenderer {
    private ScannerRenderer() {}

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        DesignPreview.renderActive(poseStack, buffers, cam);

        ItemStack held = player.getMainHandItem().getItem() instanceof BlueprintScannerItem ? player.getMainHandItem() : player.getOffhandItem();
        if (held.getItem() instanceof BlueprintScannerItem) {
            BlueprintScannerItem.Selection selection = BlueprintScannerItem.read(held);
            if (selection.a() != null && selection.dimension() != null && selection.dimension().equals(mc.level.dimension().location())) {
                VertexConsumer lines = buffers.getBuffer(RenderType.lines());
                box(poseStack, lines, cam, selection.a(), selection.a(), 0.2F, 0.9F, 1.0F, 1.0F, 0.02);
                if (selection.b() != null) {
                    box(poseStack, lines, cam, selection.b(), selection.b(), 1.0F, 0.6F, 0.1F, 1.0F, 0.02);
                    box(poseStack, lines, cam, selection.min(), selection.max(), 1.0F, 1.0F, 1.0F, 1.0F, 0.0);
                }
                if (selection.front() != null && selection.frontDir() != null) {
                    box(poseStack, lines, cam, selection.front(), selection.front(), 0.2F, 1.0F, 0.2F, 1.0F, 0.04);
                    drawFrontArrow(poseStack, lines, cam, selection.min(), selection.max(), selection.frontDir());
                }
            }
        }
        buffers.endBatch(RenderType.lines());
    }

    /** A green bar from the middle of the front side of a box, out by two and a half blocks. */
    static void drawFrontArrow(PoseStack poseStack, VertexConsumer lines, Vec3 cam, BlockPos min, BlockPos max, Direction front) {
        double cx = (min.getX() + max.getX() + 1) / 2.0;
        double cz = (min.getZ() + max.getZ() + 1) / 2.0;
        double y = min.getY() + 0.5;
        double fx = front.getStepX() * 2.5;
        double fz = front.getStepZ() * 2.5;
        double ex = front.getStepX() > 0 ? max.getX() + 1 : front.getStepX() < 0 ? min.getX() : cx;
        double ez = front.getStepZ() > 0 ? max.getZ() + 1 : front.getStepZ() < 0 ? min.getZ() : cz;
        LevelRenderer.renderLineBox(poseStack, lines, ex - cam.x - 0.05 + Math.min(0, fx), y - cam.y - 0.05, ez - cam.z - 0.05 + Math.min(0, fz),
                ex - cam.x + 0.05 + Math.max(0, fx), y - cam.y + 0.05, ez - cam.z + 0.05 + Math.max(0, fz), 0.2F, 1.0F, 0.2F, 1.0F);
    }

    static void box(PoseStack poseStack, VertexConsumer lines, Vec3 cam, BlockPos min, BlockPos max, float r, float g, float b, float a, double grow) {
        LevelRenderer.renderLineBox(poseStack, lines, min.getX() - grow - cam.x, min.getY() - grow - cam.y, min.getZ() - grow - cam.z,
                max.getX() + 1 + grow - cam.x, max.getY() + 1 + grow - cam.y, max.getZ() + 1 + grow - cam.z, r, g, b, a);
    }
}
