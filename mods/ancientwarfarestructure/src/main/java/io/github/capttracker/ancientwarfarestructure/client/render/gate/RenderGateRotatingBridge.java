/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: structure/render/gate/RenderGateRotatingBridge.
 */
package io.github.capttracker.ancientwarfarestructure.client.render.gate;

import com.mojang.math.Axis;
import io.github.capttracker.ancientwarfarestructure.entity.EntityGate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** The drawbridge: the whole tiled wall rotates about its base by the opening progress (degrees). */
public final class RenderGateRotatingBridge implements GateModelRenderer {
    private final GateModelBridge model;

    public RenderGateRotatingBridge(GateModelBridge model) {
        this.model = model;
    }

    @Override
    public void render(EntityGate g, float frame, GateDraw draw) {
        draw.push();
        BlockPos min = g.pos1;
        BlockPos max = g.pos2;

        boolean wideOnXAxis = min.getX() != max.getX();

        float rx = wideOnXAxis ? g.edgePosition + g.openingSpeed * (1 - frame) : 0;
        float rz = wideOnXAxis ? 0 : g.edgePosition + g.openingSpeed * (1 - frame);
        boolean invert = g.gateOrientation == Direction.SOUTH || g.gateOrientation == Direction.EAST;
        if (invert) {
            rx *= -1;
            rz *= -1;
        }
        draw.translate(0, -0.5f, 0);
        draw.poseStack().mulPose(Axis.XP.rotationDegrees(rx));
        draw.poseStack().mulPose(Axis.ZP.rotationDegrees(rz));
        float width = wideOnXAxis ? max.getX() - min.getX() + 1 : max.getZ() - min.getZ() + 1;
        float height = max.getY() - min.getY() + 1;
        float xOffset = wideOnXAxis ? width * 0.5f - 0.5f : 0f;
        float zOffset = wideOnXAxis ? 0f : -width * 0.5f + 0.5f;

        float tx = wideOnXAxis ? 1 : 0;
        float ty = -1;
        float tz = wideOnXAxis ? 0 : 1;
        float axisRotation = wideOnXAxis ? 180 : 90;
        if (invert) {
            draw.poseStack().mulPose(Axis.YP.rotationDegrees(180));
        }
        draw.translate(-xOffset, 0, zOffset);
        for (int y = 0; y < height; y++) {
            draw.push();
            for (int x = 0; x < width; x++) {
                model.setModelRotation(axisRotation);
                if (y == 0) {
                    model.renderGateBlock(draw);
                }
                if (y == height - 1 && x > 0 && x < width - 1) {
                    model.renderTop(draw);
                } else if (y == height - 1 && x == 0) {
                    model.renderCorner(draw);
                } else if (y == height - 1 && x == width - 1) {
                    model.renderCorner2(draw);
                } else if (x == 0 && y > 0) {
                    model.renderSide1(draw);
                } else if (x == width - 1 && y > 0) {
                    model.renderSide2(draw);
                }
                if (y > 0) {
                    draw.push();
                    model.setModelRotation(axisRotation);
                    model.renderSolidWall(draw);
                    draw.pop();
                }
                draw.translate(tx, 0, tz);
            }
            draw.pop();
            draw.translate(0, ty, 0);
        }
        draw.pop();
    }
}
