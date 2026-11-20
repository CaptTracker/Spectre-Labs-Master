/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: replaces the references of PathWorldAccess and Navigator to AWStructureBlocks.GATE_PROXY,
 * TEGateProxy and EntityGate of the Structures module.
 */
package io.github.capttracker.ancientwarfarevehicle.pathing;

import io.github.capttracker.ancientwarfarevehicle.entity.IGateEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * How the path finder finds gates. The Structures module installs a {@link Provider}; without one, gates do not
 * exist for the path finder (which is dormant anyway).
 */
public final class GateLookup {
    private GateLookup() {}

    public interface Provider {
        /** The block at the position is a gate proxy block ({@code AWStructureBlocks.GATE_PROXY}). */
        boolean isGateProxy(Level level, BlockPos pos);

        /** The proxy block at the position has its block entity ({@code TEGateProxy}). */
        boolean hasProxyTile(Level level, BlockPos pos);

        /** The gate that owns the proxy block at the position, or null. */
        @Nullable
        IGateEntity getOwner(Level level, BlockPos pos);
    }

    @Nullable
    private static volatile Provider provider;

    public static void setProvider(Provider newProvider) {
        provider = newProvider;
    }

    static boolean isGateProxy(Level level, BlockPos pos) {
        Provider p = provider;
        return p != null && p.isGateProxy(level, pos);
    }

    /** Whether soldiers may use the gate at the proxy: true without a tile, false without an owner, else the gate type decides. */
    static boolean canSoldierUseProxy(Level level, BlockPos pos) {
        Provider p = provider;
        if (p == null) {
            return false;
        }
        if (!p.hasProxyTile(level, pos)) {
            return true;
        }
        IGateEntity owner = p.getOwner(level, pos);
        return owner != null && owner.canSoldierActivate();
    }

    @Nullable
    static IGateEntity getOwner(Level level, BlockPos pos) {
        Provider p = provider;
        return p == null ? null : p.getOwner(level, pos);
    }
}
