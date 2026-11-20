/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/render/vehicle/RenderVehicleBase (the per-type delegates of RenderVehicle).
 */
package io.github.capttracker.ancientwarfarevehicle.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;

/**
 * Sets the animation angles of one vehicle model from the vehicle's state and draws it. The pose stack is already at the
 * vehicle, rotated by its yaw and flipped by {@code scale(-1, -1, 1)} (the Techne convention of the models).
 */
@FunctionalInterface
public interface VehicleModelDelegate {
    /**
     * @param yaw  the interpolated render yaw of the vehicle in degrees
     * @param tick the partial tick
     */
    void render(VehicleBase vehicle, float yaw, float tick, PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color);
}
