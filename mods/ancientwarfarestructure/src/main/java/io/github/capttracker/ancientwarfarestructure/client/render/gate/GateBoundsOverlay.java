/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: ItemGateSpawner#renderBox and IBoxRenderer.Util#renderBoundingBox (the white outline of
 * the area that the gate spawner is about to use).
 */
package io.github.capttracker.ancientwarfarestructure.client.render.gate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfarestructure.AncientWarfareStructure;
import io.github.capttracker.ancientwarfarestructure.init.AwGates;
import io.github.capttracker.ancientwarfarestructure.item.GateBounds;
import io.github.capttracker.ancientwarfarestructure.item.GateSpawnerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import javax.annotation.Nullable;

/**
 * Draws the outline of the chosen corners (or of the block the player looks at, while a corner is not set yet) for every
 * gate spawner in the player's hands. 1.12.2 drew it with immediate-mode GL lines in the world render event; this is the
 * vanilla lines render type after the particles. Game-bus listener, client only.
 */
@EventBusSubscriber(modid = AncientWarfareStructure.MOD_ID, value = Dist.CLIENT)
public final class GateBoundsOverlay {
    private GateBoundsOverlay() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean drew = false;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof GateSpawnerItem)) {
                continue;
            }
            AABB box = boxFor(player, stack);
            if (box == null) {
                continue;
            }
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            poseStack.pushPose();
            poseStack.translate(-camera.x, -camera.y, -camera.z); // the box is in world coordinates
            LevelRenderer.renderLineBox(poseStack, lines, box, 1.f, 1.f, 1.f, 1.f);
            poseStack.popPose();
            drew = true;
        }
        if (drew) {
            buffers.endBatch(RenderType.lines());
        }
    }

    @Nullable
    private static AABB boxFor(LocalPlayer player, ItemStack stack) {
        GateBounds bounds = stack.getOrDefault(AwGates.GATE_BOUNDS.get(), GateBounds.EMPTY);
        BlockPos p1;
        BlockPos p2;
        if (bounds.pos1().isPresent()) {
            p1 = bounds.pos1().get();
            p2 = bounds.pos2().isPresent() ? bounds.pos2().get() : BlockTools.getBlockClickedOn(player, player.level(), true);
        } else {
            p1 = BlockTools.getBlockClickedOn(player, player.level(), true);
            p2 = p1;
        }
        if (p1 == null || p2 == null) {
            return null;
        }
        BlockPos min = BlockTools.getMin(p1, p2);
        BlockPos max = BlockTools.getMax(p1, p2);
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }
}
