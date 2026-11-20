/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/render/RenderVehicle.
 */
package io.github.capttracker.ancientwarfarevehicle.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.capttracker.ancientwarfarevehicle.config.AWVehicleStatics;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.registry.VehicleTypeIds;
import java.text.DecimalFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * The one renderer of the vehicle entity. It sets up the transform of the original {@code RenderVehicle.doRender} (translate to the
 * entity, rotate by the render yaw, {@code scale(-1, -1, 1)} for the y-down Techne models), picks the texture of the vehicle's
 * material level and hands the drawing to the per-type {@link VehicleModelDelegate} (the 19 {@code RenderVehicleBase} classes).
 *
 * <p>Differences from 1.12.2: the red hit tint and the 0.2 alpha of the rider's own vehicle in first person are vertex colours of an
 * entity render type instead of {@code GlStateManager.color}; the nameplate is the vanilla name tag (the original drew its own
 * quad and two strings) with the same text, the same 64 block range and the same conditions.
 */
public class VehicleRenderer extends EntityRenderer<VehicleBase> {
    private static final int FIRST_PERSON_ALPHA = 51; // 0.2 * 255
    private static final DecimalFormat HEALTH_FORMAT = new DecimalFormat("#.#");

    private final VehicleModelDelegate[] delegates;

    public VehicleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.delegates = VehicleModels.create(context);
    }

    @Override
    public void render(VehicleBase vehicle, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        VehicleModelDelegate delegate = delegateFor(vehicle);
        if (delegate != null) {
            Minecraft mc = Minecraft.getInstance();
            boolean ghost = !AWVehicleStatics.clientSettings.renderVehiclesInFirstPerson
                    && mc.player != null
                    && vehicle.getControllingPassenger() == mc.player
                    && mc.options.getCameraType().isFirstPerson();

            // 1.12.2: GlStateManager.color(1, 1 - p, 1 - p, 1) while the hit animation runs (it also reset the alpha of the ghost)
            int alpha = ghost ? FIRST_PERSON_ALPHA : 255;
            int greenBlue = 255;
            if (vehicle.hitAnimationTicks > 0) {
                float percent = Mth.clamp(vehicle.hitAnimationTicks / 20.f, 0.f, 1.f);
                greenBlue = (int) ((1.f - percent) * 255.f);
                alpha = 255;
            }
            int color = FastColor.ARGB32.color(alpha, 255, greenBlue, greenBlue);

            ResourceLocation texture = getTextureLocation(vehicle);
            RenderType renderType = alpha < 255 ? RenderType.entityTranslucent(texture) : RenderType.entityCutoutNoCull(texture);
            VertexConsumer buffer = bufferSource.getBuffer(renderType);

            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(entityYaw));
            poseStack.scale(-1.f, -1.f, 1.f);
            delegate.render(vehicle, entityYaw, partialTick, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, color);
            poseStack.popPose();
        }

        if (shouldShowPlate(vehicle)) {
            renderPlate(vehicle, partialTick, poseStack, bufferSource, packedLight);
        }
    }

    private VehicleModelDelegate delegateFor(VehicleBase vehicle) {
        int index = vehicle.vehicleType.getGlobalVehicleType();
        return VehicleTypeIds.isValid(index) ? delegates[index] : null;
    }

    /** Not for the vehicle the local player rides, and not for a vehicle that has no position yet (1.12.2 tested {@code posY > 0}). */
    private boolean shouldShowPlate(VehicleBase vehicle) {
        Minecraft mc = Minecraft.getInstance();
        return AWVehicleStatics.clientSettings.renderVehicleNameplates
                && vehicle.getY() > vehicle.level().getMinBuildHeight()
                && (mc.player == null || vehicle.getControllingPassenger() != mc.player);
    }

    private void renderPlate(VehicleBase vehicle, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        Component name = vehicle.vehicleType.getDisplayComponent();
        if (AWVehicleStatics.clientSettings.renderVehicleNameplateHealth) {
            name = Component.empty().append(name)
                    .append(" " + HEALTH_FORMAT.format(vehicle.getHealth()) + "/" + HEALTH_FORMAT.format(vehicle.baseHealth));
        }
        // 1.12.2 hung the plate at height + 0.75, the vanilla name tag at height + 0.5
        poseStack.pushPose();
        poseStack.translate(0.0, 0.25, 0.0);
        // the vanilla name tag culls beyond 64 blocks, the range of the original plate
        this.renderNameTag(vehicle, name, poseStack, bufferSource, packedLight, partialTick);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(VehicleBase vehicle) {
        return vehicle.getTexture();
    }
}
