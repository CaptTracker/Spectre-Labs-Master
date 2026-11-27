/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 11): the crate that floats up from a sunken ship, drawn as a barrel.
 */
package io.github.capttracker.ancientwarfarevehicle.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.FloatingCrate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

/** A floating crate is a vanilla barrel (no art of its own) that rocks a little on the water. Client only. */
public class FloatingCrateRenderer extends EntityRenderer<FloatingCrate> {
    public FloatingCrateRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.3F;
    }

    @Override
    public ResourceLocation getTextureLocation(FloatingCrate crate) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(FloatingCrate crate, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(crate, yaw, partialTick, poseStack, buffer, packedLight);
        poseStack.pushPose();
        float time = crate.tickCount + partialTick;
        poseStack.mulPose(Axis.YP.rotationDegrees((crate.getId() * 37) % 360));
        poseStack.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(time * 0.1F + crate.getId()) * 6.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees((float) Math.cos(time * 0.08F) * 5.0F));
        poseStack.scale(0.9F, 0.9F, 0.9F);
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.BARREL.defaultBlockState(), poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
