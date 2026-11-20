/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: structure/render/gate/RenderGateBasic (immediate-mode GL replaced by the pose stack).
 */
package io.github.capttracker.ancientwarfarestructure.client.render.gate;

import io.github.capttracker.ancientwarfarestructure.entity.EntityGate;
import net.minecraft.core.BlockPos;

/**
 * The vertical gate that lifts up: tiles the side, corner and top pieces around the frame, then the wall (or bars) that
 * moves down by the opening progress. The single and double gates only change the corners they use and how the wall moves
 * ({@link #postRender}).
 */
public class RenderGateBasic implements GateModelRenderer {

    protected final GateModelBasic model;

    public RenderGateBasic(GateModelBasic model) {
        this.model = model;
    }

    @Override
    public final void render(EntityGate g, float frame, GateDraw draw) {
        draw.push();
        BlockPos min = getMin(g);
        BlockPos max = getMax(g);

        boolean wideOnXAxis = min.getX() != max.getX();
        float width;
        float height = max.getY() - min.getY() + 1;
        float xOffset = 0f;
        float zOffset = 0;
        float tx = 0;
        float ty = -1;
        float tz = 0;
        float axisRotation = 0;
        if (wideOnXAxis) {
            width = max.getX() - min.getX() + 1;
            xOffset = width * 0.5f - 0.5f;
            tx = 1;
            axisRotation = 90;
        } else {
            tz = 1;
            width = max.getZ() - min.getZ() + 1;
            zOffset = -width * 0.5f + 0.5f;
        }
        draw.translate(-xOffset, 0, zOffset);
        for (int y = 0; y < height; y++) {
            draw.push();
            for (int x = 0; x < width; x++) {
                model.setModelRotation(axisRotation);
                if (y == height - 1 && x > 0 && x < width - 1) {
                    model.renderTop(draw);
                } else if (y == height - 1 && x == 0) {
                    model.renderCorner(draw);
                } else if (y == height - 1 && x == width - 1) {
                    model.setModelRotation(axisRotation + 180);
                    model.renderCorner(draw);
                } else if (x == 0) {
                    model.renderSide(draw);
                } else if (x == width - 1) {
                    model.setModelRotation(axisRotation + 180);
                    model.renderSide(draw);
                }
                postRender(g, x, width, y, height, wideOnXAxis, axisRotation, frame, draw);
                draw.translate(tx, 0, tz);
            }
            draw.pop();
            draw.translate(0, ty, 0);
        }
        draw.pop();
    }

    protected BlockPos getMin(EntityGate gate) {
        return gate.pos1;
    }

    protected BlockPos getMax(EntityGate gate) {
        return gate.pos2;
    }

    protected void postRender(EntityGate gate, int x, float width, int y, float height, boolean wideOnXAxis, float axisRotation, float frame, GateDraw draw) {
        if (y + gate.edgePosition <= height - 0.475f) {
            draw.push();
            draw.translate(0, -gate.edgePosition - gate.openingSpeed * (1 - frame), 0);
            model.setModelRotation(axisRotation);
            drawWall(gate, draw);
            draw.pop();
        }
    }

    /** The solid wall of the wooden gates, the bars of the iron ones. */
    protected final void drawWall(EntityGate gate, GateDraw draw) {
        if (gate.getGateType().getModelType() == 0) {
            model.renderSolidWall(draw);
        } else {
            model.renderBars(draw);
        }
    }
}
