/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/render/RenderMissile, render/missile/RenderMissileBase, RenderArrow, RenderShot.
 */
package io.github.capttracker.ancientwarfarevehicle.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelArrow2;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelShot;
import io.github.capttracker.ancientwarfarevehicle.missiles.IAmmo;
import io.github.capttracker.ancientwarfarevehicle.missiles.MissileBase;
import io.github.capttracker.ancientwarfarevehicle.registry.AmmoRegistry;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The one renderer of the missile entity: the long arrow model for arrows, ballista bolts and rockets (9 ammo types), the cube
 * shot model for every other ammo (42 types), scaled by {@link IAmmo#getRenderScale()} and textured by the ammo.
 * Transform of {@code RenderMissileBase.doRender}: yaw - 90 about y, pitch - 90 about x, then {@code scale(-1, -1, 1)}.
 */
public class MissileRenderer extends EntityRenderer<MissileBase> {
    private final ModelArrow2 arrowModel;
    private final ModelShot shotModel;
    /** The ammo drawn with the arrow model, filled on first use because {@link AmmoRegistry} sets its fields up late. */
    private Set<IAmmo> arrowAmmo;

    public MissileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.arrowModel = new ModelArrow2(context.bakeLayer(ModelArrow2.LAYER));
        this.shotModel = new ModelShot(context.bakeLayer(ModelShot.LAYER));
    }

    @Override
    public void render(MissileBase missile, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        IAmmo ammo = missile.getAmmoType();
        if (ammo == null) {
            return;
        }
        // 1.12.2 used the interpolated yaw but the raw pitch; interpolating the pitch too is smoother and the same at rest
        float pitch = Mth.lerp(partialTick, missile.xRotO, missile.getXRot());

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(entityYaw - 90.f));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch - 90.f));
        poseStack.scale(-1.f, -1.f, 1.f);
        float scale = ammo.getRenderScale();
        poseStack.scale(scale, scale, scale);

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(missile)));
        if (usesArrowModel(ammo)) {
            arrowModel.renderToBuffer(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, -1);
        } else {
            shotModel.renderToBuffer(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, -1);
        }
        poseStack.popPose();
    }

    private boolean usesArrowModel(IAmmo ammo) {
        if (arrowAmmo == null) {
            Set<IAmmo> set = Collections.newSetFromMap(new IdentityHashMap<>());
            Collections.addAll(set, AmmoRegistry.ammoArrow, AmmoRegistry.ammoRocket,
                    AmmoRegistry.ammoHwachaRocketFlame, AmmoRegistry.ammoHwachaRocketExplosive, AmmoRegistry.ammoHwachaRocketAirburst,
                    AmmoRegistry.ammoBallistaBolt, AmmoRegistry.ammoBallistaBoltExplosive,
                    AmmoRegistry.ammoBallistaBoltFlame, AmmoRegistry.ammoBallistaBoltIron);
            set.remove(null);
            if (set.isEmpty()) {
                return false; // the ammo registry is not filled yet, try again next frame
            }
            arrowAmmo = set;
        }
        return arrowAmmo.contains(ammo);
    }

    @Override
    public ResourceLocation getTextureLocation(MissileBase missile) {
        return missile.getTexture();
    }
}
