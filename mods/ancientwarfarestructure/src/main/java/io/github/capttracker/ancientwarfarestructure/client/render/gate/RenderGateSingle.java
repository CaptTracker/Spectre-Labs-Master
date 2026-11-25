/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: structure/render/gate/RenderGateSingle.
 */
package io.github.capttracker.ancientwarfarestructure.client.render.gate;

import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfarestructure.entity.EntityGate;
import net.minecraft.core.BlockPos;

/** The gate whose single panel slides sideways; the side it slides to follows the order of the two corners. */
public final class RenderGateSingle extends RenderGateBasic {
    public RenderGateSingle(GateModelBasic model) {
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
        boolean opensReverse = gate.pos1.getX() > gate.pos2.getX() || gate.pos1.getZ() < gate.pos2.getZ();
        float wallTx = wideOnXAxis ? gate.edgePosition + gate.openingSpeed * (1 - frame) : 0;
        float wallTz = wideOnXAxis ? 0 : gate.edgePosition + gate.openingSpeed * (1 - frame);
        boolean render = false;
        if (opensReverse) {
            if ((wideOnXAxis && x - wallTx > -0.5f) || (!wideOnXAxis && x - wallTz > -0.5f)) {
                render = true;
            }
        } else {
            if ((wideOnXAxis && wallTx + x < gate.edgeMax - 0.5f) || (!wideOnXAxis && wallTz + x < gate.edgeMax - 0.5f)) {
                render = true;
            }
        }
        if (render) {
            if (opensReverse) {
                wallTx *= -1;
                wallTz *= -1;
            }
            draw.push();
            draw.translate(wallTx, 0, wallTz);
            model.setModelRotation(axisRotation);
            drawWall(gate, draw);
            draw.pop();
        }
    }
}
