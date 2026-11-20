/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the ModelBase / ModelRenderer base of the vehicle/model classes.
 */
package io.github.capttracker.ancientwarfarevehicle.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.capttracker.ancientwarfarevehicle.AncientWarfareVehicles;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Base of the 20 generated box models (18 vehicles, 2 missiles). A model is a baked part tree plus a handful of setters that
 * write the rotations of its moving parts; it is drawn with {@link #renderToBuffer}. The model holds its animation state, so one
 * instance is shared by every vehicle of its kind and the renderer sets all the angles right before each draw (as 1.12.2 did).
 *
 * <p>Client only: lives in the client package and is never referenced from common code.
 */
public abstract class AwPartModel {
    protected final ModelPart root;

    protected AwPartModel(ModelPart root) {
        this.root = root;
    }

    /** The layer location of a model: {@code ancientwarfarevehicle:<name>#main}. */
    protected static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(Ids.of(AncientWarfareVehicles.MOD_ID, name), "main");
    }

    /**
     * Draws every part. The pose stack must already hold the entity transform. The 1.12.2 models were authored y-down and drawn
     * after {@code scale(-1, -1, 1)}; a plain {@code EntityRenderer} has no such flip, so the renderer applies it itself.
     */
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
