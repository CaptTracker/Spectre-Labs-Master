/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/render/TorqueTransportSidedRenderer, TorqueJunctionRenderer,
 * TorqueDistributorRenderer and TorqueTransportAnimationRenderer.
 */
package io.github.capttracker.ancientwarfareautomation.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.capttracker.ancientwarfare.api.ITorque.ITorqueTile;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileTorqueSidedCell;
import io.github.capttracker.ancientwarfareautomation.block.torque.TorqueTier;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * Draws the gear heads of the torque junction and the torque distributor, one for every connected side. The housing is
 * the static block model (turned by the block state facing). A head turns in the direction of its side
 * (even sides one way, odd sides the other, as in the original) with the rotation of the block, or, on a side that the
 * block only takes torque from, with the negated output rotation of the neighbour.
 */
public class TorqueSidedCellRenderer implements BlockEntityRenderer<TileTorqueSidedCell> {
    private final AutomationModels.Family family;

    /** @param family {@link AutomationModels#TORQUE_JUNCTION} or {@link AutomationModels#TORQUE_DISTRIBUTOR} */
    public TorqueSidedCellRenderer(BlockEntityRendererProvider.Context context, AutomationModels.Family family) {
        this.family = family;
    }

    @Override
    public void render(TileTorqueSidedCell cell, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (cell.getLevel() == null) {
            return;
        }
        TorqueTier tier = cell.getTier();
        boolean[] connections = cell.getConnections();
        ITorqueTile[] neighbours = cell.getTorqueCache();
        for (Direction side : Direction.values()) {
            int index = side.get3DDataValue();
            if (!connections[index]) {
                continue;
            }
            float rotation = cell.getClientOutputRotation(side, partialTick);
            ITorqueTile neighbour = neighbours[index];
            if (!cell.canOutputTorque(side) && neighbour != null && neighbour.useOutputRotation(null)) {
                rotation = -neighbour.getClientOutputRotation(side.getOpposite(), partialTick);
            }
            poseStack.pushPose();
            PartRenderer.rotateAboutCenter(poseStack, PartRenderer.axisOf(side), (index % 2 == 0) ? rotation : -rotation);
            PartRenderer.draw(poseStack, buffers, family.head(side, tier), light, overlay);
            poseStack.popPose();
        }
    }

    @Override
    public AABB getRenderBoundingBox(TileTorqueSidedCell cell) {
        return cell.getRenderBounds();
    }
}
