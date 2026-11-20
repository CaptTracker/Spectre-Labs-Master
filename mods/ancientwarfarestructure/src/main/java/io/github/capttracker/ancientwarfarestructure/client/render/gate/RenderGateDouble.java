/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: structure/render/gate/RenderGateDouble.
 */
package io.github.capttracker.ancientwarfarestructure.client.render.gate;

import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfarestructure.entity.EntityGate;
import net.minecraft.core.BlockPos;

/** The gate whose two halves slide apart. */
public final class RenderGateDouble extends RenderGateBasic {
    public RenderGateDouble(GateModelBasic model) {
        super(model);
    }

    @Override
    protected BlockPos getMin(EntityGate gate) {
        return BlockTools.getMin(gate.pos1, gate.pos2);
    }

    @Override
    protected BlockPos getMax(EntityGate gate) {
        return BlockTools.getMax(gate.pos1, gate.pos2);
    }

    @Override
    protected void postRender(EntityGate gate, int x, float width, int y, float height, boolean wideOnXAxis, float axisRotation, float frame, GateDraw draw) {
        float move;
        boolean render = false;
        if (x < width * 0.5f) {
            move = -gate.edgePosition - gate.openingSpeed * (1 - frame);
            if (x + move > -0.5f) {
                render = true;
            }
        } else {
            move = gate.edgePosition + gate.openingSpeed * (1 - frame);
            if (x + move <= width - 0.475f) {
                render = true;
            }
        }
        float wallTx = wideOnXAxis ? move : 0;
        float wallTz = wideOnXAxis ? 0 : move;
        if (render) {
            draw.push();
            draw.translate(wallTx, 0, wallTz);
            model.setModelRotation(axisRotation);
            drawWall(gate, draw);
            draw.pop();
        }
    }
}
