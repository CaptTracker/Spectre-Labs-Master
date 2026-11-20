/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the references of vehicle/pathing/PathWorldAccess and Navigator to GATE_PROXY, TEGateProxy
 * and EntityGate.
 */
package io.github.capttracker.ancientwarfarestructure.gate;

import io.github.capttracker.ancientwarfarestructure.block.entity.GateProxyBlockEntity;
import io.github.capttracker.ancientwarfarestructure.init.AwGates;
import io.github.capttracker.ancientwarfarevehicle.entity.IGateEntity;
import io.github.capttracker.ancientwarfarevehicle.pathing.GateLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/** What the Vehicles module's path finder asks about gates. Installed with {@link GateLookup#setProvider} by {@code AwGates}. */
public final class GateVehicleLink implements GateLookup.Provider {

    @Override
    public boolean isGateProxy(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(AwGates.GATE_PROXY.get());
    }

    @Override
    public boolean hasProxyTile(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof GateProxyBlockEntity;
    }

    @Nullable
    @Override
    public IGateEntity getOwner(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof GateProxyBlockEntity proxy ? proxy.getGate().orElse(null) : null;
    }
}
