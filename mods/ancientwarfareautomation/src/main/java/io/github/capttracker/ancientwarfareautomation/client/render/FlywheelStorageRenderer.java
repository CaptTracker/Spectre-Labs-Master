/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/render/FlywheelStorageRenderer and FlywheelStorageAnimationRenderer.
 */
package io.github.capttracker.ancientwarfareautomation.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.multiblock.TileFlywheelStorage;
import io.github.capttracker.ancientwarfareautomation.block.torque.TorqueTier;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/**
 * Draws a whole flywheel storage stack from its control block, as the original did (the other blocks of the stack draw
 * nothing): for every level the case bars, the lower window and (below the top level) the upper window, the shroud
 * at the bottom and at the top level, and the spindle with its extension, which turns around Y.
 *
 * <p>1.12.2 drew the case bars, shrouds and windows into the chunk mesh (solid and translucent layer) and only the
 * spindle every frame; here the block has no static model, and everything is drawn every frame (about 100 quads per level).
 * Moving the static part into a chunk mesh model (a {@code ModelData} driven model) is listed as a rework candidate.
 * The windows have alpha 0.25, as in the original.
 */
public class FlywheelStorageRenderer implements BlockEntityRenderer<TileFlywheelStorage> {
    private static final float WINDOW_ALPHA = 0.25F;

    public FlywheelStorageRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TileFlywheelStorage storage, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (!storage.isControl || storage.getLevel() == null) {
            return;
        }
        int height = storage.setHeight;
        if (height <= 0) {
            return;
        }
        boolean large = storage.setWidth > 1;
        TorqueTier tier = storage.getTier();
        AutomationModels.Family family = large ? AutomationModels.FLYWHEEL_LARGE : AutomationModels.FLYWHEEL_SMALL;
        float rotation = storage.getRotation(partialTick);

        // spindle and extensions: turn around the centre of the block, level by level
        for (int i = 0; i < height; i++) {
            poseStack.pushPose();
            poseStack.translate(0.0D, i, 0.0D);
            PartRenderer.rotateAboutCenter(poseStack, Axis.YP, rotation);
            PartRenderer.draw(poseStack, buffers, family.get("spindle", tier), light, overlay);
            if (i < height - 1) {
                PartRenderer.draw(poseStack, buffers, family.get("extension", tier), light, overlay);//at every level less than highest
            }
            poseStack.popPose();
        }

        // static parts, solid
        for (int i = 0; i < height; i++) {
            poseStack.pushPose();
            poseStack.translate(0.0D, i, 0.0D);
            PartRenderer.draw(poseStack, buffers, family.get("case_bars", tier), light, overlay);
            if (i == height - 1) {
                PartRenderer.draw(poseStack, buffers, family.get("shroud_upper", tier), light, overlay);//at highest level
            }
            if (i == 0) {
                PartRenderer.draw(poseStack, buffers, family.get("shroud_lower", tier), light, overlay);//at ground level
            }
            poseStack.popPose();
        }

        // windows, translucent
        for (int i = 0; i < height; i++) {
            poseStack.pushPose();
            poseStack.translate(0.0D, i, 0.0D);
            PartRenderer.drawTranslucent(poseStack, buffers, family.get("window_lower", tier), WINDOW_ALPHA, light, overlay);
            if (i < height - 1) {
                PartRenderer.drawTranslucent(poseStack, buffers, family.get("window_upper", tier), WINDOW_ALPHA, light, overlay);
            }
            poseStack.popPose();
        }
    }

    @Override
    public AABB getRenderBoundingBox(TileFlywheelStorage storage) {
        return storage.getRenderBounds();
    }
}
