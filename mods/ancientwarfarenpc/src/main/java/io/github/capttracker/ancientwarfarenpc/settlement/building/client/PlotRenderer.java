/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): building zones. Client only.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcSettlements;
import io.github.capttracker.ancientwarfarenpc.settlement.building.BuildProject;
import io.github.capttracker.ancientwarfarenpc.settlement.building.BuildingNetwork;
import io.github.capttracker.ancientwarfarenpc.settlement.building.BuildingZoneItem;
import io.github.capttracker.ancientwarfarenpc.settlement.building.ClientPlotView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * While the building zone tool is in a hand: draws the plots of your settlements (a green outline on the ground, a post at each corner), the footprints of
 * their planned, queued, running and finished builds (yellow, orange, cyan, grey; red for a demolition), and the rectangle from the first corner of the
 * plot you are marking to the block you look at.
 */
@EventBusSubscriber(modid = AncientWarfareNPC.MOD_ID, value = Dist.CLIENT)
public final class PlotRenderer {
    private static final double VIEW_RANGE = 160.0D;

    private PlotRenderer() {}

    private static ItemStack heldZoneTool(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        return main.is(AwNpcSettlements.BUILDING_ZONE_TOOL.get()) ? main : player.getOffhandItem();
    }

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
        ItemStack held = heldZoneTool(player);
        if (!held.is(AwNpcSettlements.BUILDING_ZONE_TOOL.get())) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        for (BuildingNetwork.SettlementPlots settlement : ClientPlotView.get()) {
            for (BuildingNetwork.PlotView plot : settlement.plots()) {
                if (player.distanceToSqr((plot.minX() + plot.maxX()) / 2.0D, player.getY(), (plot.minZ() + plot.maxZ()) / 2.0D) > VIEW_RANGE * VIEW_RANGE) {
                    continue;
                }
                outline(mc, poseStack, lines, cam, plot.minX(), plot.minZ(), plot.maxX(), plot.maxZ(), 0.3F, 1.0F, 0.3F, true);
            }
            for (BuildingNetwork.ProjectView project : settlement.projects()) {
                float r;
                float g;
                float b;
                if (project.kind() == BuildProject.Kind.DEMOLISH.ordinal()) {
                    r = 1.0F;
                    g = 0.2F;
                    b = 0.2F;
                } else {
                    switch (BuildProject.Status.values()[Math.min(project.status(), BuildProject.Status.values().length - 1)]) {
                        case PLANNED -> {
                            r = 1.0F;
                            g = 1.0F;
                            b = 0.2F;
                        }
                        case QUEUED -> {
                            r = 1.0F;
                            g = 0.6F;
                            b = 0.1F;
                        }
                        case BUILDING -> {
                            r = 0.2F;
                            g = 0.9F;
                            b = 1.0F;
                        }
                        default -> {
                            r = 0.6F;
                            g = 0.6F;
                            b = 0.6F;
                        }
                    }
                }
                BlockPos origin = project.origin();
                DesignPreview.renderOutline(poseStack, buffers, cam, origin, new int[]{project.sizeX(), project.sizeY(), project.sizeZ()}, project.facing(), r, g, b);
            }
        }

        BuildingZoneItem.Selection selection = BuildingZoneItem.read(held);
        if (selection.corner() != null && selection.dimension() != null && selection.dimension().equals(mc.level.dimension().location())) {
            BlockPos a = selection.corner();
            ScannerRenderer.box(poseStack, lines, cam, a, a, 0.2F, 0.9F, 1.0F, 1.0F, 0.02);
            HitResult hit = mc.hitResult;
            if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos b = blockHit.getBlockPos();
                outline(mc, poseStack, lines, cam, Math.min(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()), Math.max(a.getZ(), b.getZ()),
                        1.0F, 1.0F, 1.0F, false);
            }
        }
        buffers.endBatch(RenderType.lines());
    }

    /** The ground height of a column, or -1 if the chunk is not there. */
    private static int ground(Minecraft mc, int x, int z) {
        if (!mc.level.hasChunk(x >> 4, z >> 4)) {
            return -1;
        }
        return mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    /** The rectangle of columns as flat boxes on the ground along its edge, and (if {@code posts}) a post at each corner. */
    private static void outline(Minecraft mc, PoseStack poseStack, VertexConsumer lines, Vec3 cam, int x1, int z1, int x2, int z2, float r, float g, float b, boolean posts) {
        for (int x = x1; x <= x2; x++) {
            flat(mc, poseStack, lines, cam, x, z1, r, g, b);
            if (z2 != z1) {
                flat(mc, poseStack, lines, cam, x, z2, r, g, b);
            }
        }
        for (int z = z1 + 1; z < z2; z++) {
            flat(mc, poseStack, lines, cam, x1, z, r, g, b);
            if (x2 != x1) {
                flat(mc, poseStack, lines, cam, x2, z, r, g, b);
            }
        }
        if (posts) {
            int[][] corners = {{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}};
            for (int[] c : corners) {
                int y = ground(mc, c[0], c[1]);
                if (y >= 0) {
                    LevelRenderer.renderLineBox(poseStack, lines, c[0] + 0.4 - cam.x, y - cam.y, c[1] + 0.4 - cam.z, c[0] + 0.6 - cam.x, y + 3 - cam.y, c[1] + 0.6 - cam.z,
                            r, g, b, 1.0F);
                }
            }
        }
    }

    private static void flat(Minecraft mc, PoseStack poseStack, VertexConsumer lines, Vec3 cam, int x, int z, float r, float g, float b) {
        int y = ground(mc, x, z);
        if (y < 0) {
            return;
        }
        LevelRenderer.renderLineBox(poseStack, lines, x - cam.x, y - cam.y, z - cam.z, x + 1 - cam.x, y + 0.1 - cam.y, z + 1 - cam.z, r, g, b, 0.9F);
    }
}
