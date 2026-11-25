/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/render/RenderCommandOverlay.
 */
package io.github.capttracker.ancientwarfarenpc.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.capttracker.ancientwarfare.util.EntityTools;
import io.github.capttracker.ancientwarfare.util.RayTraceUtils;
import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.item.ItemCommandBaton;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * What the player sees while holding the command baton: a white outline around the block or living entity that is looked at (up to
 * {@link ItemCommandBaton#RANGE} blocks), a red outline around each NPC bound to the baton, and a text HUD with the looked-at target at the top
 * right and the names of the commanded NPCs at the top left.
 *
 * <p>1.12.2 did all of it in the render class: it ray traced and searched the commanded entities at the start of each frame
 * ({@code RenderTickEvent}), drew the boxes in {@code RenderWorldLastEvent} and the text at the end of the frame. Now the game bus listener
 * {@link #onRenderLevel} updates the state and draws the boxes after the particles ({@code RenderLevelStageEvent}, vanilla lines render type),
 * and {@link Hud}, a GUI layer that {@code NpcRenderClient} registers, draws the text from that state.
 *
 * <p>Changes: the commanded NPCs are looked up every {@value #REFRESH_TICKS} ticks, not every frame (the client lookup scans the entities around the
 * player); the target text is cleared when nothing is hit (1.12.2 kept the last one).
 *
 * <p>Client only.
 */
@EventBusSubscriber(modid = AncientWarfareNPC.MOD_ID, value = Dist.CLIENT)
public final class NpcCommandOverlay {
    private static final int REFRESH_TICKS = 4;
    private static final double TARGET_BLOCK_GROW = 0.1D;

    private static boolean active;
    @Nullable
    private static HitResult target;
    @Nullable
    private static String targetString;
    private static List<Entity> commanded = List.of();
    private static long lastRefresh = Long.MIN_VALUE;

    private NpcCommandOverlay() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) {
            reset();
            return;
        }
        ItemStack baton = EntityTools.getItemFromEitherHand(mc.player, ItemCommandBaton.class);
        if (baton.isEmpty()) {
            reset();
            return;
        }
        active = true;
        target = RayTraceUtils.getPlayerTarget(mc.player, ItemCommandBaton.RANGE, 0);
        targetString = describe(target);
        long time = mc.level.getGameTime();
        if (time < lastRefresh || time - lastRefresh >= REFRESH_TICKS) {
            commanded = new ArrayList<>(ItemCommandBaton.getCommandedEntities(mc.player, baton));
            lastRefresh = time;
        }

        PoseStack poseStack = event.getPoseStack();
        if (poseStack == null) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z); // the boxes are in world coordinates
        try {
            AABB targetBox = targetBox(partialTick);
            if (targetBox != null) {
                LevelRenderer.renderLineBox(poseStack, lines, targetBox, 1.0F, 1.0F, 1.0F, 1.0F);
            }
            for (Entity entity : commanded) {
                if (!entity.isRemoved()) {
                    LevelRenderer.renderLineBox(poseStack, lines, interpolatedBox(entity, partialTick), 1.0F, 0.0F, 0.0F, 1.0F);
                }
            }
        } finally {
            poseStack.popPose();
            buffers.endBatch(RenderType.lines());
        }
    }

    private static void reset() {
        active = false;
        target = null;
        targetString = null;
        commanded = List.of();
    }

    @Nullable
    private static String describe(@Nullable HitResult hit) {
        if (hit instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            return pos.getX() + "," + pos.getY() + "," + pos.getZ();
        }
        if (hit instanceof EntityHitResult entityHit) {
            return entityHit.getEntity().getName().getString();
        }
        return null;
    }

    @Nullable
    private static AABB targetBox(float partialTick) {
        HitResult hit = target;
        if (hit instanceof BlockHitResult blockHit) {
            return new AABB(blockHit.getBlockPos()).inflate(TARGET_BLOCK_GROW);
        }
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living) {
            return interpolatedBox(living, partialTick);
        }
        return null;
    }

    /** The entity's box moved from its tick position to where it is drawn between the last and this tick. */
    private static AABB interpolatedBox(Entity entity, float partialTick) {
        double dx = entity.getX() - Mth.lerp((double) partialTick, entity.xo, entity.getX());
        double dy = entity.getY() - Mth.lerp((double) partialTick, entity.yo, entity.getY());
        double dz = entity.getZ() - Mth.lerp((double) partialTick, entity.zo, entity.getZ());
        return entity.getBoundingBox().move(-dx, -dy, -dz);
    }

    /** The text of the overlay, a GUI layer above everything else (skipped with a screen, the debug screen or hidden GUI, as before). */
    public static final class Hud implements LayeredDraw.Layer {
        private static final int WHITE = 0xFFFFFFFF;

        @Override
        public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
            Minecraft mc = Minecraft.getInstance();
            if (!active || mc.player == null || mc.screen != null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
                return;
            }
            Font font = mc.font;
            int x = graphics.guiWidth() - 10;
            Component header = Component.translatable("guistrings.npc.target");
            graphics.drawString(font, header, x - font.width(header), 0, WHITE);
            String targetText = targetString;
            if (targetText != null) {
                graphics.drawString(font, targetText, x - font.width(targetText), 10, WHITE);
            }
            graphics.drawString(font, Component.translatable("guistrings.npc.commanded"), 10, 0, WHITE);
            int line = 0;
            for (Entity entity : commanded) {
                if (!entity.isRemoved()) {
                    graphics.drawString(font, entity.getName(), 10, 10 + 10 * line, WHITE);
                    line++;
                }
            }
        }
    }
}
