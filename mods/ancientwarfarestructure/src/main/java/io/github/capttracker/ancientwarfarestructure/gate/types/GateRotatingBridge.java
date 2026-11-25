/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: structure/gates/types/GateRotatingBridge (and the swing box of
 * structure/entity/RotateBoundingBox).
 */
package io.github.capttracker.ancientwarfarestructure.gate.types;

import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfarestructure.block.GateProxyMode;
import io.github.capttracker.ancientwarfarestructure.entity.EntityGate;
import io.github.capttracker.ancientwarfarestructure.init.AwGates;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The drawbridge: a wall that rotates down by 90 degrees until it lies flat on the ground in front of its base.
 *
 * <p>Collision (1.12.2: the entity's {@code RotateBoundingBox} while it swung, a full block box when down): the wall
 * cells are closed proxies while the bridge is up, collide with nothing while it moves, and the cells of the landing
 * area (the base row, as long in the facing direction as the bridge is high) become {@link GateProxyMode#FLOOR}
 * proxies once it is down. The swung-out box is only the entity's hit and pick box now (see {@link #getEntityBox});
 * entities are not pushed or carried by the moving bridge any more.
 */
public class GateRotatingBridge extends Gate {
    private static final float TO_RAD = (float) Math.PI / 180F;

    public GateRotatingBridge(int id, String registryName, String tex, Supplier<SoundEvent> moveSound, Supplier<SoundEvent> hurtSound, Supplier<SoundEvent> breakSound, IntSupplier maxHealth) {
        super(id, registryName, tex, moveSound, hurtSound, breakSound, maxHealth);
        setName("gateDrawbridge");
        this.moveSpeed = 1.f;
        this.canSoldierInteract = false;
        setVariant(Variant.WOOD_ROTATING);
    }

    /** The far end of the landing area at base level (1.12.2: {@code pos3}). */
    private static BlockPos landingEnd(EntityGate gate, BlockPos min, BlockPos max) {
        int heightAdj = max.getY() - min.getY();
        return max.offset(0, -heightAdj, 0).relative(gate.gateOrientation, heightAdj);
    }

    private static boolean fullyOpen(EntityGate gate) {
        return gate.getOpeningStatus() == 0 && gate.edgePosition >= gate.edgeMax;
    }

    @Override
    protected GateProxyMode modeForCell(EntityGate gate, BlockPos cell, BlockPos min, BlockPos max) {
        if (gate.isClosed()) {
            return GateProxyMode.CLOSED;
        }
        // the base row of the wall is part of the landing area
        return fullyOpen(gate) && cell.getY() == min.getY() ? GateProxyMode.FLOOR : GateProxyMode.OPEN;
    }

    @Override
    public AABB getEntityBox(EntityGate gate) {
        BlockPos min = BlockTools.getMin(gate.pos1, gate.pos2);
        BlockPos max = BlockTools.getMax(gate.pos1, gate.pos2);
        if (gate.edgePosition == 0) {
            return volume(min, max);
        }
        if (gate.edgePosition < gate.edgeMax) {
            return swingBox(gate, min, max);
        }
        BlockPos pos3 = landingEnd(gate, min, max);
        BlockPos landingMax = BlockTools.getMax(min, pos3);
        BlockPos landingMin = BlockTools.getMin(min, pos3);
        return volume(landingMin, landingMax);
    }

    /**
     * The region that the bridge is in while it rotates: the 1.12.2 {@code RotateBoundingBox.intersects}, which was a box
     * as high as the wall's remaining height ({@code cos(angle)}) and as long as the swung-out length ({@code sin(angle)})
     * on the facing side of the wall.
     */
    private static AABB swingBox(EntityGate gate, BlockPos min, BlockPos max) {
        double minX = min.getX();
        double minY = min.getY();
        double minZ = min.getZ();
        double maxX = max.getX() + 1;
        double maxZ = max.getZ() + 1;
        double wallHeight = max.getY() + 1 - min.getY();
        float angle = gate.edgePosition * TO_RAD;
        double height = Math.max(Mth.cos(angle) * wallHeight, 0.05D);
        double length = Math.max(Mth.sin(angle) * (wallHeight + 1), 0.05D);
        switch (gate.gateOrientation) {
            case WEST:
                return new AABB(maxX - length, minY, minZ, maxX, minY + height, maxZ);
            case NORTH:
                return new AABB(minX, minY, maxZ - length, maxX, minY + height, maxZ);
            case EAST:
                return new AABB(minX, minY, minZ, minX + length, minY + height, maxZ);
            default:
                return new AABB(minX, minY, minZ, maxX, minY + height, minZ + length);
        }
    }

    @Override
    public boolean canActivate(EntityGate gate, boolean open) {
        if (gate.pos1 == null || gate.pos2 == null) {
            return false;
        }
        if (!open) {
            return super.canActivate(gate, false);
        }
        BlockPos min = BlockTools.getMin(gate.pos1, gate.pos2);
        BlockPos max = BlockTools.getMax(gate.pos1, gate.pos2);
        BlockPos pos3 = landingEnd(gate, min, max);
        BlockPos landingMax = BlockTools.getMax(min, pos3);
        BlockPos landingMin = BlockTools.getMin(min, pos3);
        Level level = gate.level();
        for (int x = landingMin.getX(); x <= landingMax.getX(); x++) {
            for (int z = landingMin.getZ(); z <= landingMax.getZ(); z++) {
                if (!isClearLanding(level.getBlockState(new BlockPos(x, landingMin.getY(), z)))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * A landing cell is clear if it is air, already a gate proxy, or something a block could be placed over such as grass,
     * flowers or a snow layer (it is broken and dropped when the bridge lands, see {@code placeProxy}). Fluids are not
     * clear, so a bridge does not delete water. Port change: 1.12.2 accepted only air, so a bridge taller than one block
     * would not open over a strip of grass, with no message.
     */
    private static boolean isClearLanding(BlockState state) {
        return state.isAir() || state.is(AwGates.GATE_PROXY.get()) || (state.canBeReplaced() && state.getFluidState().isEmpty());
    }

    @Override
    public void setInitialBounds(EntityGate gate, BlockPos pos1, BlockPos pos2) {
        BlockPos min = BlockTools.getMin(pos1, pos2);
        BlockPos max = BlockTools.getMax(pos1, pos2);
        boolean wideOnXAxis = min.getX() != max.getX();
        float width = wideOnXAxis ? max.getX() - min.getX() + 1 : max.getZ() - min.getZ() + 1;
        float xOffset = wideOnXAxis ? width * 0.5f : 0.5f;
        float zOffset = wideOnXAxis ? 0.5f : width * 0.5f;
        gate.setPositions(min, max);
        gate.setRenderBoundingBox(getRenderBoundingBox(gate, min, max));
        gate.edgeMax = 90.f;
        gate.setPos(min.getX() + xOffset, min.getY(), min.getZ() + zOffset);
    }

    @Override
    protected AABB getRenderBoundingBox(EntityGate gate, BlockPos min, BlockPos max) {
        BlockPos pos3 = landingEnd(gate, min, max);
        BlockPos boxMax = BlockTools.getMax(max, pos3);
        BlockPos boxMin = BlockTools.getMin(min, pos3);
        return volume(boxMin, boxMax);
    }

    @Override
    public void restoreProxies(EntityGate gate) {
        super.restoreProxies(gate);
        if (fullyOpen(gate)) {
            placeLanding(gate);
        }
    }

    @Override
    public void removeProxies(EntityGate gate) {
        super.removeProxies(gate);
        if (gate.level().isClientSide || gate.pos1 == null) {
            return;
        }
        BlockPos min = BlockTools.getMin(gate.pos1, gate.pos2);
        BlockPos max = BlockTools.getMax(gate.pos1, gate.pos2);
        BlockPos pos3 = landingEnd(gate, min, max);
        removeProxiesBetween(gate, BlockTools.getMin(min, pos3), BlockTools.getMax(min, pos3), pos -> false);
    }

    private void placeLanding(EntityGate gate) {
        BlockPos min = BlockTools.getMin(gate.pos1, gate.pos2);
        BlockPos max = BlockTools.getMax(gate.pos1, gate.pos2);
        BlockPos pos3 = landingEnd(gate, min, max);
        for (BlockPos pos : BlockPos.betweenClosed(BlockTools.getMin(min, pos3), BlockTools.getMax(min, pos3))) {
            placeProxy(gate, pos, GateProxyMode.FLOOR);
        }
    }

    @Override
    public void onGateFinishOpen(EntityGate gate) {
        if (gate.level().isClientSide) {
            return;
        }
        placeLanding(gate);
    }

    /** The bridge starts to rise: the floor goes (the cells of the wall's base row stay, they are the hinge). */
    @Override
    public void onGateStartClose(EntityGate gate) {
        if (gate.level().isClientSide) {
            return;
        }
        BlockPos min = BlockTools.getMin(gate.pos1, gate.pos2);
        BlockPos max = BlockTools.getMax(gate.pos1, gate.pos2);
        boolean widestOnXAxis = gate.pos1.getX() != gate.pos2.getX();
        BlockPos pos3 = landingEnd(gate, min, max);
        removeProxiesBetween(gate, BlockTools.getMin(min, pos3), BlockTools.getMax(min, pos3),
                pos -> (widestOnXAxis && pos.getZ() == gate.pos1.getZ()) || (!widestOnXAxis && pos.getX() == gate.pos1.getX()));
        // the hinge cells are no floor any more
        updateProxies(gate);
    }
}
